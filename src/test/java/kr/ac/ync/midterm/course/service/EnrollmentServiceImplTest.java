package kr.ac.ync.midterm.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.dto.request.EnrollRequest;
import kr.ac.ync.midterm.course.dto.response.EnrollmentResponse;
import kr.ac.ync.midterm.course.exception.AlreadyEnrolledException;
import kr.ac.ync.midterm.course.exception.InvalidJoinCodeException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class EnrollmentServiceImplTest {

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

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
				Course.builder()
						.instructor(instructor)
						.name(name)
						.joinCode(joinCode)
						.build()
		);
	}

	private EnrollRequest request(String joinCode) {
		EnrollRequest request = new EnrollRequest();
		request.setJoinCode(joinCode);
		return request;
	}

	@Test
	@DisplayName("enroll - 올바른 참여코드면 수강 등록되고 강좌명이 반환됨")
	void enroll_success() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		// when
		EnrollmentResponse response = enrollmentService.enroll(student.getId(), request("AAAAA2"));
		// then
		assertThat(response.courseId()).isEqualTo(course.getId());
		assertThat(response.courseName()).isEqualTo("자바스프링");
		assertThat(response.joinedAt()).isNotNull();
		assertThat(enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), student.getId())).isTrue();
	}

	@Test
	@DisplayName("enroll - 앞뒤 공백과 대소문자 차이는 무시")
	void enroll_caseAndSpaceInsensitive() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		// when
		enrollmentService.enroll(student.getId(), request("  aaaaa2 "));
		// then
		assertThat(enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), student.getId())).isTrue();
	}

	@Test
	@DisplayName("enroll - 존재하지 않는 참여코드면 예외 (등록되지 않음)")
	void enroll_invalidCode() {
		// given
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), request("ZZZZZZ")))
				.isInstanceOf(InvalidJoinCodeException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
		assertThat(enrollmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("enroll - 이미 등록한 강좌면 예외이고 중복 저장되지 않음")
	void enroll_duplicate() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		enrollmentService.enroll(student.getId(), request("AAAAA2"));
		// when & then
		assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), request("AAAAA2")))
				.isInstanceOf(AlreadyEnrolledException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
		assertThat(enrollmentRepository.findByCourseIdOrderByJoinedAtAsc(course.getId())).hasSize(1);
	}

	@Test
	@DisplayName("enroll - 같은 학생이 다른 강좌에는, 다른 학생이 같은 강좌에는 등록할 수 있음")
	void enroll_differentCombinations() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course java = savedCourse(instructor, "자바스프링", "AAAAA2");
		Course db = savedCourse(instructor, "데이터베이스", "BBBBB3");
		User first = savedUser("first@test.com", Role.STUDENT, "20260001");
		User second = savedUser("second@test.com", Role.STUDENT, "20260002");
		// when
		enrollmentService.enroll(first.getId(), request("AAAAA2"));
		enrollmentService.enroll(first.getId(), request("BBBBB3"));
		enrollmentService.enroll(second.getId(), request("AAAAA2"));
		// then
		assertThat(enrollmentRepository.findByCourseIdOrderByJoinedAtAsc(java.getId())).hasSize(2);
		assertThat(enrollmentRepository.findByCourseIdOrderByJoinedAtAsc(db.getId())).hasSize(1);
	}
}
