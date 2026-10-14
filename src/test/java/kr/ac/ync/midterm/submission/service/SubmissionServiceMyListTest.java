package kr.ac.ync.midterm.submission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.response.MySubmissionResponse;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 내 제출 목록 조회 (S4): 본인 것만, 최근 제출순, 수강 중인 강좌만, 채점 결과 값.
 */
@SpringBootTest
@Transactional
class SubmissionServiceMyListTest {

	private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 10, 12, 0);

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
	private User me;
	private Course course;

	@BeforeEach
	void setUp() {
		instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		me = savedUser("me@test.com", Role.STUDENT, "20260001");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(me).build());
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name("홍길동")
				.studentNo(studentNo).role(role).build());
	}

	private Assignment assignmentIn(Course target, String title, int maxScore) {
		return assignmentRepository.save(Assignment.builder().course(target).title(title).content("내용")
				.startAt(T0.minusDays(1)).endAt(T0.plusDays(1)).maxScore(maxScore).build());
	}

	private Submission submit(Assignment assignment, User student, LocalDateTime at) {
		return submissionRepository.save(Submission.builder().assignment(assignment).student(student).content("답안")
				.submittedAt(at).build());
	}

	@Test
	@DisplayName("findMySubmissions - 본인의 제출만 최근 제출순으로 조회됨")
	void onlyMineNewestFirst() {
		User other = savedUser("other@test.com", Role.STUDENT, "20260002");
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());
		Assignment a1 = assignmentIn(course, "1주차", 100);
		Assignment a2 = assignmentIn(course, "2주차", 50);
		submit(a1, me, T0.minusHours(3));
		submit(a2, me, T0.minusHours(1));
		submit(a1, other, T0);

		List<MySubmissionResponse> result = submissionService.findMySubmissions(me.getId());

		assertThat(result).extracting(MySubmissionResponse::assignmentTitle).containsExactly("2주차", "1주차");
		assertThat(result.getFirst().courseName()).isEqualTo("자바스프링");
		assertThat(result.getFirst().maxScore()).isEqualTo(50);
	}

	@Test
	@DisplayName("findMySubmissions - 제출이 없으면 빈 목록")
	void empty() {
		assertThat(submissionService.findMySubmissions(me.getId())).isEmpty();
	}

	@Test
	@DisplayName("findMySubmissions - 수강 중이 아닌 강좌의 제출은 나오지 않음")
	void notEnrolledCourseExcluded() {
		Course otherCourse = courseRepository.save(
				Course.builder().instructor(instructor).name("데이터베이스").joinCode("BBBBB3").build());
		submit(assignmentIn(otherCourse, "수강하지 않는 강좌 과제", 100), me, T0);

		assertThat(submissionService.findMySubmissions(me.getId())).isEmpty();
	}

	@Test
	@DisplayName("findMySubmissions - 채점 전에는 점수가 없고 채점 후에는 상태와 점수가 담김")
	void gradedValues() {
		Assignment a1 = assignmentIn(course, "1주차", 100);
		Assignment a2 = assignmentIn(course, "2주차", 100);
		submit(a1, me, T0.minusHours(2));
		Submission graded = submit(a2, me, T0.minusHours(1));
		graded.grade(88, "피드백", T0);

		List<MySubmissionResponse> result = submissionService.findMySubmissions(me.getId());

		MySubmissionResponse gradedRow = result.get(0);
		assertThat(gradedRow.isGraded()).isTrue();
		assertThat(gradedRow.score()).isEqualTo(88);
		MySubmissionResponse waitingRow = result.get(1);
		assertThat(waitingRow.isGraded()).isFalse();
		assertThat(waitingRow.score()).isNull();
	}
}
