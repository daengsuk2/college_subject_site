package kr.ac.ync.midterm.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse.NotSubmittedRow;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse.SubmittedRow;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class SubmissionStatusServiceImplTest {

	private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 10, 12, 0);

	@Autowired
	private SubmissionStatusService submissionStatusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private User instructor;
	private Course course;
	private Assignment assignment;

	@BeforeEach
	void setUp() {
		instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null, "강사");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차").content("내용")
				.startAt(T0.minusDays(1)).endAt(T0.plusDays(1)).maxScore(100).build());
	}

	private User savedUser(String email, Role role, String studentNo, String name) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name(name)
				.studentNo(studentNo).role(role).build());
	}

	private User enrolledStudent(String email, String studentNo, String name) {
		User student = savedUser(email, Role.STUDENT, studentNo, name);
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		return student;
	}

	private Submission submit(User student, LocalDateTime at, String fileName) {
		Submission submission = Submission.builder().assignment(assignment).student(student).content("답안")
				.submittedAt(at).build();
		if (fileName != null) {
			submission.attachFile("stored-" + student.getId() + ".pdf", fileName);
		}
		return submissionRepository.save(submission);
	}

	@Test
	@DisplayName("findStatus - 수강생 3명 중 2명이 제출하면 제출 2명, 미제출 1명")
	void findStatus_counts() {
		User a = enrolledStudent("a@test.com", "20260001", "가나다");
		User b = enrolledStudent("b@test.com", "20260002", "나다라");
		User c = enrolledStudent("c@test.com", "20260003", "다라마");
		submit(a, T0, null);
		submit(b, T0.plusHours(1), null);

		SubmissionStatusResponse status = submissionStatusService.findStatus(instructor.getId(), assignment.getId());

		assertThat(status.enrolledCount()).isEqualTo(3);
		assertThat(status.submittedCount()).isEqualTo(2);
		assertThat(status.notSubmittedCount()).isEqualTo(1);
		assertThat(status.submitted()).extracting(SubmittedRow::studentNo).containsExactly("20260001", "20260002");
		assertThat(status.notSubmitted()).extracting(NotSubmittedRow::studentNo).containsExactly(c.getStudentNo());
		assertThat(status.courseName()).isEqualTo("자바스프링");
		assertThat(status.assignmentTitle()).isEqualTo("1주차");
	}

	@Test
	@DisplayName("findStatus - 제출자는 제출 시각이 이른 순, 미제출자는 이름 순")
	void findStatus_ordering() {
		User late = enrolledStudent("late@test.com", "20260001", "하하");
		User early = enrolledStudent("early@test.com", "20260002", "가가");
		enrolledStudent("z@test.com", "20260003", "마마");
		enrolledStudent("y@test.com", "20260004", "나나");
		submit(late, T0.plusHours(5), null);
		submit(early, T0.plusHours(1), null);

		SubmissionStatusResponse status = submissionStatusService.findStatus(instructor.getId(), assignment.getId());

		assertThat(status.submitted()).extracting(SubmittedRow::studentName).containsExactly("가가", "하하");
		assertThat(status.notSubmitted()).extracting(NotSubmittedRow::studentName).containsExactly("나나", "마마");
	}

	@Test
	@DisplayName("findStatus - 아무도 제출하지 않으면 모두 미제출, 모두 제출하면 미제출 없음")
	void findStatus_noneAndAll() {
		User a = enrolledStudent("a@test.com", "20260001", "가나다");
		User b = enrolledStudent("b@test.com", "20260002", "나다라");

		SubmissionStatusResponse none = submissionStatusService.findStatus(instructor.getId(), assignment.getId());
		assertThat(none.submitted()).isEmpty();
		assertThat(none.notSubmitted()).hasSize(2);

		submit(a, T0, null);
		submit(b, T0, null);
		SubmissionStatusResponse all = submissionStatusService.findStatus(instructor.getId(), assignment.getId());
		assertThat(all.submitted()).hasSize(2);
		assertThat(all.notSubmitted()).isEmpty();
	}

	@Test
	@DisplayName("findStatus - 수강생이 없는 강좌는 모두 0")
	void findStatus_noStudents() {
		SubmissionStatusResponse status = submissionStatusService.findStatus(instructor.getId(), assignment.getId());

		assertThat(status.enrolledCount()).isZero();
		assertThat(status.submitted()).isEmpty();
		assertThat(status.notSubmitted()).isEmpty();
	}

	@Test
	@DisplayName("findStatus - 수강생이 아닌 학생의 제출은 제출자에 섞이지 않음")
	void findStatus_ignoresNonEnrolled() {
		User enrolled = enrolledStudent("a@test.com", "20260001", "가나다");
		User outsider = savedUser("out@test.com", Role.STUDENT, "20269999", "외부인");
		submit(enrolled, T0, null);
		submit(outsider, T0, null);

		SubmissionStatusResponse status = submissionStatusService.findStatus(instructor.getId(), assignment.getId());

		assertThat(status.submitted()).extracting(SubmittedRow::studentNo).containsExactly("20260001");
		assertThat(status.enrolledCount()).isEqualTo(1);
		assertThat(status.notSubmitted()).isEmpty();
	}

	@Test
	@DisplayName("findStatus - 다른 과제의 제출은 섞이지 않음")
	void findStatus_otherAssignment() {
		User a = enrolledStudent("a@test.com", "20260001", "가나다");
		Assignment other = assignmentRepository.save(Assignment.builder().course(course).title("2주차").content("내용")
				.startAt(T0.minusDays(1)).endAt(T0.plusDays(1)).maxScore(100).build());
		submissionRepository.save(Submission.builder().assignment(other).student(a).content("다른 과제 답안")
				.submittedAt(T0).build());

		SubmissionStatusResponse status = submissionStatusService.findStatus(instructor.getId(), assignment.getId());

		assertThat(status.submitted()).isEmpty();
		assertThat(status.notSubmitted()).extracting(NotSubmittedRow::studentNo).containsExactly("20260001");
	}

	@Test
	@DisplayName("findStatus - 채점 완료 여부, 점수, 첨부파일 이름이 담김")
	void findStatus_rowDetails() {
		User a = enrolledStudent("a@test.com", "20260001", "가나다");
		User b = enrolledStudent("b@test.com", "20260002", "나다라");
		Submission graded = submit(a, T0, "답안.pdf");
		ReflectionTestUtils.setField(graded, "status", SubmissionStatus.GRADED);
		ReflectionTestUtils.setField(graded, "score", 90);
		submit(b, T0.plusHours(1), null);

		SubmissionStatusResponse status = submissionStatusService.findStatus(instructor.getId(), assignment.getId());

		SubmittedRow first = status.submitted().get(0);
		assertThat(first.isGraded()).isTrue();
		assertThat(first.score()).isEqualTo(90);
		assertThat(first.originalFilename()).isEqualTo("답안.pdf");
		assertThat(first.hasFile()).isTrue();
		SubmittedRow second = status.submitted().get(1);
		assertThat(second.isGraded()).isFalse();
		assertThat(second.score()).isNull();
		assertThat(second.hasFile()).isFalse();
	}

	@Test
	@DisplayName("findStatus - 다른 강사의 과제는 403 (B5)")
	void findStatus_otherInstructor() {
		User other = savedUser("other-teacher@test.com", Role.INSTRUCTOR, null, "다른강사");

		assertThatThrownBy(() -> submissionStatusService.findStatus(other.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("findStatus - 없는 과제는 404")
	void findStatus_notFound() {
		assertThatThrownBy(() -> submissionStatusService.findStatus(instructor.getId(), 999999L))
				.isInstanceOf(AssignmentNotFoundException.class);
	}
}
