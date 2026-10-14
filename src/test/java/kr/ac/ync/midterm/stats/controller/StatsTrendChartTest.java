package kr.ac.ync.midterm.stats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * 강사 통계 화면의 과제별 추이 선 차트 (X31): 화면에 내려가는 차트 데이터가 계산한 값과 같은지, 순서 · 제외 규칙 · 권한 · XSS.
 * 차트가 실제로 그려지는지는 브라우저에서 따로 확인한다. (한글은 자바스크립트 유니코드 이스케이프로 내려가므로 영문 제목을 쓴다.)
 */
class StatsTrendChartTest extends BaseController {

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
	private User studentA;
	private User studentB;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(Course.builder().instructor(instructor).name("Java").joinCode("AAAAA2").build());
		studentA = student("a@test.com", "20260001");
		studentB = student("b@test.com", "20260002");
		enrollmentRepository.save(Enrollment.builder().course(course).student(studentA).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(studentB).build());
	}

	private User student(String email, String no) {
		return userRepository.save(User.builder().email(email).password("encodedPassword").name("Student " + no)
				.studentNo(no).role(Role.STUDENT).build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Assignment assignment(Course target, String title, int maxScore, LocalDateTime startAt) {
		return assignmentRepository.save(Assignment.builder().course(target).title(title).content("내용")
				.startAt(startAt).endAt(startAt.plusDays(30)).maxScore(maxScore).build());
	}

	private Submission submit(Assignment assignment, User who, Integer score) {
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment).student(who)
				.content("답안").submittedAt(LocalDateTime.now().minusMinutes(5)).build());
		if (score != null) {
			submission.grade(score, null, LocalDateTime.now());
		}
		return submission;
	}

	private String page(String url, User actor) throws Exception {
		return mockMvc.perform(get(url).with(user(principal(actor))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	/** 화면에 내려간 "var 이름 = [..];" 배열을 읽는다 (null은 null로) */
	private List<String> jsArray(String html, String name) {
		Matcher m = Pattern.compile("var " + name + " = \\[(.*?)\\];").matcher(html);
		assertThat(m.find()).as(name + " 배열이 화면에 있음").isTrue();
		List<String> values = new ArrayList<>();
		for (String v : m.group(1).split(",")) {
			if (!v.isBlank()) {
				values.add(v.trim());
			}
		}
		return values;
	}

	/**
	 * 시작이 이른 순: W1(배점 100, A 80 · B 80 채점, 제출 2/2), W2(배점 50, A 40 채점, 제출 1/2), W3(방금 시작, 제출 없음)
	 * 일부러 시작이 늦은 과제부터 만들고, 예정 과제 · 수강생이 아닌 학생의 제출 · 다른 강좌의 과제를 섞어 둔다.
	 */
	private void fixture() {
		LocalDateTime now = LocalDateTime.now();
		assignment(course, "W3", 100, now.minusSeconds(2));
		Assignment w2 = assignment(course, "W2", 50, now.minusDays(1));
		Assignment w1 = assignment(course, "W1", 100, now.minusDays(3));
		assignment(course, "Upcoming", 100, now.plusDays(1));
		submit(w1, studentA, 80);
		submit(w1, studentB, 80);
		submit(w2, studentA, 40);

		User outsider = student("out@test.com", "20269999");
		submit(w1, outsider, 10);
		Course other = courseRepository.save(Course.builder().instructor(instructor).name("Other").joinCode("BBBBB3").build());
		assignment(other, "OtherCourse", 100, now.minusDays(2));
	}

	@Test
	@DisplayName("추이 선 차트 - 데이터가 과제 순서대로 제출률 · 평균 점수 비율 · 툴팁 값으로 내려가고 계산한 값과 일치함")
	void trendData() throws Exception {
		fixture();

		String html = page("/instructor/stats?courseId=" + course.getId(), instructor);

		assertThat(html).contains("id=\"trendChart\"");
		assertThat(jsArray(html, "trendLabels")).containsExactly("\"W1\"", "\"W2\"", "\"W3\"");
		assertThat(jsArray(html, "trendSubmissionRates")).containsExactly("100.0", "50.0", "0.0");
		// W1: 평균 80 / 배점 100 = 80%, W2: 평균 40 / 배점 50 = 80%, W3: 채점된 제출이 없어 null (선이 끊김)
		assertThat(jsArray(html, "trendScoreRates")).containsExactly("80.0", "80.0", "null");
		assertThat(jsArray(html, "trendSubmitted")).containsExactly("2", "1", "0");
		assertThat(jsArray(html, "trendEnrolled")).containsExactly("2", "2", "2");
		assertThat(jsArray(html, "trendAverages")).containsExactly("80.0", "40.0", "null");
		assertThat(jsArray(html, "trendMaxScores")).containsExactly("100", "50", "100");
	}

	@Test
	@DisplayName("추이 선 차트 - 예정 과제 · 다른 강좌의 과제는 나오지 않고, 수강생이 아닌 학생의 제출은 평균 · 제출률에 섞이지 않음")
	void trendExclusions() throws Exception {
		fixture();

		String html = page("/instructor/stats?courseId=" + course.getId(), instructor);

		assertThat(jsArray(html, "trendLabels")).doesNotContain("\"Upcoming\"", "\"OtherCourse\"");
		// 외부인이 W1에 10점으로 제출했어도 W1은 수강생 2명의 80점 평균 그대로
		assertThat(jsArray(html, "trendScoreRates").getFirst()).isEqualTo("80.0");
		assertThat(jsArray(html, "trendSubmitted").getFirst()).isEqualTo("2");
	}

	@Test
	@DisplayName("추이 선 차트 - 재채점하면 평균 점수 비율이 바뀜")
	void trendRegrade() throws Exception {
		fixture();
		Submission graded = submissionRepository.findAll().stream()
				.filter(s -> s.getStudent().getId().equals(studentB.getId()) && s.getScore() != null).findFirst().orElseThrow();

		graded.grade(100, "재채점", LocalDateTime.now());

		// W1 평균 (80 + 100) / 2 = 90 → 90.0%
		assertThat(jsArray(page("/instructor/stats?courseId=" + course.getId(), instructor), "trendScoreRates").getFirst())
				.isEqualTo("90.0");
	}

	@Test
	@DisplayName("추이 선 차트 - 과제가 하나뿐이거나 모든 과제의 평균이 없어도 오류 없이 값이 내려감")
	void trendSingleAndNoAverage() throws Exception {
		assignment(course, "Only", 100, LocalDateTime.now().minusDays(1));

		String html = page("/instructor/stats?courseId=" + course.getId(), instructor);

		assertThat(jsArray(html, "trendLabels")).containsExactly("\"Only\"");
		assertThat(jsArray(html, "trendSubmissionRates")).containsExactly("0.0");
		assertThat(jsArray(html, "trendScoreRates")).containsExactly("null");
	}

	@Test
	@DisplayName("추이 선 차트 - 수강생이나 시작된 과제가 없으면 차트 대신 안내 문구가 보이고 오류가 없음")
	void trendEmpty() throws Exception {
		Course empty = courseRepository.save(Course.builder().instructor(instructor).name("Empty").joinCode("CCCCC4").build());

		mockMvc.perform(get("/instructor/stats").param("courseId", empty.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("id=\"trendChart\""))))
				.andExpect(content().string(containsString("제출률을 계산할 수 없습니다.")));
	}

	@Test
	@DisplayName("추이 선 차트 - 기존 도넛 · 막대 차트와 표, 안내 문구도 함께 표시됨")
	void existingChartsKept() throws Exception {
		fixture();

		mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(content().string(containsString("id=\"submissionChart\"")))
				.andExpect(content().string(containsString("id=\"averageChart\"")))
				.andExpect(content().string(containsString("id=\"trendChart\"")))
				.andExpect(content().string(containsString("id=\"assignmentTable\"")))
				.andExpect(content().string(containsString("과제 사이의 시간 간격은 반영하지 않습니다.")));
	}

	@Test
	@DisplayName("추이 선 차트 - 다른 강사의 강좌는 403이고 차트 데이터가 내려가지 않음, 학생은 403 (B5)")
	void trendAccess() throws Exception {
		fixture();
		User other = createUser("other-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString())
						.with(user(principal(other))))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("trendLabels"))));
		mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString())
						.with(user(principal(student))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("추이 선 차트 - 과제 제목의 따옴표 · HTML · </script>는 차트 데이터에서 안전하게 이스케이프됨 (XSS)")
	void trendXss() throws Exception {
		assignment(course, "</script><script>alert(1)</script>\"quote'", 100, LocalDateTime.now().minusDays(1));
		assignment(course, "<img src=x onerror=alert(2)>", 100, LocalDateTime.now().minusDays(2));

		String html = page("/instructor/stats?courseId=" + course.getId(), instructor);

		// 스크립트를 끝내는 </script> 는 이스케이프(</script>)되어 차트 데이터 밖으로 빠져나오지 못한다
		assertThat(html).doesNotContain("</script><script>alert(1)");
		assertThat(html).contains("<\\/script><script>alert(1)<\\/script>");
		// 스크립트 블록 안의 글자는 자바스크립트 문자열일 뿐이므로, 블록을 뺀 HTML에는 태그가 그대로 나오지 않고 이스케이프되어 있다
		String outsideScripts = html.replaceAll("(?s)<script.*?</script>", "");
		assertThat(outsideScripts).doesNotContain("<img src=x onerror=alert(2)>");
		assertThat(outsideScripts).contains("&lt;img src=x onerror=alert(2)&gt;");
	}
}
