package kr.ac.ync.midterm.export.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

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
import kr.ac.ync.midterm.global.csv.CsvBuilderTest;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 표 내려받기 (X8 수강생, X32 제출 현황 · 통계): 파일 내용이 화면과 같은지, 수식 주입 방지, 권한(B5), 빈 데이터.
 */
class InstructorExportControllerTest extends BaseController {

	private static final String EVIL_NAME = "=HYPERLINK(\"http://evil\",\"x\")";

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
	private Assignment assignment;
	private User normal;
	private User evil;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바,스프링").joinCode("AAAAA2").build());
		normal = userRepository.save(User.builder().email("normal@test.com").password("encodedPassword")
				.name("홍길동").studentNo("20260001").role(Role.STUDENT).build());
		evil = userRepository.save(User.builder().email("evil@test.com").password("encodedPassword")
				.name(EVIL_NAME).studentNo("20260002").role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(normal).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(evil).build());

		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("+SUM(1) 과제")
				.content("내용").startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());

		Submission graded = Submission.builder().assignment(assignment).student(normal).content("답안")
				.submittedAt(LocalDateTime.of(2026, 10, 12, 14, 30)).build();
		graded.attachFile("stored.pdf", "a,b.pdf");
		graded.grade(90, "피드백", LocalDateTime.now());
		submissionRepository.save(graded);
		// 수강생이 아닌 학생의 제출은 파일에 들어가면 안 된다
		submissionRepository.save(Submission.builder().assignment(assignment).student(student).content("외부 답안")
				.submittedAt(LocalDateTime.now()).build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private List<List<String>> csv(String url, User actor) throws Exception {
		byte[] bytes = mockMvc.perform(get(url).with(user(principal(actor))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		return CsvBuilderTest.parse(bytes);
	}

	// ---------- 수강생 (X8) ----------

	@Test
	@DisplayName("수강생 CSV - 헤더 · 등록 순서의 모든 수강생이 들어 있고, 응답 헤더는 text/csv UTF-8 attachment, 한글 파일명이 인코딩됨")
	void students() throws Exception {
		mockMvc.perform(get("/instructor/courses/" + course.getId() + "/students.csv")
						.with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")));

		List<List<String>> rows = csv("/instructor/courses/" + course.getId() + "/students.csv", instructor);

		assertThat(rows.getFirst()).containsExactly("학번", "이름", "이메일", "수강 등록일");
		assertThat(rows).hasSize(3);
		assertThat(rows.get(1).subList(0, 3)).containsExactly("20260001", "홍길동", "normal@test.com");
		assertThat(rows.get(1).get(3)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
		assertThat(rows.get(2).get(0)).isEqualTo("20260002");
	}

	@Test
	@DisplayName("수강생 CSV - 수식으로 읽힐 수 있는 이름은 앞에 작은따옴표가 붙고 쉼표 · 따옴표가 있어도 값이 깨지지 않음")
	void students_formulaInjection() throws Exception {
		List<List<String>> rows = csv("/instructor/courses/" + course.getId() + "/students.csv", instructor);

		assertThat(rows.get(2).get(1)).isEqualTo("'" + EVIL_NAME);
		assertThat(rows.get(2)).hasSize(4);
	}

	@Test
	@DisplayName("수강생 CSV - 수강생이 없으면 머리글만 내려옴")
	void students_empty() throws Exception {
		Course empty = courseRepository.save(
				Course.builder().instructor(instructor).name("빈 강좌").joinCode("BBBBB3").build());

		assertThat(csv("/instructor/courses/" + empty.getId() + "/students.csv", instructor))
				.containsExactly(List.of("학번", "이름", "이메일", "수강 등록일"));
	}

	// ---------- 제출 현황 (X32) ----------

	@Test
	@DisplayName("제출 현황 CSV - 제출한 학생(채점 완료, 점수, 첨부 파일명)과 제출하지 않은 학생이 함께 들어 있고, 수강생이 아닌 학생은 빠짐")
	void submissions() throws Exception {
		List<List<String>> rows = csv("/instructor/assignments/" + assignment.getId() + "/submissions.csv", instructor);

		assertThat(rows.getFirst()).containsExactly("학번", "이름", "제출 여부", "제출 시각", "상태", "점수", "첨부 파일명");
		assertThat(rows).hasSize(3);
		assertThat(rows.get(1)).containsExactly("20260001", "홍길동", "제출", "2026-10-12 14:30", "채점 완료", "90",
				"a,b.pdf");
		assertThat(rows.get(2)).containsExactly("20260002", "'" + EVIL_NAME, "미제출", "", "", "", "");
		assertThat(rows.stream().flatMap(List::stream)).doesNotContain("외부 답안");
	}

	@Test
	@DisplayName("제출 현황 CSV - 채점 전 제출은 상태가 '제출함'이고 점수가 빈 칸임")
	void submissions_ungraded() throws Exception {
		Submission waiting = submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), normal.getId())
				.orElseThrow();
		submissionRepository.delete(waiting);
		submissionRepository.flush();
		submissionRepository.save(Submission.builder().assignment(assignment).student(normal).content("답안")
				.submittedAt(LocalDateTime.of(2026, 10, 12, 14, 30)).build());

		List<List<String>> rows = csv("/instructor/assignments/" + assignment.getId() + "/submissions.csv", instructor);

		assertThat(rows.get(1)).containsExactly("20260001", "홍길동", "제출", "2026-10-12 14:30", "제출함", "", "");
	}

	// ---------- 통계 (X32) ----------

	@Test
	@DisplayName("통계 CSV(과제별) - 과제 제목의 수식 시작 글자가 막히고 숫자가 통계 화면과 같음")
	void statsAssignments() throws Exception {
		List<List<String>> rows = csv("/instructor/stats/assignments.csv?courseId=" + course.getId(), instructor);

		assertThat(rows.getFirst()).containsExactly("과제", "제출 수", "수강생 수", "제출률(%)", "채점 대기", "평균 점수", "배점");
		assertThat(rows).hasSize(2);
		// 수강생 2명 중 1명 제출 → 50.0%, 채점 완료 1건 90점
		assertThat(rows.get(1)).containsExactly("'+SUM(1) 과제", "1", "2", "50.0", "0", "90.0", "100");

		mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(content().string(containsString("50.0%")))
				.andExpect(content().string(containsString("90.0 / 100")));
	}

	@Test
	@DisplayName("통계 CSV(미제출 학생) - 미제출 과제가 있는 학생과 개수가 화면과 같음")
	void statsMissing() throws Exception {
		List<List<String>> rows = csv("/instructor/stats/missing.csv?courseId=" + course.getId(), instructor);

		assertThat(rows.getFirst()).containsExactly("학번", "이름", "미제출 과제 수");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(1)).containsExactly("20260002", "'" + EVIL_NAME, "1");
	}

	@Test
	@DisplayName("통계 CSV - 과제나 수강생이 없으면 머리글만 내려오고, courseId가 없으면 400")
	void stats_emptyAndMissingParam() throws Exception {
		Course empty = courseRepository.save(
				Course.builder().instructor(instructor).name("빈 강좌").joinCode("BBBBB3").build());

		assertThat(csv("/instructor/stats/assignments.csv?courseId=" + empty.getId(), instructor)).hasSize(1);
		assertThat(csv("/instructor/stats/missing.csv?courseId=" + empty.getId(), instructor)).hasSize(1);
		mockMvc.perform(get("/instructor/stats/assignments.csv").with(user(principal(instructor))))
				.andExpect(status().isBadRequest());
	}

	// ---------- 권한 ----------

	@Test
	@DisplayName("다른 강사의 강좌 · 과제는 네 가지 모두 403이고 파일이 내려오지 않음 (B5)")
	void otherInstructor() throws Exception {
		User other = createUser("other-teacher@test.com", Role.INSTRUCTOR);
		String[] urls = {
				"/instructor/courses/" + course.getId() + "/students.csv",
				"/instructor/assignments/" + assignment.getId() + "/submissions.csv",
				"/instructor/stats/assignments.csv?courseId=" + course.getId(),
				"/instructor/stats/missing.csv?courseId=" + course.getId() };

		for (String url : urls) {
			mockMvc.perform(get(url).with(user(principal(other))))
					.andExpect(status().isForbidden())
					.andExpect(content().string(org.hamcrest.Matchers.not(containsString("20260001"))));
		}
	}

	@Test
	@DisplayName("없는 강좌 · 과제는 404")
	void notFound() throws Exception {
		mockMvc.perform(get("/instructor/courses/999999/students.csv").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/instructor/assignments/999999/submissions.csv").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/instructor/stats/assignments.csv?courseId=999999").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("학생은 403, 비로그인은 로그인 화면으로 이동, POST는 허용되지 않음")
	void studentAnonymousAndMethod() throws Exception {
		String[] urls = {
				"/instructor/courses/" + course.getId() + "/students.csv",
				"/instructor/assignments/" + assignment.getId() + "/submissions.csv",
				"/instructor/stats/assignments.csv?courseId=" + course.getId(),
				"/instructor/stats/missing.csv?courseId=" + course.getId() };

		for (String url : urls) {
			mockMvc.perform(get(url).with(user(principal(student)))).andExpect(status().isForbidden());
			mockMvc.perform(get(url)).andExpect(status().is3xxRedirection());
			mockMvc.perform(post(url).with(user(principal(instructor))).with(csrf()))
					.andExpect(status().isMethodNotAllowed());
		}
	}

	// ---------- 화면의 버튼 ----------

	@Test
	@DisplayName("강좌 상세 · 제출 현황 · 통계 화면에 CSV 내려받기 버튼이 올바른 주소로 걸려 있음")
	void buttons() throws Exception {
		mockMvc.perform(get("/instructor/courses/" + course.getId()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("/instructor/courses/" + course.getId() + "/students.csv")));
		mockMvc.perform(get("/instructor/assignments/" + assignment.getId() + "/submissions")
						.with(user(principal(instructor))))
				.andExpect(content().string(containsString("/instructor/assignments/" + assignment.getId() + "/submissions.csv")));
		mockMvc.perform(get("/instructor/stats").param("courseId", course.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(content().string(containsString("/instructor/stats/assignments.csv?courseId=" + course.getId())))
				.andExpect(content().string(containsString("/instructor/stats/missing.csv?courseId=" + course.getId())));
	}
}
