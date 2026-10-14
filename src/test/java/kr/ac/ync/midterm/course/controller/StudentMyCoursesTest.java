package kr.ac.ync.midterm.course.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
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
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 내 수강 강좌 (X3): 본인의 수강 강좌만, 시작된 과제 수와 내가 제출한 과제 수, 최근 등록순, 수강 등록 후 이동.
 */
class StudentMyCoursesTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private UserRepository userRepository;

	private User instructor;

	@BeforeEach
	void setUpData() {
		instructor = userRepository.save(User.builder().email("teacher@test.com").password("encodedPassword")
				.name("김강사").role(Role.INSTRUCTOR).build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Course course(String name, String code) {
		return courseRepository.save(Course.builder().instructor(instructor).name(name).joinCode(code).build());
	}

	private void enroll(Course course, User who) {
		enrollmentRepository.save(Enrollment.builder().course(course).student(who).build());
	}

	private Assignment assignment(Course course, String title, LocalDateTime startAt) {
		return assignmentRepository.save(Assignment.builder().course(course).title(title).content("내용")
				.startAt(startAt).endAt(startAt.plusDays(7)).maxScore(100).build());
	}

	private void submit(Assignment assignment, User who) {
		submissionRepository.save(Submission.builder().assignment(assignment).student(who).content("답안")
				.submittedAt(LocalDateTime.now()).build());
	}

	private String encoded(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	@Test
	@DisplayName("GET /student/courses - 수강 강좌만 표시되고 강좌명 · 담당 강사 · 시작된 과제 수 · 내가 제출한 과제 수가 계산한 값과 일치함")
	void list_numbers() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		Course c1 = course("자바스프링", "AAAAA2");
		Course c2 = course("데이터베이스", "BBBBB3");
		Course notEnrolled = course("수강하지 않는 강좌", "CCCCC4");
		enroll(c1, student);
		enroll(c2, student);
		Assignment a1 = assignment(c1, "a1", now.minusDays(3));
		Assignment a2 = assignment(c1, "a2", now.minusDays(2));
		assignment(c1, "a3", now.minusDays(1));
		assignment(c1, "a4 예정", now.plusDays(1));
		assignment(c2, "b1", now.minusDays(1));
		assignment(notEnrolled, "c1", now.minusDays(1));
		submit(a1, student);
		submit(a2, student);

		// 자바스프링: 시작된 과제 3개(예정 제외) 중 2개 제출, 데이터베이스: 1개 중 0개 제출
		mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("student/courses"))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(containsString("데이터베이스")))
				.andExpect(content().string(not(containsString("수강하지 않는 강좌"))))
				.andExpect(content().string(containsString("김강사")))
				.andExpect(content().string(containsString("3개")))
				.andExpect(content().string(containsString("2 / 3")))
				.andExpect(content().string(containsString("1개")))
				.andExpect(content().string(containsString("0 / 1")));
	}

	@Test
	@DisplayName("GET /student/courses - 다른 학생의 수강 · 제출은 섞이지 않음")
	void list_notOthers() throws Exception {
		User other = createUser("other@test.com", Role.STUDENT);
		Course c1 = course("자바스프링", "AAAAA2");
		Course othersOnly = course("다른 학생만 듣는 강좌", "BBBBB3");
		enroll(c1, student);
		enroll(c1, other);
		enroll(othersOnly, other);
		Assignment a1 = assignment(c1, "a1", LocalDateTime.now().minusDays(1));
		submit(a1, other);

		mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(not(containsString("다른 학생만 듣는 강좌"))))
				.andExpect(content().string(containsString("0 / 1")))
				.andExpect(content().string(not(containsString("1 / 1"))));
	}

	@Test
	@DisplayName("GET /student/courses - 최근에 등록한 강좌가 위에 나옴")
	void list_newestFirst() throws Exception {
		Course first = course("먼저 등록한 강좌", "AAAAA2");
		Course second = course("나중에 등록한 강좌", "BBBBB3");
		enroll(first, student);
		enroll(second, student);

		String html = mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andReturn().getResponse().getContentAsString();

		assertThat(html.indexOf("나중에 등록한 강좌")).isPositive().isLessThan(html.indexOf("먼저 등록한 강좌"));
	}

	@Test
	@DisplayName("GET /student/courses - 강좌명을 누르면 그 강좌가 선택된 내 과제 목록으로 이동하는 링크가 걸림")
	void list_linkToFilteredAssignments() throws Exception {
		Course c1 = course("자바 & 스프링", "AAAAA2");
		enroll(c1, student);

		mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andExpect(content().string(containsString("/student/assignments?course=" + encoded("자바 & 스프링"))));
	}

	@Test
	@DisplayName("GET /student/courses - 수강 강좌가 없으면 안내 문구와 수강 등록 링크가 보임")
	void list_empty() throws Exception {
		mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 수강 등록한 강좌가 없습니다.")))
				.andExpect(content().string(containsString("/student/courses/join")));
	}

	@Test
	@DisplayName("GET /student/courses - 방금 등록한 안내 메시지가 표시됨")
	void list_flashMessage() throws Exception {
		mockMvc.perform(get("/student/courses").flashAttr("message", "자바스프링 강좌에 수강 등록되었습니다.")
						.with(user(principal(student))))
				.andExpect(content().string(containsString("자바스프링 강좌에 수강 등록되었습니다.")));
	}

	@Test
	@DisplayName("수강 등록에 성공하면 내 수강 강좌로 이동하고, 방금 등록한 강좌가 목록 맨 위에 보임")
	void join_redirectsToMyCourses() throws Exception {
		Course old = course("전에 등록한 강좌", "AAAAA2");
		enroll(old, student);
		course("새로 등록할 강좌", "BBBBB3");

		mockMvc.perform(post("/student/courses/join").param("joinCode", "bbbbb3")
						.with(user(principal(student))).with(csrf()))
				.andExpect(redirectedUrl("/student/courses"));

		String html = mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andReturn().getResponse().getContentAsString();
		assertThat(html.indexOf("새로 등록할 강좌")).isPositive().isLessThan(html.indexOf("전에 등록한 강좌"));
	}

	@Test
	@DisplayName("수강 등록 실패(틀린 참여코드, 이미 등록한 강좌)는 지금처럼 등록 화면에 머물며 에러를 보여 줌")
	void join_failureStaysOnJoinPage() throws Exception {
		Course c = course("자바스프링", "AAAAA2");
		enroll(c, student);

		mockMvc.perform(post("/student/courses/join").param("joinCode", "ZZZZZZ")
						.with(user(principal(student))).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("student/join"));
		mockMvc.perform(post("/student/courses/join").param("joinCode", "AAAAA2")
						.with(user(principal(student))).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("student/join"))
				.andExpect(content().string(containsString("이미 등록된 강좌입니다.")));
	}

	@Test
	@DisplayName("GET /student/courses - 강사는 403, 비로그인은 로그인 화면으로 이동")
	void list_security() throws Exception {
		mockMvc.perform(get("/student/courses").with(user(principal(instructor))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/student/courses"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@DisplayName("GET /student/courses - 강좌명 · 강사 이름의 HTML은 이스케이프되어 표시됨 (XSS)")
	void list_xssEscaped() throws Exception {
		User evilInstructor = userRepository.save(User.builder().email("evil@test.com").password("encodedPassword")
				.name("<script>alert(2)</script>").role(Role.INSTRUCTOR).build());
		Course evil = courseRepository.save(Course.builder().instructor(evilInstructor)
				.name("<img src=x onerror=alert(1)>").joinCode("DDDDD5").build());
		enroll(evil, student);

		mockMvc.perform(get("/student/courses").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
				.andExpect(content().string(not(containsString("<script>alert(2)</script>"))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
				.andExpect(content().string(containsString("&lt;script&gt;alert(2)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("사이드바에 '내 수강 강좌' 메뉴가 학생에게만 보이고, 내 과제 화면은 ?course= 로 강좌 필터를 미리 선택하는 스크립트를 가짐")
	void sidebarAndPreselect() throws Exception {
		mockMvc.perform(get("/student/assignments").with(user(principal(student))))
				.andExpect(content().string(containsString("/student/courses")))
				.andExpect(content().string(containsString("내 수강 강좌")))
				.andExpect(content().string(containsString("URLSearchParams")));
		mockMvc.perform(get("/instructor/courses").with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("내 수강 강좌"))));
	}
}
