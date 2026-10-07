package kr.ac.ync.midterm.course.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

class StudentCourseControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	private Course course;

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Course savedCourse() {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build()
		);
		return course;
	}

	@Test
	@DisplayName("GET /student/courses/join - 학생은 참여코드 입력 화면을 볼 수 있음")
	void joinForm() throws Exception {
		mockMvc.perform(get("/student/courses/join").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("student/join"));
	}

	@Test
	@DisplayName("POST /student/courses/join - 1.정상 등록 (소문자 · 공백 입력도 허용)")
	void join_success() throws Exception {
		// given
		Course saved = savedCourse();

		// when & then
		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(student)))
						.with(csrf())
						.param("joinCode", "  aaaaa2 "))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/student/courses/join"))
				.andExpect(flash().attribute("message", containsString("자바스프링")));

		assertThat(enrollmentRepository.existsByCourseIdAndStudentId(saved.getId(), student.getId())).isTrue();
	}

	@Test
	@DisplayName("POST /student/courses/join - 2.존재하지 않는 참여코드면 폼 에러 (등록 안 됨)")
	void join_invalidCode() throws Exception {
		// given
		savedCourse();

		// when & then
		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(student)))
						.with(csrf())
						.param("joinCode", "ZZZZZZ"))
				.andExpect(status().isOk())
				.andExpect(view().name("student/join"))
				.andExpect(model().attributeHasFieldErrors("enrollRequest", "joinCode"));

		assertThat(enrollmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST /student/courses/join - 3.참여코드가 비어 있으면 폼 에러")
	void join_blankCode() throws Exception {
		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(student)))
						.with(csrf())
						.param("joinCode", "   "))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("enrollRequest", "joinCode"));
	}

	@Test
	@DisplayName("POST /student/courses/join - 4.이미 등록한 강좌면 폼 에러이고 중복 저장되지 않음")
	void join_alreadyEnrolled() throws Exception {
		// given
		Course saved = savedCourse();
		mockMvc.perform(post("/student/courses/join")
				.with(user(principal(student))).with(csrf()).param("joinCode", "AAAAA2"));

		// when & then
		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(student)))
						.with(csrf())
						.param("joinCode", "AAAAA2"))
				.andExpect(status().isOk())
				.andExpect(view().name("student/join"))
				.andExpect(model().attributeHasFieldErrors("enrollRequest", "joinCode"));

		assertThat(enrollmentRepository.findByCourseIdOrderByJoinedAtAsc(saved.getId())).hasSize(1);
	}

	@Test
	@DisplayName("/student/courses/join - 강사는 403")
	void instructor_forbidden() throws Exception {
		// given
		User instructor = createUser("another@test.com", Role.INSTRUCTOR);
		savedCourse();

		// when & then
		mockMvc.perform(get("/student/courses/join").with(user(principal(instructor))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(instructor)))
						.with(csrf())
						.param("joinCode", "AAAAA2"))
				.andExpect(status().isForbidden());

		assertThat(enrollmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST /student/courses/join - CSRF 토큰이 없으면 403")
	void join_withoutCsrf() throws Exception {
		// given
		savedCourse();

		// when & then
		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(student)))
						.param("joinCode", "AAAAA2"))
				.andExpect(status().isForbidden());

		assertThat(enrollmentRepository.count()).isZero();
	}
}
