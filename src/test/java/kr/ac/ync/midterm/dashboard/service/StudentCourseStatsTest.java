package kr.ac.ync.midterm.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse;
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse.CourseStatRow;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 학생 대시보드의 강좌별 통계 (X35): 손으로 계산한 강좌별 값, 강좌별 합 = 전체 값, 다른 학생 · 강좌 미포함, 화면 표시.
 */
class StudentCourseStatsTest extends BaseController {

	@Autowired
	private DashboardService dashboardService;

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
	private Course java;
	private Course db;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
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

	private Assignment assignment(Course course, String title, int maxScore, LocalDateTime startAt,
			LocalDateTime endAt) {
		return assignmentRepository.save(Assignment.builder().course(course).title(title).content("내용")
				.startAt(startAt).endAt(endAt).maxScore(maxScore).build());
	}

	private Submission submit(Assignment assignment, User who, Integer score) {
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment).student(who)
				.content("답안").submittedAt(LocalDateTime.now().minusHours(1)).build());
		if (score != null) {
			submission.grade(score, null, LocalDateTime.now());
		}
		return submission;
	}

	private String encoded(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/**
	 * 자바스프링: 시작된 과제 3개(a1 90/100, a2 40/50 채점 완료, a3 마감 임박 미제출) + 예정 1개
	 * 데이터베이스: 시작된 과제 2개(b1 마감 임박 미제출, b2 제출했지만 채점 전, 종료까지 30시간이라 임박 아님)
	 */
	private void fixture() {
		java = course("자바스프링", "AAAAA2");
		db = course("데이터베이스", "BBBBB3");
		Course notEnrolled = course("수강하지 않는 강좌", "CCCCC4");
		User other = createUser("other@test.com", Role.STUDENT);
		enroll(java, student);
		enroll(db, student);
		enroll(java, other);
		LocalDateTime now = LocalDateTime.now();

		Assignment a1 = assignment(java, "a1", 100, now.minusDays(3), now.plusDays(3));
		Assignment a2 = assignment(java, "a2", 50, now.minusDays(2), now.plusDays(3));
		Assignment a3 = assignment(java, "a3 마감 임박", 100, now.minusDays(1), now.plusHours(5));
		assignment(java, "a4 예정", 100, now.plusDays(1), now.plusDays(5));
		assignment(db, "b1 마감 임박", 100, now.minusDays(1), now.plusHours(10));
		Assignment b2 = assignment(db, "b2", 100, now.minusDays(2), now.plusHours(30));
		Assignment outside = assignment(notEnrolled, "c1", 100, now.minusDays(1), now.plusHours(2));

		submit(a1, student, 90);
		submit(a2, student, 40);
		submit(b2, student, null);
		// 섞이면 안 되는 데이터: 다른 학생의 제출, 수강하지 않는 강좌의 제출
		submit(a3, other, 100);
		submit(outside, student, 100);
	}

	@Test
	@DisplayName("강좌별 제출률 · 제출 수 · 평균 점수 · 마감 임박 수가 손으로 계산한 값과 일치함")
	void perCourseNumbers() {
		fixture();

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(student.getId());

		CourseStatRow javaRow = dashboard.courses().stream().filter(r -> r.courseName().equals("자바스프링"))
				.findFirst().orElseThrow();
		assertThat(javaRow.startedAssignmentCount()).isEqualTo(3);
		assertThat(javaRow.submittedCount()).isEqualTo(2);
		assertThat(javaRow.submissionRate()).isCloseTo(200.0 / 3, within(0.0001));
		assertThat(javaRow.gradedCount()).isEqualTo(2);
		assertThat(javaRow.averageScore()).isEqualTo(65.0);
		assertThat(javaRow.averageScoreRate()).isCloseTo(130 * 100.0 / 150, within(0.0001));
		assertThat(javaRow.dueSoonCount()).isEqualTo(1);

		CourseStatRow dbRow = dashboard.courses().stream().filter(r -> r.courseName().equals("데이터베이스"))
				.findFirst().orElseThrow();
		assertThat(dbRow.startedAssignmentCount()).isEqualTo(2);
		assertThat(dbRow.submittedCount()).isEqualTo(1);
		assertThat(dbRow.submissionRate()).isEqualTo(50.0);
		assertThat(dbRow.gradedCount()).isZero();
		assertThat(dbRow.averageScore()).isNull();
		assertThat(dbRow.averageScoreRate()).isNull();
		assertThat(dbRow.dueSoonCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("강좌별 값을 합치면 대시보드 카드의 전체 값과 일치함 (시작된 과제 · 제출 · 채점된 수 · 마감 임박)")
	void rowsSumToTotals() {
		fixture();

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(student.getId());

		assertThat(dashboard.courses().stream().mapToLong(CourseStatRow::startedAssignmentCount).sum())
				.isEqualTo(dashboard.startedAssignmentCount()).isEqualTo(5);
		assertThat(dashboard.courses().stream().mapToLong(CourseStatRow::submittedCount).sum())
				.isEqualTo(dashboard.submittedCount()).isEqualTo(3);
		assertThat(dashboard.courses().stream().mapToLong(CourseStatRow::gradedCount).sum())
				.isEqualTo(dashboard.gradedCount()).isEqualTo(2);
		assertThat(dashboard.courses().stream().mapToLong(CourseStatRow::dueSoonCount).sum())
				.isEqualTo(dashboard.dueSoonCount()).isEqualTo(2);
		assertThat(dashboard.courses()).hasSize((int) dashboard.courseCount());
	}

	@Test
	@DisplayName("수강하지 않는 강좌와 다른 학생의 제출은 강좌별 표에 섞이지 않음")
	void notMixed() {
		fixture();

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(student.getId());

		assertThat(dashboard.courses()).extracting(CourseStatRow::courseName)
				.containsExactlyInAnyOrder("자바스프링", "데이터베이스")
				.doesNotContain("수강하지 않는 강좌");
		// 다른 학생이 a3에 제출했어도 내 자바스프링 제출은 2건
		assertThat(dashboard.courses().stream().filter(r -> r.courseName().equals("자바스프링")).findFirst()
				.orElseThrow().submittedCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("재채점하면 해당 강좌의 평균 점수만 바뀜")
	void regradeChangesOnlyThatCourse() {
		fixture();
		Submission graded = submissionRepository.findAll().stream()
				.filter(s -> s.getStudent().getId().equals(student.getId()) && s.getScore() != null && s.getScore() == 90)
				.findFirst().orElseThrow();

		graded.grade(100, "재채점", LocalDateTime.now());

		CourseStatRow javaRow = dashboardService.findStudentDashboard(student.getId()).courses().stream()
				.filter(r -> r.courseName().equals("자바스프링")).findFirst().orElseThrow();
		assertThat(javaRow.averageScore()).isEqualTo(70.0);
	}

	@Test
	@DisplayName("시작된 과제가 없는 강좌는 제출률 · 평균이 null이고(0으로 나누지 않음), 수강 강좌가 없으면 빈 목록")
	void emptyCases() {
		Course upcomingOnly = course("예정 과제만 있는 강좌", "AAAAA2");
		enroll(upcomingOnly, student);
		assignment(upcomingOnly, "예정", 100, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3));

		CourseStatRow row = dashboardService.findStudentDashboard(student.getId()).courses().getFirst();

		assertThat(row.startedAssignmentCount()).isZero();
		assertThat(row.submissionRate()).isNull();
		assertThat(row.averageScore()).isNull();
		assertThat(row.dueSoonCount()).isZero();

		User fresh = createUser("fresh@test.com", Role.STUDENT);
		assertThat(dashboardService.findStudentDashboard(fresh.getId()).courses()).isEmpty();
	}

	@Test
	@DisplayName("강좌 행은 최근에 수강 등록한 강좌가 먼저 나옴")
	void newestEnrollmentFirst() {
		Course first = course("먼저 등록", "AAAAA2");
		Course second = course("나중에 등록", "BBBBB3");
		enroll(first, student);
		enroll(second, student);

		assertThat(dashboardService.findStudentDashboard(student.getId()).courses())
				.extracting(CourseStatRow::courseName).containsExactly("나중에 등록", "먼저 등록");
	}

	// ---------- 화면 ----------

	@Test
	@DisplayName("학생 대시보드 - 강좌별 통계 표가 표시되고 강좌 링크가 내 과제 목록의 강좌 필터로 연결되며 표 검색 · 정렬이 켜짐")
	void page_table() throws Exception {
		fixture();

		mockMvc.perform(get("/").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("강좌별 통계")))
				.andExpect(content().string(containsString("id=\"courseStatTable\"")))
				.andExpect(content().string(containsString("js-datatable")))
				.andExpect(content().string(containsString("/js/datatables-init.js")))
				.andExpect(content().string(containsString("/student/assignments?course=" + encoded("자바스프링"))))
				.andExpect(content().string(containsString("/student/assignments?course=" + encoded("데이터베이스"))))
				// 자바스프링: 66.7%, 2 / 3, 평균 65.0점 (배점 대비 86.7%), 마감 임박 1개
				.andExpect(content().string(containsString("66.7%")))
				.andExpect(content().string(containsString("2 / 3")))
				.andExpect(content().string(containsString("65.0점 (배점 대비 86.7%)")))
				// 데이터베이스: 50.0%, 1 / 2, 채점된 제출이 없어 평균 "-"
				.andExpect(content().string(containsString("50.0%")))
				.andExpect(content().string(containsString("1 / 2")))
				.andExpect(content().string(not(containsString("수강하지 않는 강좌"))));
	}

	@Test
	@DisplayName("학생 대시보드 - 수강 강좌가 없으면 강좌별 표와 표 스크립트 없이 안내 문구만 보임")
	void page_noCourses() throws Exception {
		User fresh = createUser("fresh@test.com", Role.STUDENT);

		mockMvc.perform(get("/").with(user(principal(fresh))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 수강 등록한 강좌가 없습니다.")))
				.andExpect(content().string(not(containsString("courseStatTable"))))
				.andExpect(content().string(not(containsString("/js/datatables-init.js"))));
	}

	@Test
	@DisplayName("강사 대시보드에는 강좌별 학생 통계 표가 나오지 않음")
	void page_notForInstructor() throws Exception {
		fixture();

		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("courseStatTable"))))
				.andExpect(content().string(not(containsString("강좌별 통계"))));
	}

	@Test
	@DisplayName("강좌명의 HTML은 강좌별 표에서 이스케이프되어 표시됨 (XSS)")
	void page_escaped() throws Exception {
		Course evil = course("<img src=x onerror=alert(1)>", "DDDDD5");
		enroll(evil, student);

		mockMvc.perform(get("/").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")));
	}
}
