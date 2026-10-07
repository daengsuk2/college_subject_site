package kr.ac.ync.midterm.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;
import kr.ac.ync.midterm.assignment.dto.response.StudentAssignmentResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class StudentAssignmentServiceImplTest {

	// 현재 시각을 고정해 상태 판정과 정렬을 확인한다
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
	private static final ZoneId ZONE = ZoneId.systemDefault();

	@MockitoBean
	private Clock clock;

	@Autowired
	private StudentAssignmentService studentAssignmentService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@BeforeEach
	void fixClock() {
		when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
		when(clock.getZone()).thenReturn(ZONE);
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(
				User.builder()
						.email(email)
						.password("encodedPassword")
						.name("홍길동")
						.studentNo(studentNo)
						.role(role)
						.build()
		);
	}

	private Course savedCourse(User instructor, String name, String joinCode) {
		return courseRepository.save(
				Course.builder().instructor(instructor).name(name).joinCode(joinCode).build()
		);
	}

	private Assignment savedAssignment(Course course, String title, LocalDateTime startAt, LocalDateTime endAt) {
		return assignmentRepository.save(
				Assignment.builder()
						.course(course)
						.title(title)
						.content("내용")
						.startAt(startAt)
						.endAt(endAt)
						.maxScore(100)
						.build()
		);
	}

	private void enroll(Course course, User student) {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
	}

	@Test
	@DisplayName("findMyAssignments - 수강 등록한 강좌의 과제만 조회됨 (B2)")
	void findMyAssignments_onlyEnrolledCourses() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course enrolled = savedCourse(instructor, "자바스프링", "AAAAA2");
		Course notEnrolled = savedCourse(instructor, "데이터베이스", "BBBBB3");
		enroll(enrolled, student);
		savedAssignment(enrolled, "내 강좌 과제", NOW.minusDays(1), NOW.plusDays(1));
		savedAssignment(notEnrolled, "수강하지 않는 강좌 과제", NOW.minusDays(1), NOW.plusDays(1));

		// when
		List<StudentAssignmentResponse> result = studentAssignmentService.findMyAssignments(student.getId());

		// then
		assertThat(result).extracting(StudentAssignmentResponse::title).containsExactly("내 강좌 과제");
		assertThat(result.getFirst().courseName()).isEqualTo("자바스프링");
	}

	@Test
	@DisplayName("findMyAssignments - 다른 학생의 수강 강좌 과제는 보이지 않음 (B2)")
	void findMyAssignments_otherStudentsCourse() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User me = savedUser("me@test.com", Role.STUDENT, "20260001");
		User other = savedUser("other@test.com", Role.STUDENT, "20260002");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enroll(course, other);
		savedAssignment(course, "다른 학생이 수강하는 강좌 과제", NOW.minusDays(1), NOW.plusDays(1));

		// when & then
		assertThat(studentAssignmentService.findMyAssignments(me.getId())).isEmpty();
	}

	@Test
	@DisplayName("findMyAssignments - 상태가 현재 시각 기준으로 계산됨 (예정 · 진행중 · 마감)")
	void findMyAssignments_statuses() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enroll(course, student);
		savedAssignment(course, "예정 과제", NOW.plusDays(1), NOW.plusDays(2));
		savedAssignment(course, "진행중 과제", NOW.minusDays(1), NOW.plusDays(1));
		savedAssignment(course, "마감 과제", NOW.minusDays(3), NOW.minusDays(2));

		// when
		List<StudentAssignmentResponse> result = studentAssignmentService.findMyAssignments(student.getId());

		// then
		assertThat(result).extracting(StudentAssignmentResponse::title, StudentAssignmentResponse::status)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("예정 과제", AssignmentStatus.UPCOMING),
						org.assertj.core.groups.Tuple.tuple("진행중 과제", AssignmentStatus.IN_PROGRESS),
						org.assertj.core.groups.Tuple.tuple("마감 과제", AssignmentStatus.CLOSED));
	}

	@Test
	@DisplayName("findMyAssignments - 시작 · 종료 정각은 진행중, 종료 1초 뒤는 마감 (경계값)")
	void findMyAssignments_boundaries() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enroll(course, student);
		savedAssignment(course, "지금 시작", NOW, NOW.plusDays(1));
		savedAssignment(course, "지금 종료", NOW.minusDays(1), NOW);
		savedAssignment(course, "1초 전 종료", NOW.minusDays(1), NOW.minusSeconds(1));
		savedAssignment(course, "1초 뒤 시작", NOW.plusSeconds(1), NOW.plusDays(1));

		// when
		List<StudentAssignmentResponse> result = studentAssignmentService.findMyAssignments(student.getId());

		// then
		assertThat(result).extracting(StudentAssignmentResponse::title, StudentAssignmentResponse::status)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("지금 시작", AssignmentStatus.IN_PROGRESS),
						org.assertj.core.groups.Tuple.tuple("지금 종료", AssignmentStatus.IN_PROGRESS),
						org.assertj.core.groups.Tuple.tuple("1초 전 종료", AssignmentStatus.CLOSED),
						org.assertj.core.groups.Tuple.tuple("1초 뒤 시작", AssignmentStatus.UPCOMING));
	}

	@Test
	@DisplayName("findMyAssignments - 진행중 → 예정 → 마감 순, 같은 상태는 종료일시가 이른 순")
	void findMyAssignments_order() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enroll(course, student);
		savedAssignment(course, "마감", NOW.minusDays(3), NOW.minusDays(2));
		savedAssignment(course, "예정 늦게", NOW.plusDays(5), NOW.plusDays(9));
		savedAssignment(course, "진행중 늦게 종료", NOW.minusDays(1), NOW.plusDays(5));
		savedAssignment(course, "예정 빨리", NOW.plusDays(1), NOW.plusDays(3));
		savedAssignment(course, "진행중 빨리 종료", NOW.minusDays(1), NOW.plusDays(1));

		// when
		List<StudentAssignmentResponse> result = studentAssignmentService.findMyAssignments(student.getId());

		// then
		assertThat(result).extracting(StudentAssignmentResponse::title)
				.containsExactly("진행중 빨리 종료", "진행중 늦게 종료", "예정 빨리", "예정 늦게", "마감");
	}

	@Test
	@DisplayName("findMyAssignments - 수강 중인 강좌가 없으면 빈 목록")
	void findMyAssignments_noEnrollment() {
		// given
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");

		// when & then
		assertThat(studentAssignmentService.findMyAssignments(student.getId())).isEmpty();
	}

	@Test
	@DisplayName("findMyAssignment - 수강 등록한 강좌의 과제 상세가 조회됨")
	void findMyAssignment_success() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enroll(course, student);
		Assignment assignment = savedAssignment(course, "1주차", NOW.minusDays(1), NOW.plusDays(1));

		// when
		StudentAssignmentResponse response = studentAssignmentService.findMyAssignment(student.getId(), assignment.getId());

		// then
		assertThat(response.title()).isEqualTo("1주차");
		assertThat(response.content()).isEqualTo("내용");
		assertThat(response.courseName()).isEqualTo("자바스프링");
		assertThat(response.maxScore()).isEqualTo(100);
		assertThat(response.status()).isEqualTo(AssignmentStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("findMyAssignment - 수강 등록하지 않은 강좌의 과제는 403 (B2)")
	void findMyAssignment_notEnrolled() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		Assignment assignment = savedAssignment(course, "1주차", NOW.minusDays(1), NOW.plusDays(1));

		// when & then
		assertThatThrownBy(() -> studentAssignmentService.findMyAssignment(student.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("findMyAssignment - 다른 학생만 수강하는 강좌의 과제는 403 (B2)")
	void findMyAssignment_otherStudentEnrolled() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User me = savedUser("me@test.com", Role.STUDENT, "20260001");
		User other = savedUser("other@test.com", Role.STUDENT, "20260002");
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enroll(course, other);
		Assignment assignment = savedAssignment(course, "1주차", NOW.minusDays(1), NOW.plusDays(1));

		// when & then
		assertThatThrownBy(() -> studentAssignmentService.findMyAssignment(me.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("findMyAssignment - 존재하지 않는 과제면 404")
	void findMyAssignment_notFound() {
		// given
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");

		// when & then
		assertThatThrownBy(() -> studentAssignmentService.findMyAssignment(student.getId(), 999_999L))
				.isInstanceOf(AssignmentNotFoundException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
	}
}
