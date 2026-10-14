package kr.ac.ync.midterm.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.dashboard.dto.response.InstructorDashboardResponse;
import kr.ac.ync.midterm.dashboard.dto.response.InstructorDashboardResponse.RecentSubmissionRow;
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse;
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse.DueSoonRow;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 학생 · 강사 대시보드 (S5): 손으로 계산한 숫자와 일치하는지, 마감 임박 24시간 경계, 본인 데이터만 집계되는지 확인한다.
 */
@SpringBootTest
@Transactional
class DashboardServiceImplTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
	private static final ZoneId ZONE = ZoneId.systemDefault();

	@MockitoBean
	private Clock clock;

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private User instructor;

	@BeforeEach
	void setUp() {
		when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
		when(clock.getZone()).thenReturn(ZONE);
		instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null, "강사");
	}

	private User savedUser(String email, Role role, String studentNo, String name) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name(name)
				.studentNo(studentNo).role(role).build());
	}

	private Course course(User owner, String name, String code) {
		return courseRepository.save(Course.builder().instructor(owner).name(name).joinCode(code).build());
	}

	private void enroll(Course course, User student) {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
	}

	private Assignment assignment(Course course, String title, int maxScore, LocalDateTime startAt,
			LocalDateTime endAt) {
		return assignmentRepository.save(Assignment.builder().course(course).title(title).content("내용")
				.startAt(startAt).endAt(endAt).maxScore(maxScore).build());
	}

	private Submission submit(Assignment assignment, User student, LocalDateTime at, Integer gradedScore) {
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment)
				.student(student).content("답안").submittedAt(at).build());
		if (gradedScore != null) {
			submission.grade(gradedScore, null, at.plusMinutes(5));
		}
		return submission;
	}

	// ---------- 학생 ----------

	/**
	 * 학생 S: 강좌 2개 수강. 시작된 과제 6개, 제출 3건(채점 2건 90/100, 40/50), 마감 임박 3개.
	 */
	private User studentFixture() {
		User s = savedUser("s@test.com", Role.STUDENT, "20260001", "학생");
		User t = savedUser("t@test.com", Role.STUDENT, "20260002", "다른학생");
		Course c1 = course(instructor, "자바스프링", "AAAAA2");
		Course c2 = course(instructor, "데이터베이스", "BBBBB3");
		Course c3 = course(instructor, "수강하지 않는 강좌", "CCCCC4");
		enroll(c1, s);
		enroll(c1, t);
		enroll(c2, s);

		Assignment a1 = assignment(c1, "a1 진행중", 100, NOW.minusDays(2), NOW.plusDays(3));
		Assignment a2 = assignment(c1, "a2 10시간 남음(제출함)", 100, NOW.minusDays(1), NOW.plusHours(10));
		assignment(c1, "a3 정확히 24시간 남음", 100, NOW.minusDays(1), NOW.plusHours(24));
		assignment(c1, "a4 24시간 1초 남음", 100, NOW.minusDays(1), NOW.plusHours(24).plusSeconds(1));
		assignment(c1, "a5 예정", 100, NOW.plusDays(1), NOW.plusDays(5));
		Assignment a6 = assignment(c1, "a6 마감", 50, NOW.minusDays(10), NOW.minusDays(1));
		assignment(c1, "a7 종료 정각", 100, NOW.minusDays(1), NOW);
		assignment(c2, "b1 5시간 남음", 100, NOW.minusDays(1), NOW.plusHours(5));
		Assignment otherCourse = assignment(c3, "c1 수강하지 않음", 100, NOW.minusDays(1), NOW.plusHours(1));

		submit(a1, s, NOW.minusHours(30), 90);
		submit(a2, s, NOW.minusHours(2), null);
		submit(a6, s, NOW.minusDays(5), 40);
		// 섞이면 안 되는 데이터: 다른 학생의 제출, 수강하지 않는 강좌의 제출
		submit(a1, t, NOW.minusHours(1), 10);
		submit(otherCourse, s, NOW.minusHours(1), 100);
		return s;
	}

	@Test
	@DisplayName("학생 - 수강 강좌 수와 제출률 (제출한 과제 수 / 시작된 과제 수)이 계산한 값과 일치")
	void student_counts() {
		User s = studentFixture();

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(s.getId());

		assertThat(dashboard.courseCount()).isEqualTo(2);
		// 시작된 과제: a1 a2 a3 a4 a6 a7 b1 = 7 (a5 예정 제외, 수강하지 않는 강좌 제외)
		assertThat(dashboard.startedAssignmentCount()).isEqualTo(7);
		assertThat(dashboard.submittedCount()).isEqualTo(3);
		assertThat(dashboard.submissionRate()).isCloseTo(3 * 100.0 / 7, within(0.0001));
	}

	@Test
	@DisplayName("학생 - 평균 점수는 채점된 내 제출의 점수 평균이고 배점 대비 비율도 함께 계산됨")
	void student_average() {
		User s = studentFixture();

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(s.getId());

		assertThat(dashboard.gradedCount()).isEqualTo(2);
		// (90 + 40) / 2 = 65.0점, 배점 대비 (90 + 40) / (100 + 50) = 86.67%
		assertThat(dashboard.averageScore()).isEqualTo(65.0);
		assertThat(dashboard.averageScoreRate()).isCloseTo(130 * 100.0 / 150, within(0.0001));
	}

	@Test
	@DisplayName("학생 - 마감 임박은 진행중이고 24시간 이내이며 아직 제출하지 않은 과제만, 종료가 이른 순")
	void student_dueSoon() {
		User s = studentFixture();

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(s.getId());

		// a7(종료 정각, 0분) -> b1(5시간) -> a3(정확히 24시간). a2는 제출함, a4는 24시간 1초, a5 예정, a6 마감, c1 미수강은 제외
		assertThat(dashboard.dueSoon()).extracting(DueSoonRow::title)
				.containsExactly("a7 종료 정각", "b1 5시간 남음", "a3 정확히 24시간 남음");
		assertThat(dashboard.dueSoonCount()).isEqualTo(3);
		assertThat(dashboard.dueSoon()).extracting(DueSoonRow::remainingMinutes).containsExactly(0L, 300L, 1440L);
		assertThat(dashboard.dueSoon()).extracting(DueSoonRow::remainingText)
				.containsExactly("0분", "5시간", "24시간");
		assertThat(dashboard.dueSoon().getFirst().courseName()).isEqualTo("자바스프링");
	}

	@Test
	@DisplayName("학생 - 남은 시간 표시: 분만, 시간만, 시간과 분")
	void student_remainingText() {
		assertThat(new DueSoonRow(1L, "t", "c", NOW, 45).remainingText()).isEqualTo("45분");
		assertThat(new DueSoonRow(1L, "t", "c", NOW, 120).remainingText()).isEqualTo("2시간");
		assertThat(new DueSoonRow(1L, "t", "c", NOW, 125).remainingText()).isEqualTo("2시간 5분");
	}

	@Test
	@DisplayName("학생 - 제출하면 마감 임박 목록에서 빠지고, 채점 전 제출은 평균에서 제외됨")
	void student_submitRemovesFromDueSoon() {
		User s = savedUser("s@test.com", Role.STUDENT, "20260001", "학생");
		Course c = course(instructor, "자바스프링", "AAAAA2");
		enroll(c, s);
		Assignment a = assignment(c, "임박 과제", 100, NOW.minusDays(1), NOW.plusHours(3));
		assertThat(dashboardService.findStudentDashboard(s.getId()).dueSoon()).hasSize(1);

		submit(a, s, NOW, null);

		StudentDashboardResponse after = dashboardService.findStudentDashboard(s.getId());
		assertThat(after.dueSoon()).isEmpty();
		assertThat(after.submittedCount()).isEqualTo(1);
		assertThat(after.gradedCount()).isZero();
		assertThat(after.averageScore()).isNull();
		assertThat(after.averageScoreRate()).isNull();
	}

	@Test
	@DisplayName("학생 - 재채점하면 평균 점수도 바뀜")
	void student_regradeChangesAverage() {
		User s = savedUser("s@test.com", Role.STUDENT, "20260001", "학생");
		Course c = course(instructor, "자바스프링", "AAAAA2");
		enroll(c, s);
		Assignment a = assignment(c, "과제", 100, NOW.minusDays(1), NOW.plusDays(3));
		Submission submission = submit(a, s, NOW.minusHours(1), 60);
		assertThat(dashboardService.findStudentDashboard(s.getId()).averageScore()).isEqualTo(60.0);

		submission.grade(95, "재채점", NOW);

		assertThat(dashboardService.findStudentDashboard(s.getId()).averageScore()).isEqualTo(95.0);
	}

	@Test
	@DisplayName("학생 - 수강 강좌가 없으면 모두 0이고 제출률 · 평균은 null (0으로 나누지 않음)")
	void student_empty() {
		User s = savedUser("s@test.com", Role.STUDENT, "20260001", "학생");

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(s.getId());

		assertThat(dashboard.courseCount()).isZero();
		assertThat(dashboard.startedAssignmentCount()).isZero();
		assertThat(dashboard.submittedCount()).isZero();
		assertThat(dashboard.submissionRate()).isNull();
		assertThat(dashboard.averageScore()).isNull();
		assertThat(dashboard.dueSoon()).isEmpty();
	}

	@Test
	@DisplayName("학생 - 시작된 과제가 없으면(예정 과제만 있으면) 제출률은 null")
	void student_onlyUpcoming() {
		User s = savedUser("s@test.com", Role.STUDENT, "20260001", "학생");
		Course c = course(instructor, "자바스프링", "AAAAA2");
		enroll(c, s);
		assignment(c, "예정 과제", 100, NOW.plusDays(1), NOW.plusDays(5));

		StudentDashboardResponse dashboard = dashboardService.findStudentDashboard(s.getId());

		assertThat(dashboard.courseCount()).isEqualTo(1);
		assertThat(dashboard.startedAssignmentCount()).isZero();
		assertThat(dashboard.submissionRate()).isNull();
		assertThat(dashboard.dueSoon()).isEmpty();
	}

	@Test
	@DisplayName("학생 - 시작 정각인 과제는 시작된 것으로 계산됨 (경계)")
	void student_startBoundary() {
		User s = savedUser("s@test.com", Role.STUDENT, "20260001", "학생");
		Course c = course(instructor, "자바스프링", "AAAAA2");
		enroll(c, s);
		assignment(c, "정각 시작", 100, NOW, NOW.plusDays(1));
		assignment(c, "1초 뒤 시작", 100, NOW.plusSeconds(1), NOW.plusDays(1));

		assertThat(dashboardService.findStudentDashboard(s.getId()).startedAssignmentCount()).isEqualTo(1);
	}

	// ---------- 강사 ----------

	@Test
	@DisplayName("강사 - 내 강좌 수, 진행중 과제 수, 채점 대기 수가 본인 강좌만 집계됨")
	void instructor_counts() {
		User other = savedUser("other-teacher@test.com", Role.INSTRUCTOR, null, "다른강사");
		Course k1 = course(instructor, "자바스프링", "AAAAA2");
		Course k2 = course(instructor, "데이터베이스", "BBBBB3");
		Course k3 = course(other, "다른 강사 강좌", "CCCCC4");
		User s1 = savedUser("s1@test.com", Role.STUDENT, "20260001", "학생1");
		User s2 = savedUser("s2@test.com", Role.STUDENT, "20260002", "학생2");
		User s3 = savedUser("s3@test.com", Role.STUDENT, "20260003", "학생3");
		enroll(k1, s1);
		enroll(k1, s2);
		enroll(k2, s3);
		enroll(k3, s1);

		Assignment p1 = assignment(k1, "p1 진행중", 100, NOW.minusDays(1), NOW.plusDays(1));
		assignment(k1, "p2 마감", 100, NOW.minusDays(5), NOW.minusDays(1));
		assignment(k1, "p3 예정", 100, NOW.plusDays(1), NOW.plusDays(2));
		Assignment p4 = assignment(k2, "p4 진행중", 100, NOW.minusDays(1), NOW.plusDays(1));
		assignment(k2, "p5 시작 정각", 100, NOW, NOW.plusDays(1));
		Assignment p6 = assignment(k2, "p6 종료 정각", 100, NOW.minusDays(1), NOW);
		assignment(k2, "p7 종료 1초 전 마감", 100, NOW.minusDays(2), NOW.minusSeconds(1));
		Assignment q1 = assignment(k3, "q1 다른 강사", 100, NOW.minusDays(1), NOW.plusDays(1));

		submit(p1, s1, NOW.minusHours(3), null);
		submit(p1, s2, NOW.minusHours(2), null);
		submit(p4, s3, NOW.minusHours(1), null);
		submit(p6, s3, NOW, null).grade(80, null, NOW); // 이미 채점된 제출은 채점 대기에서 빠진다
		submit(q1, s1, NOW.minusHours(1), null);

		InstructorDashboardResponse dashboard = dashboardService.findInstructorDashboard(instructor.getId());

		assertThat(dashboard.courseCount()).isEqualTo(2);
		// 진행중: p1, p4, p5(시작 정각), p6(종료 정각) = 4 (p2 마감, p3 예정, p7 마감, 다른 강사 q1 제외)
		assertThat(dashboard.inProgressAssignmentCount()).isEqualTo(4);
		// 채점 대기: p1(s1) p1(s2) p4(s3) = 3 (채점 완료 1건 제외, 다른 강사 강좌 제출 제외)
		assertThat(dashboard.pendingGradingCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("강사 - 최근 제출은 최근 순으로 10건까지, 본인 강좌의 수강생 제출만")
	void instructor_recent() {
		User other = savedUser("other-teacher@test.com", Role.INSTRUCTOR, null, "다른강사");
		Course k1 = course(instructor, "자바스프링", "AAAAA2");
		Course k3 = course(other, "다른 강사 강좌", "CCCCC4");
		User outsider = savedUser("out@test.com", Role.STUDENT, "20269999", "외부인");
		Assignment p1 = assignment(k1, "p1", 100, NOW.minusDays(1), NOW.plusDays(1));
		Assignment q1 = assignment(k3, "q1", 100, NOW.minusDays(1), NOW.plusDays(1));
		for (int i = 0; i < 12; i++) {
			User s = savedUser("s" + i + "@test.com", Role.STUDENT, "2026010" + i, "학생" + i);
			enroll(k1, s);
			submit(p1, s, NOW.minusHours(20).plusMinutes(i), null);
		}
		User otherStudent = savedUser("os@test.com", Role.STUDENT, "20260555", "남의학생");
		enroll(k3, otherStudent);
		submit(q1, otherStudent, NOW.minusMinutes(1), null);
		submit(p1, outsider, NOW.minusMinutes(2), null);

		InstructorDashboardResponse dashboard = dashboardService.findInstructorDashboard(instructor.getId());

		assertThat(dashboard.recentSubmissions()).hasSize(10);
		assertThat(dashboard.recentSubmissions()).extracting(RecentSubmissionRow::studentName)
				.containsExactly("학생11", "학생10", "학생9", "학생8", "학생7", "학생6", "학생5", "학생4", "학생3",
						"학생2");
		assertThat(dashboard.recentSubmissions()).extracting(RecentSubmissionRow::assignmentTitle)
				.containsOnly("p1");
	}

	@Test
	@DisplayName("강사 - 강좌나 제출이 없으면 모두 0이고 최근 제출은 빈 목록")
	void instructor_empty() {
		InstructorDashboardResponse dashboard = dashboardService.findInstructorDashboard(instructor.getId());

		assertThat(dashboard.courseCount()).isZero();
		assertThat(dashboard.inProgressAssignmentCount()).isZero();
		assertThat(dashboard.pendingGradingCount()).isZero();
		assertThat(dashboard.recentSubmissions()).isEmpty();
	}

	@Test
	@DisplayName("강사 - 채점하면 채점 대기 수가 줄고 최근 제출의 상태가 채점 완료로 보임")
	void instructor_gradingUpdatesPending() {
		Course k1 = course(instructor, "자바스프링", "AAAAA2");
		User s1 = savedUser("s1@test.com", Role.STUDENT, "20260001", "학생1");
		enroll(k1, s1);
		Assignment p1 = assignment(k1, "p1", 100, NOW.minusDays(1), NOW.plusDays(1));
		Submission submission = submit(p1, s1, NOW.minusHours(1), null);
		assertThat(dashboardService.findInstructorDashboard(instructor.getId()).pendingGradingCount()).isEqualTo(1);

		submission.grade(70, null, NOW);

		InstructorDashboardResponse after = dashboardService.findInstructorDashboard(instructor.getId());
		assertThat(after.pendingGradingCount()).isZero();
		assertThat(after.recentSubmissions().getFirst().isGraded()).isTrue();
	}
}
