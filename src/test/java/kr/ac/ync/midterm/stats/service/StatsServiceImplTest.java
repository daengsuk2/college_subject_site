package kr.ac.ync.midterm.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import kr.ac.ync.midterm.course.exception.CourseNotFoundException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.AssignmentStatRow;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.MissingStudentRow;
import kr.ac.ync.midterm.stats.dto.response.StatsPageResponse;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 강사 통계 (I6): 손으로 계산한 숫자와 정확히 일치하는지 확인한다.
 */
@SpringBootTest
@Transactional
class StatsServiceImplTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
	private static final ZoneId ZONE = ZoneId.systemDefault();

	@MockitoBean
	private Clock clock;

	@Autowired
	private StatsService statsService;

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
	private Course course;

	@BeforeEach
	void setUp() {
		when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
		when(clock.getZone()).thenReturn(ZONE);
		instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null, "강사");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
	}

	private User savedUser(String email, Role role, String studentNo, String name) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name(name)
				.studentNo(studentNo).role(role).build());
	}

	private User enrolled(Course target, String email, String studentNo, String name) {
		User student = savedUser(email, Role.STUDENT, studentNo, name);
		enrollmentRepository.save(Enrollment.builder().course(target).student(student).build());
		return student;
	}

	private Assignment assignment(Course target, String title, int maxScore, LocalDateTime startAt) {
		return assignmentRepository.save(Assignment.builder().course(target).title(title).content("내용")
				.startAt(startAt).endAt(startAt.plusDays(7)).maxScore(maxScore).build());
	}

	private Submission submit(Assignment assignment, User student, Integer gradedScore) {
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment)
				.student(student).content("답안").submittedAt(NOW.minusHours(1)).build());
		if (gradedScore != null) {
			submission.grade(gradedScore, null, NOW.minusMinutes(30));
		}
		return submission;
	}

	private CourseStatsResponse stats() {
		return statsService.findStats(instructor.getId(), course.getId()).stats();
	}

	/** 수강생 3명, 시작된 과제 2개(+예정 1개), 제출 4건 */
	private void fixture() {
		User a = enrolled(course, "a@test.com", "20260001", "가나다");
		User b = enrolled(course, "b@test.com", "20260002", "나다라");
		User c = enrolled(course, "c@test.com", "20260003", "다라마");
		Assignment a1 = assignment(course, "1주차", 100, NOW.minusDays(2));
		Assignment a2 = assignment(course, "2주차", 50, NOW.minusDays(1));
		assignment(course, "3주차(예정)", 100, NOW.plusDays(1));
		submit(a1, a, 90);
		submit(a1, b, 70);
		submit(a1, c, null);
		submit(a2, a, 40);
	}

	@Test
	@DisplayName("findStats - 제출률 = 제출 건수 / (수강생 수 x 시작된 과제 수), 손으로 계산한 값과 일치")
	void overallRate() {
		fixture();

		CourseStatsResponse stats = stats();

		assertThat(stats.enrolledCount()).isEqualTo(3);
		assertThat(stats.startedAssignmentCount()).isEqualTo(2);
		assertThat(stats.possibleCount()).isEqualTo(6);
		assertThat(stats.submittedCount()).isEqualTo(4);
		assertThat(stats.notSubmittedCount()).isEqualTo(2);
		assertThat(stats.submissionRate()).isCloseTo(66.6667, org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	@DisplayName("findStats - 과제별 제출 수 · 제출률 · 채점 대기 · 평균 점수가 정확함 (평균은 채점된 제출만)")
	void perAssignmentRows() {
		fixture();

		CourseStatsResponse stats = stats();

		assertThat(stats.assignments()).extracting(AssignmentStatRow::title).containsExactly("1주차", "2주차");
		AssignmentStatRow first = stats.assignments().get(0);
		assertThat(first.submittedCount()).isEqualTo(3);
		assertThat(first.enrolledCount()).isEqualTo(3);
		assertThat(first.submissionRate()).isEqualTo(100.0);
		assertThat(first.gradedCount()).isEqualTo(2);
		assertThat(first.pendingCount()).isEqualTo(1);
		assertThat(first.averageScore()).isEqualTo(80.0);
		assertThat(first.maxScore()).isEqualTo(100);

		AssignmentStatRow second = stats.assignments().get(1);
		assertThat(second.submittedCount()).isEqualTo(1);
		assertThat(second.submissionRate()).isCloseTo(33.3333, org.assertj.core.data.Offset.offset(0.001));
		assertThat(second.pendingCount()).isZero();
		assertThat(second.averageScore()).isEqualTo(40.0);
	}

	@Test
	@DisplayName("findStats - 채점 전 제출은 평균에서 제외되고, 채점된 제출이 없으면 평균은 null")
	void averageExcludesUngraded() {
		User a = enrolled(course, "a@test.com", "20260001", "가나다");
		Assignment a1 = assignment(course, "1주차", 100, NOW.minusDays(1));
		submit(a1, a, null);

		AssignmentStatRow row = stats().assignments().getFirst();

		assertThat(row.submittedCount()).isEqualTo(1);
		assertThat(row.gradedCount()).isZero();
		assertThat(row.averageScore()).isNull();
	}

	@Test
	@DisplayName("findStats - 재채점으로 점수가 바뀌면 평균도 바뀜")
	void averageFollowsRegrade() {
		User a = enrolled(course, "a@test.com", "20260001", "가나다");
		User b = enrolled(course, "b@test.com", "20260002", "나다라");
		Assignment a1 = assignment(course, "1주차", 100, NOW.minusDays(1));
		Submission first = submit(a1, a, 60);
		submit(a1, b, 100);
		assertThat(stats().assignments().getFirst().averageScore()).isEqualTo(80.0);

		first.grade(80, "재채점", NOW);

		assertThat(stats().assignments().getFirst().averageScore()).isEqualTo(90.0);
	}

	@Test
	@DisplayName("findStats - 예정 과제는 제출률 · 과제 목록 · 미제출 계산에 포함되지 않음")
	void upcomingExcluded() {
		User a = enrolled(course, "a@test.com", "20260001", "가나다");
		assignment(course, "예정 과제", 100, NOW.plusDays(1));
		Assignment started = assignment(course, "시작된 과제", 100, NOW.minusDays(1));
		submit(started, a, null);

		CourseStatsResponse stats = stats();

		assertThat(stats.startedAssignmentCount()).isEqualTo(1);
		assertThat(stats.assignments()).extracting(AssignmentStatRow::title).containsExactly("시작된 과제");
		assertThat(stats.possibleCount()).isEqualTo(1);
		assertThat(stats.submissionRate()).isEqualTo(100.0);
		assertThat(stats.missingStudents()).isEmpty();
	}

	@Test
	@DisplayName("findStats - 시작일시 정각인 과제는 시작된 것으로 계산됨 (경계)")
	void startedAtBoundary() {
		enrolled(course, "a@test.com", "20260001", "가나다");
		assignment(course, "정각 시작", 100, NOW);
		assignment(course, "1초 뒤 시작", 100, NOW.plusSeconds(1));

		assertThat(stats().assignments()).extracting(AssignmentStatRow::title).containsExactly("정각 시작");
	}

	@Test
	@DisplayName("findStats - 수강생이 아닌 학생의 제출과 다른 강좌의 제출은 집계에 섞이지 않음")
	void ignoresOutsidersAndOtherCourses() {
		User a = enrolled(course, "a@test.com", "20260001", "가나다");
		Assignment a1 = assignment(course, "1주차", 100, NOW.minusDays(1));
		submit(a1, a, 50);
		User outsider = savedUser("out@test.com", Role.STUDENT, "20269999", "외부인");
		submit(a1, outsider, 100);
		Course other = courseRepository.save(
				Course.builder().instructor(instructor).name("데이터베이스").joinCode("BBBBB3").build());
		enrolled(other, "o@test.com", "20260077", "다른강좌학생");
		Assignment otherAssignment = assignment(other, "다른 강좌 과제", 100, NOW.minusDays(1));
		submit(otherAssignment, a, 10);

		CourseStatsResponse stats = stats();

		assertThat(stats.submittedCount()).isEqualTo(1);
		assertThat(stats.assignments().getFirst().averageScore()).isEqualTo(50.0);
		assertThat(stats.submissionRate()).isEqualTo(100.0);
	}

	@Test
	@DisplayName("findStats - 미제출자는 미제출 과제 수가 많은 순, 같으면 이름 순이고 모두 제출한 학생은 빠짐")
	void missingStudents() {
		User a = enrolled(course, "a@test.com", "20260001", "다다다");
		enrolled(course, "b@test.com", "20260002", "가가가");
		User c = enrolled(course, "c@test.com", "20260003", "나나나");
		Assignment a1 = assignment(course, "1주차", 100, NOW.minusDays(3));
		Assignment a2 = assignment(course, "2주차", 100, NOW.minusDays(2));
		Assignment a3 = assignment(course, "3주차", 100, NOW.minusDays(1));
		// 다다다: 3개 모두 제출, 나나나: 1개 제출(2개 미제출), 가가가: 0개 제출(3개 미제출)
		submit(a1, a, null);
		submit(a2, a, null);
		submit(a3, a, null);
		submit(a1, c, null);

		assertThat(stats().missingStudents()).extracting(MissingStudentRow::studentName, MissingStudentRow::missingCount)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("가가가", 3L),
						org.assertj.core.groups.Tuple.tuple("나나나", 2L));
	}

	@Test
	@DisplayName("findStats - 다른 강좌에서 제출한 기록은 이 강좌의 미제출 계산에 영향을 주지 않음")
	void missingIgnoresOtherCourseSubmissions() {
		User a = enrolled(course, "a@test.com", "20260001", "가나다");
		assignment(course, "1주차", 100, NOW.minusDays(1));
		Course other = courseRepository.save(
				Course.builder().instructor(instructor).name("데이터베이스").joinCode("BBBBB3").build());
		enrollmentRepository.save(Enrollment.builder().course(other).student(a).build());
		Assignment otherAssignment = assignment(other, "DB 1주차", 100, NOW.minusDays(1));
		submit(otherAssignment, a, null);

		CourseStatsResponse stats = stats();

		assertThat(stats.submittedCount()).isZero();
		assertThat(stats.missingStudents()).extracting(MissingStudentRow::studentName, MissingStudentRow::missingCount)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("가나다", 1L));
	}

	@Test
	@DisplayName("findStats - 수강생이 없으면 제출률은 null이고 과제별 제출률도 null (0으로 나누지 않음)")
	void noStudents() {
		assignment(course, "1주차", 100, NOW.minusDays(1));

		CourseStatsResponse stats = stats();

		assertThat(stats.enrolledCount()).isZero();
		assertThat(stats.possibleCount()).isZero();
		assertThat(stats.submissionRate()).isNull();
		assertThat(stats.assignments().getFirst().submissionRate()).isNull();
		assertThat(stats.missingStudents()).isEmpty();
	}

	@Test
	@DisplayName("findStats - 시작된 과제가 없으면 제출률은 null이고 빈 목록")
	void noAssignments() {
		enrolled(course, "a@test.com", "20260001", "가나다");

		CourseStatsResponse stats = stats();

		assertThat(stats.startedAssignmentCount()).isZero();
		assertThat(stats.submissionRate()).isNull();
		assertThat(stats.assignments()).isEmpty();
		assertThat(stats.missingStudents()).isEmpty();
	}

	@Test
	@DisplayName("findStats - 제출이 하나도 없으면 제출률 0%이고 모두 미제출")
	void noSubmissions() {
		enrolled(course, "a@test.com", "20260001", "가나다");
		enrolled(course, "b@test.com", "20260002", "나다라");
		assignment(course, "1주차", 100, NOW.minusDays(1));

		CourseStatsResponse stats = stats();

		assertThat(stats.submissionRate()).isEqualTo(0.0);
		assertThat(stats.assignments().getFirst().averageScore()).isNull();
		assertThat(stats.missingStudents()).hasSize(2);
	}

	@Test
	@DisplayName("findStats - courseId가 없으면 가장 최근 개설한 강좌, 강좌 목록도 함께 반환됨")
	void defaultsToNewestCourse() {
		Course newer = courseRepository.save(
				Course.builder().instructor(instructor).name("데이터베이스").joinCode("BBBBB3").build());

		StatsPageResponse page = statsService.findStats(instructor.getId(), null);

		assertThat(page.courses()).hasSize(2);
		assertThat(page.stats().courseId()).isIn(course.getId(), newer.getId());
		assertThat(page.stats().courseName()).isEqualTo(page.courses().getFirst().name());
	}

	@Test
	@DisplayName("findStats - 강좌가 하나도 없으면 stats는 null")
	void noCourses() {
		User fresh = savedUser("fresh-teacher@test.com", Role.INSTRUCTOR, null, "새강사");

		StatsPageResponse page = statsService.findStats(fresh.getId(), null);

		assertThat(page.courses()).isEmpty();
		assertThat(page.stats()).isNull();
	}

	@Test
	@DisplayName("findStats - 다른 강사의 강좌는 403, 없는 강좌는 404 (B5)")
	void ownership() {
		User other = savedUser("other-teacher@test.com", Role.INSTRUCTOR, null, "다른강사");

		assertThatThrownBy(() -> statsService.findStats(other.getId(), course.getId()))
				.isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> statsService.findStats(instructor.getId(), 999999L))
				.isInstanceOf(CourseNotFoundException.class);
	}
}
