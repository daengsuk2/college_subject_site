package kr.ac.ync.midterm.course.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

class InstructorCourseControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Course savedCourse(User instructor, String name, String joinCode) {
		return courseRepository.save(
				Course.builder().instructor(instructor).name(name).joinCode(joinCode).build()
		);
	}

	// ---------- I1 강좌 개설 ----------

	@Test
	@DisplayName("GET /instructor/courses - 본인 강좌만 표시됨 (B5)")
	void list_onlyMine() throws Exception {
		// given
		User instructorA = createUser("a@test.com", Role.INSTRUCTOR);
		User instructorB = createUser("b@test.com", Role.INSTRUCTOR);
		savedCourse(instructorA, "A의 강좌", "AAAAA2");
		savedCourse(instructorB, "B의 강좌", "BBBBB3");

		// when & then
		mockMvc.perform(get("/instructor/courses").with(user(principal(instructorA))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/courses"))
				.andExpect(content().string(containsString("A의 강좌")))
				.andExpect(content().string(containsString("AAAAA2")))
				.andExpect(content().string(not(containsString("B의 강좌"))));
	}

	@Test
	@DisplayName("GET /instructor/courses - 강좌가 없으면 안내 문구")
	void list_empty() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/instructor/courses").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("개설한 강좌가 없습니다.")));
	}

	@Test
	@DisplayName("POST /instructor/courses - 1.정상 개설 시 참여코드 안내와 함께 목록으로 이동")
	void create_success() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		// when & then
		mockMvc.perform(post("/instructor/courses")
						.with(user(principal(instructor)))
						.with(csrf())
						.param("name", "자바스프링"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses"))
				.andExpect(flash().attribute("message", containsString("참여코드")));

		assertThat(courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId()))
				.hasSize(1)
				.first()
				.satisfies(course -> assertThat(course.getJoinCode()).matches("[A-HJ-NP-Z2-9]{6}"));
	}

	@Test
	@DisplayName("POST /instructor/courses - 2.강좌명이 비어 있으면 폼 에러 (저장 안 됨)")
	void create_blankName() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		// when & then
		mockMvc.perform(post("/instructor/courses")
						.with(user(principal(instructor)))
						.with(csrf())
						.param("name", "   "))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/courses"))
				.andExpect(model().attributeHasFieldErrors("courseCreateRequest", "name"));

		assertThat(courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId())).isEmpty();
	}

	@Test
	@DisplayName("POST /instructor/courses - 3.강좌명이 100자를 넘으면 폼 에러")
	void create_nameTooLong() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		// when & then
		mockMvc.perform(post("/instructor/courses")
						.with(user(principal(instructor)))
						.with(csrf())
						.param("name", "가".repeat(101)))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("courseCreateRequest", "name"));
	}

	@Test
	@DisplayName("/instructor/courses - 학생은 목록 · 개설 모두 403")
	void student_forbidden() throws Exception {
		mockMvc.perform(get("/instructor/courses").with(user(principal(student))))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/instructor/courses")
						.with(user(principal(student)))
						.with(csrf())
						.param("name", "침입 강좌"))
				.andExpect(status().isForbidden());

		assertThat(courseRepository.count()).isZero();
	}

	// ---------- I2 강좌 상세 ----------

	@Test
	@DisplayName("GET /instructor/courses/{id} - 본인 강좌는 참여코드와 수강생이 표시됨")
	void detail_owner() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());

		// when & then
		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/course-detail"))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(containsString("AAAAA2")))
				.andExpect(content().string(containsString("student@test.com")));
	}

	@Test
	@DisplayName("GET /instructor/courses/{id} - 수강생이 없으면 안내 문구")
	void detail_noStudents() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");

		// when & then
		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("등록된 수강생이 없습니다.")));
	}

	@Test
	@DisplayName("GET /instructor/courses/{id} - 다른 강사의 강좌는 403 화면 (B5)")
	void detail_otherInstructor() throws Exception {
		// given
		User owner = createUser("owner@test.com", Role.INSTRUCTOR);
		User other = createUser("other@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(owner, "자바스프링", "AAAAA2");

		// when & then
		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(other))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"))
				.andExpect(content().string(not(containsString("AAAAA2"))));
	}

	@Test
	@DisplayName("GET /instructor/courses/{id} - 존재하지 않는 강좌는 404 화면")
	void detail_notFound() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		// when & then
		mockMvc.perform(get("/instructor/courses/{id}", 999_999L).with(user(principal(instructor))))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));
	}

	@Test
	@DisplayName("GET /instructor/courses/{id} - 학생은 403")
	void detail_student() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");

		// when & then
		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(student))))
				.andExpect(status().isForbidden());
	}
}
