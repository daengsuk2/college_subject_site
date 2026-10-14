package kr.ac.ync.midterm.home.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
 * 역할별 대시보드 화면 (/): 학생은 내 통계(S5), 강사는 내 강좌 요약. 역할 분리와 빈 데이터, XSS를 확인한다.
 */
class DashboardPageTest extends BaseController {

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

	private Assignment assignment(String title, LocalDateTime startAt, LocalDateTime endAt) {
		return assignmentRepository.save(Assignment.builder().course(course).title(title).content("내용")
				.startAt(startAt).endAt(endAt).maxScore(100).build());
	}

	private Submission submit(Assignment assignment, User owner, Integer score) {
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment)
				.student(owner).content("답안").submittedAt(LocalDateTime.now().minusMinutes(30)).build());
		if (score != null) {
			submission.grade(score, null, LocalDateTime.now());
		}
		return submission;
	}

	@Test
	@DisplayName("GET / 학생 - 수강 강좌 수 · 제출률 · 평균 점수 · 마감 임박이 계산한 값대로 표시됨")
	void student_numbers() throws Exception {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		Assignment done = assignment("끝낸 과제", now.minusDays(2), now.plusDays(3));
		assignment("곧 마감 과제", now.minusDays(1), now.plusHours(5));
		assignment("여유 있는 과제", now.minusDays(1), now.plusDays(3));
		submit(done, student, 80);
		// 시작된 과제 3개 중 1개 제출 → 33.3%, 평균 80.0점(배점 대비 80.0%), 마감 임박 1개

		mockMvc.perform(get("/").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("home"))
				.andExpect(model().attributeExists("student"))
				.andExpect(model().attributeDoesNotExist("instructor"))
				.andExpect(content().string(containsString("33.3%")))
				.andExpect(content().string(containsString("1 / 3개 제출")))
				.andExpect(content().string(containsString("80.0점")))
				.andExpect(content().string(containsString("배점 대비 80.0%")))
				.andExpect(content().string(containsString("곧 마감 과제")))
				.andExpect(content().string(not(containsString("여유 있는 과제"))))
				.andExpect(content().string(containsString("/student/assignments/")));
	}

	@Test
	@DisplayName("GET / 학생 - 수강 강좌가 없으면 안내 문구가 표시되고 오류 없이 '-'로 보임")
	void student_empty() throws Exception {
		mockMvc.perform(get("/").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 수강 등록한 강좌가 없습니다.")))
				.andExpect(content().string(containsString("마감이 임박한 과제가 없습니다.")))
				.andExpect(content().string(containsString("채점된 제출이 없습니다")))
				.andExpect(content().string(not(containsString("NaN"))))
				.andExpect(content().string(not(containsString("Infinity"))));
	}

	@Test
	@DisplayName("GET / 학생 - 다른 학생의 제출 · 점수와 강사용 내용은 보이지 않음")
	void student_noOthersData() throws Exception {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		User other = userRepository.save(User.builder().email("other@test.com").password("encodedPassword")
				.name("다른학생이름").studentNo("20260002").role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());
		LocalDateTime now = LocalDateTime.now();
		Assignment a = assignment("과제", now.minusDays(1), now.plusDays(3));
		submit(a, other, 99);

		mockMvc.perform(get("/").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("다른학생이름"))))
				.andExpect(content().string(not(containsString("99.0점"))))
				.andExpect(content().string(not(containsString("최근 제출"))))
				.andExpect(content().string(not(containsString("채점 대기"))));
	}

	@Test
	@DisplayName("GET / 강사 - 내 강좌 수 · 진행중 과제 수 · 채점 대기 수와 최근 제출 표가 표시됨")
	void instructor_numbers() throws Exception {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		Assignment a1 = assignment("진행중 과제", now.minusDays(1), now.plusDays(1));
		assignment("마감된 과제", now.minusDays(5), now.minusDays(1));
		Submission waiting = submit(a1, student, null);

		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("home"))
				.andExpect(model().attributeExists("instructor"))
				.andExpect(model().attributeDoesNotExist("student"))
				.andExpect(content().string(containsString("1개")))
				.andExpect(content().string(containsString("1건")))
				.andExpect(content().string(containsString("진행중 과제")))
				.andExpect(content().string(containsString("테스터")))
				.andExpect(content().string(containsString("/instructor/submissions/" + waiting.getId() + "/grade")));
	}

	@Test
	@DisplayName("GET / 강사 - 제출이 없으면 안내 문구가 표시되고 학생용 내용은 보이지 않음")
	void instructor_empty() throws Exception {
		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 제출된 과제가 없습니다.")))
				.andExpect(content().string(not(containsString("마감 임박"))))
				.andExpect(content().string(not(containsString("제출률"))));
	}

	@Test
	@DisplayName("GET / 강사 - 다른 강사의 강좌 제출은 최근 제출과 채점 대기에 나오지 않음 (B5)")
	void instructor_notOthersCourses() throws Exception {
		User otherInstructor = createUser("other-teacher@test.com", Role.INSTRUCTOR);
		Course otherCourse = courseRepository.save(
				Course.builder().instructor(otherInstructor).name("남의 강좌").joinCode("BBBBB3").build());
		enrollmentRepository.save(Enrollment.builder().course(otherCourse).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		Assignment othersAssignment = assignmentRepository.save(Assignment.builder().course(otherCourse)
				.title("남의 과제").content("내용").startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100)
				.build());
		submit(othersAssignment, student, null);

		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("남의 과제"))))
				.andExpect(content().string(not(containsString("남의 강좌"))))
				.andExpect(content().string(containsString("아직 제출된 과제가 없습니다.")));
	}

	@Test
	@DisplayName("GET / - 과제 제목 · 강좌명 · 학생 이름의 HTML은 이스케이프되어 표시됨 (XSS)")
	void xssEscaped() throws Exception {
		Course evil = courseRepository.save(Course.builder().instructor(instructor)
				.name("<img src=x onerror=alert(1)>").joinCode("CCCCC4").build());
		User evilStudent = userRepository.save(User.builder().email("evil@test.com").password("encodedPassword")
				.name("<script>alert(2)</script>").studentNo("20260009").role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(evil).student(evilStudent).build());
		LocalDateTime now = LocalDateTime.now();
		Assignment evilAssignment = assignmentRepository.save(Assignment.builder().course(evil)
				.title("<b onmouseover=alert(3)>제목</b>").content("내용").startAt(now.minusDays(1))
				.endAt(now.plusHours(2)).maxScore(100).build());
		submit(evilAssignment, evilStudent, null);

		// 강사 화면: 최근 제출 표 (학생 이름, 과제 제목, 강좌명)
		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(2)</script>"))))
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
				.andExpect(content().string(not(containsString("<b onmouseover=alert(3)>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(2)&lt;/script&gt;")));

		// 학생 화면: 마감 임박 표 (제출하지 않은 다른 학생 기준)
		User viewer = userRepository.save(User.builder().email("viewer@test.com").password("encodedPassword")
				.name("보는학생").studentNo("20260010").role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(evil).student(viewer).build());
		mockMvc.perform(get("/").with(user(principal(viewer))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<b onmouseover=alert(3)>"))))
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
				.andExpect(content().string(containsString("&lt;b onmouseover=alert(3)&gt;")));
	}

	@Test
	@DisplayName("GET / - 비로그인은 로그인 화면으로 이동")
	void anonymous() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection());
	}
}
