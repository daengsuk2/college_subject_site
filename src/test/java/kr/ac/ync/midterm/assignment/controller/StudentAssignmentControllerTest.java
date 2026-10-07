package kr.ac.ync.midterm.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

class StudentAssignmentControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Course savedCourse(String name, String joinCode) {
		User instructor = userInstructor();
		return courseRepository.save(
				Course.builder().instructor(instructor).name(name).joinCode(joinCode).build());
	}

	private User instructor;

	private User userInstructor() {
		if (instructor == null) {
			instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		}
		return instructor;
	}

	private Assignment savedAssignment(Course course, String title, LocalDateTime startAt, LocalDateTime endAt) {
		return assignmentRepository.save(
				Assignment.builder().course(course).title(title).content("과제 내용입니다")
						.startAt(startAt).endAt(endAt).maxScore(100).build());
	}

	private void enroll(Course course, User user) {
		enrollmentRepository.save(Enrollment.builder().course(course).student(user).build());
	}

	@Test
	@DisplayName("GET /student/assignments - 수강 강좌의 과제만 상태 배지와 함께 표시됨 (B2)")
	void list_onlyEnrolledWithBadges() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		Course enrolled = savedCourse("자바스프링", "AAAAA2");
		Course notEnrolled = savedCourse("데이터베이스", "BBBBB3");
		enroll(enrolled, student);
		savedAssignment(enrolled, "예정 과제", now.plusDays(1), now.plusDays(2));
		savedAssignment(enrolled, "진행중 과제", now.minusDays(1), now.plusDays(1));
		savedAssignment(enrolled, "마감 과제", now.minusDays(3), now.minusDays(2));
		savedAssignment(notEnrolled, "수강하지 않는 과제", now.minusDays(1), now.plusDays(1));

		// when & then
		mockMvc.perform(get("/student/assignments").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("student/assignments"))
				.andExpect(content().string(containsString("예정 과제")))
				.andExpect(content().string(containsString("진행중 과제")))
				.andExpect(content().string(containsString("마감 과제")))
				.andExpect(content().string(containsString("badge-secondary")))
				.andExpect(content().string(containsString("badge-success")))
				.andExpect(content().string(containsString("badge-danger")))
				.andExpect(content().string(not(containsString("수강하지 않는 과제"))));
	}

	@Test
	@DisplayName("GET /student/assignments - 진행중 → 예정 → 마감 순으로 표시됨")
	void list_order() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		Course course = savedCourse("자바스프링", "AAAAA2");
		enroll(course, student);
		savedAssignment(course, "마감-과제", now.minusDays(3), now.minusDays(2));
		savedAssignment(course, "예정-과제", now.plusDays(1), now.plusDays(2));
		savedAssignment(course, "진행중-과제", now.minusDays(1), now.plusDays(1));

		// when
		String html = mockMvc.perform(get("/student/assignments").with(user(principal(student))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// then
		assertThat(html.indexOf("진행중-과제")).isPositive()
				.isLessThan(html.indexOf("예정-과제"));
		assertThat(html.indexOf("예정-과제")).isLessThan(html.indexOf("마감-과제"));
	}

	@Test
	@DisplayName("GET /student/assignments - 수강 중인 강좌가 없으면 안내 문구")
	void list_empty() throws Exception {
		mockMvc.perform(get("/student/assignments").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("표시할 과제가 없습니다.")));
	}

	@Test
	@DisplayName("GET /student/assignments - 강사는 403")
	void list_instructor() throws Exception {
		mockMvc.perform(get("/student/assignments").with(user(principal(userInstructor()))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /student/assignments/{id} - 수강 중인 강좌의 과제 상세 (내용 · 강좌명 · 배지)")
	void detail_enrolled() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		Course course = savedCourse("자바스프링", "AAAAA2");
		enroll(course, student);
		Assignment assignment = savedAssignment(course, "1주차 과제", now.minusDays(1), now.plusDays(1));

		// when & then
		mockMvc.perform(get("/student/assignments/{id}", assignment.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("student/assignment-detail"))
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(containsString("과제 내용입니다")))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(containsString("진행중")))
				.andExpect(content().string(containsString("badge-success")));
	}

	@Test
	@DisplayName("GET /student/assignments/{id} - 수강하지 않는 강좌의 과제는 403 화면 (B2)")
	void detail_notEnrolled() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		Course course = savedCourse("자바스프링", "AAAAA2");
		Assignment assignment = savedAssignment(course, "비공개 과제", now.minusDays(1), now.plusDays(1));

		// when & then
		mockMvc.perform(get("/student/assignments/{id}", assignment.getId()).with(user(principal(student))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"))
				.andExpect(content().string(not(containsString("과제 내용입니다"))));
	}

	@Test
	@DisplayName("GET /student/assignments/{id} - 존재하지 않는 과제는 404 화면")
	void detail_notFound() throws Exception {
		mockMvc.perform(get("/student/assignments/{id}", 999_999L).with(user(principal(student))))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));
	}

	@Test
	@DisplayName("GET /student/assignments/{id} - 강사는 403")
	void detail_instructor() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		Course course = savedCourse("자바스프링", "AAAAA2");
		Assignment assignment = savedAssignment(course, "1주차", now.minusDays(1), now.plusDays(1));

		// when & then
		mockMvc.perform(get("/student/assignments/{id}", assignment.getId())
						.with(user(principal(userInstructor()))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("XSS - 과제 제목 · 내용의 스크립트는 목록과 상세에서 이스케이프되어 출력됨")
	void xss_titleAndContent() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		Course course = savedCourse("자바스프링", "AAAAA2");
		enroll(course, student);
		Assignment assignment = assignmentRepository.save(
				Assignment.builder().course(course).title("<script>alert(1)</script>")
						.content("<img src=x onerror=alert(2)>")
						.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(10).build());

		// when & then
		mockMvc.perform(get("/student/assignments").with(user(principal(student))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))));

		mockMvc.perform(get("/student/assignments/{id}", assignment.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(2)&gt;")))
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(2)>"))));
	}
}
