package kr.ac.ync.midterm.stats.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
 * 강사의 통계 대시보드 (I6): 화면에 표시되는 숫자, 강좌 선택, 권한(B5), 빈 데이터, XSS.
 */
class InstructorStatsControllerTest extends BaseController {

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
	private Course course;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private User enrolled(String email, String studentNo, String name) {
		User s = userRepository.save(User.builder().email(email).password("encodedPassword").name(name)
				.studentNo(studentNo).role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(s).build());
		return s;
	}

	private Assignment startedAssignment(String title, int maxScore, int startedDaysAgo) {
		LocalDateTime now = LocalDateTime.now();
		return assignmentRepository.save(Assignment.builder().course(course).title(title).content("내용")
				.startAt(now.minusDays(startedDaysAgo)).endAt(now.plusDays(7)).maxScore(maxScore).build());
	}

	private Submission submit(Assignment assignment, User student, Integer score) {
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment)
				.student(student).content("답안").submittedAt(LocalDateTime.now().minusHours(1)).build());
		if (score != null) {
			submission.grade(score, null, LocalDateTime.now());
		}
		return submission;
	}

	@Test
	@DisplayName("GET /instructor/stats - 요약 숫자, 과제별 표, 미제출자 표가 계산한 값대로 표시됨")
	void shownNumbers() throws Exception {
		User a = enrolled("a@test.com", "20260001", "김제출");
		User b = enrolled("b@test.com", "20260002", "이미제출");
		enrolled("c@test.com", "20260003", "박미제출");
		Assignment a1 = startedAssignment("1주차 과제", 100, 2);
		Assignment a2 = startedAssignment("2주차 과제", 50, 1);
		submit(a1, a, 90);
		submit(a1, b, 70);
		submit(a2, a, null);
		// 수강생 3명 x 과제 2개 = 6, 제출 3건 → 50.0%

		mockMvc.perform(get("/instructor/stats").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/stats"))
				.andExpect(content().string(containsString("3명")))
				.andExpect(content().string(containsString("2개")))
				.andExpect(content().string(containsString("50.0%")))
				.andExpect(content().string(containsString("3 / 6건")))
				// 1주차: 제출 2/3 = 66.7%, 평균 (90+70)/2 = 80.0 / 100
				.andExpect(content().string(containsString("66.7%")))
				.andExpect(content().string(containsString("80.0 / 100")))
				// 2주차: 제출 1/3 = 33.3%, 채점 전이라 평균 없음, 채점 대기 1건
				.andExpect(content().string(containsString("33.3%")))
				.andExpect(content().string(containsString("1건")))
				// 미제출: 박미제출 2개, 이미제출 1개, 김제출 1개
				.andExpect(content().string(containsString("박미제출")))
				.andExpect(content().string(containsString("2개")));
	}

	@Test
	@DisplayName("GET /instructor/stats - 차트 데이터(제출 · 미제출 수, 제목, 평균, 배점)가 화면에 전달됨")
	void chartData() throws Exception {
		User a = enrolled("a@test.com", "20260001", "김제출");
		enrolled("b@test.com", "20260002", "이미제출");
		Assignment a1 = startedAssignment("Week1 HW", 100, 1);
		submit(a1, a, 90);

		// 한글은 자바스크립트 유니코드 이스케이프로 내려가므로, 값 비교는 영문 제목으로 한다
		mockMvc.perform(get("/instructor/stats").with(user(principal(instructor))))
				.andExpect(content().string(containsString("/vendor/chart.js/Chart.min.js")))
				.andExpect(content().string(containsString("var submitted = 1;")))
				.andExpect(content().string(containsString("var notSubmitted = 1;")))
				.andExpect(content().string(containsString("[\"Week1 HW\"]")))
				.andExpect(content().string(containsString("[90.0]")))
				.andExpect(content().string(containsString("[100]")));
	}

	@Test
	@DisplayName("GET /instructor/stats?courseId= - 선택한 강좌의 통계가 표시되고 강좌 선택 목록에 내 강좌가 모두 나옴")
	void courseSelection() throws Exception {
		Course other = courseRepository.save(
				Course.builder().instructor(instructor).name("데이터베이스").joinCode("BBBBB3").build());
		LocalDateTime now = LocalDateTime.now();
		assignmentRepository.save(Assignment.builder().course(other).title("DB 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
		startedAssignment("자바 과제", 100, 1);

		mockMvc.perform(get("/instructor/stats").param("courseId", other.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("DB 과제")))
				.andExpect(content().string(not(containsString("자바 과제"))))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(containsString("데이터베이스")));
	}

	@Test
	@DisplayName("GET /instructor/stats - 수강생이나 시작된 과제가 없으면 오류 없이 안내 문구가 표시됨")
	void emptyData() throws Exception {
		mockMvc.perform(get("/instructor/stats").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("제출률을 계산할 수 없습니다.")))
				.andExpect(content().string(containsString("아직 시작된 과제가 없습니다.")))
				.andExpect(content().string(not(containsString("NaN"))))
				.andExpect(content().string(not(containsString("Infinity"))))
				.andExpect(content().string(not(containsString("Chart.min.js"))));

		enrolled("a@test.com", "20260001", "김제출");
		startedAssignment("1주차", 100, 1);
		mockMvc.perform(get("/instructor/stats").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("0.0%")));
	}

	@Test
	@DisplayName("GET /instructor/stats - 강좌가 하나도 없는 강사에게는 안내 문구가 표시됨")
	void noCourses() throws Exception {
		User fresh = createUser("fresh-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/instructor/stats").with(user(principal(fresh))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 개설한 강좌가 없습니다.")));
	}

	@Test
	@DisplayName("GET /instructor/stats - 다른 강사의 강좌는 403, 없는 강좌는 404, 숫자가 아닌 값은 400 (B5)")
	void accessRules() throws Exception {
		User other = createUser("other-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString())
						.with(user(principal(other))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"));
		mockMvc.perform(get("/instructor/stats").param("courseId", "999999")
						.with(user(principal(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/instructor/stats").param("courseId", "abc")
						.with(user(principal(instructor))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("GET /instructor/stats - 학생은 403, 비로그인은 로그인 화면으로 이동")
	void studentAndAnonymous() throws Exception {
		mockMvc.perform(get("/instructor/stats").with(user(principal(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/instructor/stats"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@DisplayName("GET /instructor/stats - 강좌명 · 과제 제목 · 학생 이름의 HTML과 스크립트 종료 태그가 이스케이프됨 (XSS)")
	void xssEscaped() throws Exception {
		Course evil = courseRepository.save(Course.builder().instructor(instructor)
				.name("<img src=x onerror=alert(1)>").joinCode("CCCCC4").build());
		User s = userRepository.save(User.builder().email("evil@test.com").password("encodedPassword")
				.name("<script>alert(2)</script>").studentNo("20260009").role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(evil).student(s).build());
		LocalDateTime now = LocalDateTime.now();
		assignmentRepository.save(Assignment.builder().course(evil).title("</script><script>alert(3)</script>")
				.content("내용").startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());

		mockMvc.perform(get("/instructor/stats").param("courseId", evil.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
				.andExpect(content().string(not(containsString("<script>alert(2)</script>"))))
				.andExpect(content().string(not(containsString("</script><script>alert(3)"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(2)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("사이드바의 '통계 대시보드' 메뉴가 이 화면 주소로 연결됨")
	void sidebarLink() throws Exception {
		mockMvc.perform(get("/instructor/courses").with(user(principal(instructor))))
				.andExpect(content().string(containsString("/instructor/stats")));
		mockMvc.perform(get("/instructor/stats").with(user(principal(instructor))))
				.andExpect(status().isOk());
	}
}
