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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;
import kr.ac.ync.midterm.submission.dto.request.GradeRequest;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.GradeFormResponse;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.InvalidScoreException;
import kr.ac.ync.midterm.submission.exception.SubmissionNotFoundException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class GradingServiceImplTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final int MAX_SCORE = 100;

	@MockitoBean
	private Clock clock;

	@Autowired
	private GradingService gradingService;

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

	private User instructor;
	private User student;
	private Assignment assignment;
	private Submission submission;

	@BeforeEach
	void setUp() {
		when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
		when(clock.getZone()).thenReturn(ZONE);
		instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차").content("내용")
				.startAt(NOW.minusDays(1)).endAt(NOW.plusDays(1)).maxScore(MAX_SCORE).build());
		submission = submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content("학생 답안").submittedAt(NOW.minusHours(2)).build());
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name("홍길동")
				.studentNo(studentNo).role(role).build());
	}

	private GradeRequest request(Integer score, String feedback) {
		GradeRequest request = new GradeRequest();
		request.setScore(score);
		request.setFeedback(feedback);
		return request;
	}

	@Test
	@DisplayName("grade - 점수와 피드백이 저장되고 채점 완료 상태와 채점 시각이 기록됨")
	void grade_saves() {
		gradingService.grade(instructor.getId(), submission.getId(), request(85, "  잘 했어요  "));

		assertThat(submission.getScore()).isEqualTo(85);
		assertThat(submission.getFeedback()).isEqualTo("잘 했어요");
		assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.GRADED);
		assertThat(submission.getGradedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("grade - 피드백은 비워도 되고 빈 값은 null로 저장됨")
	void grade_blankFeedback() {
		gradingService.grade(instructor.getId(), submission.getId(), request(70, "   "));

		assertThat(submission.getFeedback()).isNull();
		assertThat(submission.isGraded()).isTrue();
	}

	@Test
	@DisplayName("grade - 0점과 배점과 같은 점수는 허용됨 (B6 경계)")
	void grade_boundariesAllowed() {
		gradingService.grade(instructor.getId(), submission.getId(), request(0, null));
		assertThat(submission.getScore()).isZero();

		gradingService.grade(instructor.getId(), submission.getId(), request(MAX_SCORE, null));
		assertThat(submission.getScore()).isEqualTo(MAX_SCORE);
	}

	@Test
	@DisplayName("grade - 음수와 배점 초과 점수는 거부되고 저장되지 않음 (B6)")
	void grade_outOfRange() {
		for (int invalid : new int[] { -1, MAX_SCORE + 1, Integer.MAX_VALUE, Integer.MIN_VALUE }) {
			assertThatThrownBy(() -> gradingService.grade(instructor.getId(), submission.getId(), request(invalid, "x")))
					.as("점수 " + invalid)
					.isInstanceOf(InvalidScoreException.class)
					.hasMessage("점수는 0 이상 100 이하여야 합니다.");
		}
		assertThat(submission.getScore()).isNull();
		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("grade - 점수가 없으면 거부됨")
	void grade_nullScore() {
		assertThatThrownBy(() -> gradingService.grade(instructor.getId(), submission.getId(), request(null, "x")))
				.isInstanceOf(InvalidScoreException.class);
		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("grade - 거부된 재채점은 기존 채점 결과를 바꾸지 않음")
	void grade_rejectedKeepsPrevious() {
		gradingService.grade(instructor.getId(), submission.getId(), request(80, "첫 채점"));

		assertThatThrownBy(() -> gradingService.grade(instructor.getId(), submission.getId(), request(500, "잘못")))
				.isInstanceOf(InvalidScoreException.class);

		assertThat(submission.getScore()).isEqualTo(80);
		assertThat(submission.getFeedback()).isEqualTo("첫 채점");
	}

	@Test
	@DisplayName("grade - 이미 채점한 제출을 다시 채점하면 점수 · 피드백 · 채점 시각이 바뀜")
	void grade_regrade() {
		gradingService.grade(instructor.getId(), submission.getId(), request(60, "첫 채점"));
		when(clock.instant()).thenReturn(NOW.plusDays(1).atZone(ZONE).toInstant());

		gradingService.grade(instructor.getId(), submission.getId(), request(95, "다시 채점"));

		assertThat(submission.getScore()).isEqualTo(95);
		assertThat(submission.getFeedback()).isEqualTo("다시 채점");
		assertThat(submission.getGradedAt()).isEqualTo(NOW.plusDays(1));
		assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.GRADED);
	}

	@Test
	@DisplayName("grade - 다른 강사의 강좌 제출은 403이고 저장되지 않음 (B5)")
	void grade_otherInstructor() {
		User other = savedUser("other-teacher@test.com", Role.INSTRUCTOR, null);

		assertThatThrownBy(() -> gradingService.grade(other.getId(), submission.getId(), request(90, "몰래 채점")))
				.isInstanceOf(ForbiddenException.class);

		assertThat(submission.isGraded()).isFalse();
		assertThat(submission.getScore()).isNull();
	}

	@Test
	@DisplayName("grade - 없는 제출은 404")
	void grade_notFound() {
		assertThatThrownBy(() -> gradingService.grade(instructor.getId(), 999999L, request(50, null)))
				.isInstanceOf(SubmissionNotFoundException.class);
	}

	@Test
	@DisplayName("채점 후에는 학생이 다시 제출할 수 없음 (B4, 실제 채점 서비스로 확인)")
	void afterGrading_resubmitRejected() {
		gradingService.grade(instructor.getId(), submission.getId(), request(88, "채점 완료"));
		SubmissionRequest resubmit = new SubmissionRequest();
		resubmit.setContent("고치려는 내용");

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(), resubmit))
				.isInstanceOf(AlreadyGradedException.class);

		assertThat(submission.getContent()).isEqualTo("학생 답안");
	}

	@Test
	@DisplayName("findForGrading - 학생 · 제출 · 과제 정보와 기존 채점 결과가 담김")
	void findForGrading() {
		GradeFormResponse before = gradingService.findForGrading(instructor.getId(), submission.getId());
		assertThat(before.studentNo()).isEqualTo("20260001");
		assertThat(before.assignmentTitle()).isEqualTo("1주차");
		assertThat(before.maxScore()).isEqualTo(MAX_SCORE);
		assertThat(before.content()).isEqualTo("학생 답안");
		assertThat(before.isGraded()).isFalse();
		assertThat(before.score()).isNull();

		gradingService.grade(instructor.getId(), submission.getId(), request(77, "피드백"));
		GradeFormResponse after = gradingService.findForGrading(instructor.getId(), submission.getId());
		assertThat(after.isGraded()).isTrue();
		assertThat(after.score()).isEqualTo(77);
		assertThat(after.feedback()).isEqualTo("피드백");
	}

	@Test
	@DisplayName("findForGrading - 다른 강사는 403, 없는 제출은 404 (B5)")
	void findForGrading_denied() {
		User other = savedUser("other-teacher@test.com", Role.INSTRUCTOR, null);

		assertThatThrownBy(() -> gradingService.findForGrading(other.getId(), submission.getId()))
				.isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> gradingService.findForGrading(instructor.getId(), 999999L))
				.isInstanceOf(SubmissionNotFoundException.class);
	}
}
