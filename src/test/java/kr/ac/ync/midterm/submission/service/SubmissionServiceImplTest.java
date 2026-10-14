package kr.ac.ync.midterm.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.SubmissionPeriodException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class SubmissionServiceImplTest {

	// 현재 시각을 고정해 B1 경계값(시작 · 종료 정각)을 확인한다
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
	private static final ZoneId ZONE = ZoneId.systemDefault();

	@MockitoBean
	private Clock clock;

	@Autowired
	private SubmissionService submissionService;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	private User student;
	private Course course;

	@BeforeEach
	void setUp() {
		setNow(NOW);
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		student = savedUser("student@test.com", Role.STUDENT, "20260001");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
	}

	private void setNow(LocalDateTime now) {
		when(clock.instant()).thenReturn(now.atZone(ZONE).toInstant());
		when(clock.getZone()).thenReturn(ZONE);
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name("홍길동")
				.studentNo(studentNo).role(role).build());
	}

	private Assignment savedAssignment(LocalDateTime startAt, LocalDateTime endAt) {
		return assignmentRepository.save(Assignment.builder().course(course).title("과제").content("내용")
				.startAt(startAt).endAt(endAt).maxScore(100).build());
	}

	private Assignment openAssignment() {
		return savedAssignment(NOW.minusDays(1), NOW.plusDays(1));
	}

	private SubmissionRequest request(String content) {
		SubmissionRequest request = new SubmissionRequest();
		request.setContent(content);
		return request;
	}

	@Test
	@DisplayName("submit - 진행중인 과제에 수강생이 제출하면 저장됨")
	void submit_first() {
		Assignment assignment = openAssignment();

		submissionService.submit(student.getId(), assignment.getId(), request("첫 제출"));

		Submission saved = submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
				.orElseThrow();
		assertThat(saved.getContent()).isEqualTo("첫 제출");
		assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
		assertThat(saved.getSubmittedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("submit - 같은 과제에 두 번 제출해도 제출은 1건이고 내용이 덮어써짐 (B3)")
	void submit_twice_overwrites() {
		Assignment assignment = openAssignment();
		submissionService.submit(student.getId(), assignment.getId(), request("첫 제출"));
		setNow(NOW.plusHours(3));

		submissionService.submit(student.getId(), assignment.getId(), request("수정한 제출"));

		assertThat(submissionRepository.count()).isEqualTo(1);
		Submission saved = submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
				.orElseThrow();
		assertThat(saved.getContent()).isEqualTo("수정한 제출");
		assertThat(saved.getSubmittedAt()).isEqualTo(NOW.plusHours(3));
	}

	@Test
	@DisplayName("submit - 다른 학생의 제출과는 별개로 저장됨 (과제당 학생 1건)")
	void submit_perStudent() {
		Assignment assignment = openAssignment();
		User other = savedUser("other@test.com", Role.STUDENT, "20260002");
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());

		submissionService.submit(student.getId(), assignment.getId(), request("내 제출"));
		submissionService.submit(other.getId(), assignment.getId(), request("다른 학생 제출"));

		assertThat(submissionRepository.count()).isEqualTo(2);
		assertThat(submissionService.findMySubmission(student.getId(), assignment.getId()).orElseThrow().content())
				.isEqualTo("내 제출");
	}

	@Test
	@DisplayName("submit - 시작일시 정각과 종료일시 정각에도 제출할 수 있음 (B1 경계)")
	void submit_atBoundaries() {
		Assignment atStart = savedAssignment(NOW, NOW.plusDays(1));
		Assignment atEnd = savedAssignment(NOW.minusDays(1), NOW);

		submissionService.submit(student.getId(), atStart.getId(), request("시작 정각"));
		submissionService.submit(student.getId(), atEnd.getId(), request("종료 정각"));

		assertThat(submissionRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("submit - 시작 전(1초 전)에는 제출할 수 없음 (B1)")
	void submit_beforeStart() {
		Assignment assignment = savedAssignment(NOW.plusSeconds(1), NOW.plusDays(1));

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(), request("너무 이름")))
				.isInstanceOf(SubmissionPeriodException.class)
				.hasMessage("제출 기간이 아닙니다.");
		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("submit - 종료 후(1초 후)에는 제출할 수 없음 (B1)")
	void submit_afterEnd() {
		Assignment assignment = savedAssignment(NOW.minusDays(1), NOW.minusSeconds(1));

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(), request("너무 늦음")))
				.isInstanceOf(SubmissionPeriodException.class);
		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("submit - 마감된 뒤에는 기존 제출도 수정할 수 없음 (B1)")
	void submit_resubmitAfterEnd() {
		Assignment assignment = openAssignment();
		submissionService.submit(student.getId(), assignment.getId(), request("기간 안 제출"));
		setNow(NOW.plusDays(1).plusSeconds(1));

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(), request("기간 밖 수정")))
				.isInstanceOf(SubmissionPeriodException.class);
		assertThat(submissionService.findMySubmission(student.getId(), assignment.getId()).orElseThrow().content())
				.isEqualTo("기간 안 제출");
	}

	@Test
	@DisplayName("submit - 수강 등록하지 않은 학생은 403이고 저장되지 않음 (B2)")
	void submit_notEnrolled() {
		Assignment assignment = openAssignment();
		User stranger = savedUser("stranger@test.com", Role.STUDENT, "20260003");

		assertThatThrownBy(() -> submissionService.submit(stranger.getId(), assignment.getId(), request("몰래 제출")))
				.isInstanceOf(ForbiddenException.class)
				.satisfies(e -> assertThat(((ForbiddenException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("submit - 없는 과제는 404")
	void submit_notFound() {
		assertThatThrownBy(() -> submissionService.submit(student.getId(), 999999L, request("내용")))
				.isInstanceOf(AssignmentNotFoundException.class);
	}

	@Test
	@DisplayName("submit - 채점 완료된 제출은 재제출할 수 없고 내용도 바뀌지 않음 (B4)")
	void submit_graded() {
		Assignment assignment = openAssignment();
		submissionService.submit(student.getId(), assignment.getId(), request("채점될 제출"));
		Submission saved = submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
				.orElseThrow();
		ReflectionTestUtils.setField(saved, "status", SubmissionStatus.GRADED);
		submissionRepository.flush();

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(), request("고치려는 내용")))
				.isInstanceOf(AlreadyGradedException.class)
				.hasMessage("채점 완료된 과제입니다.");
		assertThat(saved.getContent()).isEqualTo("채점될 제출");
	}

	@Test
	@DisplayName("findMySubmission - 제출이 없으면 빈 값, 있으면 내용 반환")
	void findMySubmission() {
		Assignment assignment = openAssignment();
		assertThat(submissionService.findMySubmission(student.getId(), assignment.getId())).isEmpty();

		submissionService.submit(student.getId(), assignment.getId(), request("내 제출"));

		SubmissionResponse response = submissionService.findMySubmission(student.getId(), assignment.getId())
				.orElseThrow();
		assertThat(response.content()).isEqualTo("내 제출");
		assertThat(response.isGraded()).isFalse();
	}

	@Test
	@DisplayName("findMySubmission - 수강 등록하지 않은 학생은 403 (B2)")
	void findMySubmission_notEnrolled() {
		Assignment assignment = openAssignment();
		User stranger = savedUser("stranger@test.com", Role.STUDENT, "20260003");

		assertThatThrownBy(() -> submissionService.findMySubmission(stranger.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}
}
