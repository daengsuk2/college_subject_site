package kr.ac.ync.midterm.submission.controller;

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

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 강사의 채점 화면 (I5): 표시, 저장, 점수 범위(B6), 권한(B5), 채점 후 재제출 차단(B4), XSS.
 */
class InstructorGradingControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private User instructor;
	private Assignment assignment;
	private Submission submission;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
		submission = submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content("학생이 쓴 답안").submittedAt(now.minusHours(1)).build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private String url() {
		return "/instructor/submissions/" + submission.getId() + "/grade";
	}

	private MockHttpServletRequestBuilder gradeAs(User actor, String score, String feedback) {
		return post(url()).param("score", score).param("feedback", feedback)
				.with(user(principal(actor))).with(csrf());
	}

	private String statusUrl() {
		return "/instructor/assignments/" + assignment.getId() + "/submissions";
	}

	@Test
	@DisplayName("GET 채점 화면 - 학생 · 과제 · 제출 내용과 점수 입력 폼이 표시됨")
	void form_shown() throws Exception {
		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/grade"))
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(containsString("학생이 쓴 답안")))
				.andExpect(content().string(containsString("20260000")))
				.andExpect(content().string(containsString("점수 (0 ~ 100)")))
				.andExpect(content().string(not(containsString("채점 수정"))));
	}

	@Test
	@DisplayName("GET 채점 화면 - 첨부파일이 있으면 다운로드 링크가 표시됨")
	void form_withFile() throws Exception {
		submission.attachFile("stored.pdf", "내 답안.pdf");
		submissionRepository.flush();

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("내 답안.pdf")))
				.andExpect(content().string(containsString("/submissions/" + submission.getId() + "/file")));
	}

	@Test
	@DisplayName("POST 채점 - 저장하면 채점 완료가 되고 제출 현황으로 이동하며 점수가 표시됨")
	void grade_ok() throws Exception {
		mockMvc.perform(gradeAs(instructor, "85", "수고했습니다"))
				.andExpect(redirectedUrl(statusUrl()))
				.andExpect(flash().attribute("message", "채점을 저장했습니다."));

		assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.GRADED);
		assertThat(submission.getScore()).isEqualTo(85);
		assertThat(submission.getFeedback()).isEqualTo("수고했습니다");
		assertThat(submission.getGradedAt()).isNotNull();

		mockMvc.perform(get(statusUrl()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("채점 완료")))
				.andExpect(content().string(containsString("85 / 100")));
	}

	@Test
	@DisplayName("POST 채점 - 0점과 배점과 같은 점수는 저장됨 (B6 경계)")
	void grade_boundaries() throws Exception {
		mockMvc.perform(gradeAs(instructor, "0", "")).andExpect(redirectedUrl(statusUrl()));
		assertThat(submission.getScore()).isZero();

		mockMvc.perform(gradeAs(instructor, "100", "")).andExpect(redirectedUrl(statusUrl()));
		assertThat(submission.getScore()).isEqualTo(100);
	}

	@Test
	@DisplayName("POST 채점 - 음수와 배점 초과는 점수 칸에 에러가 표시되고 저장되지 않음 (B6)")
	void grade_outOfRange() throws Exception {
		mockMvc.perform(gradeAs(instructor, "-1", "x"))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/grade"))
				.andExpect(model().attributeHasFieldErrors("gradeRequest", "score"))
				.andExpect(content().string(containsString("점수는 0 이상이어야 합니다.")));

		mockMvc.perform(gradeAs(instructor, "101", "x"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("gradeRequest", "score"))
				.andExpect(content().string(containsString("점수는 0 이상 100 이하여야 합니다.")));

		assertThat(submission.isGraded()).isFalse();
		assertThat(submission.getScore()).isNull();
	}

	@Test
	@DisplayName("POST 채점 - 화면 입력 제한(max)과 무관하게 직접 보낸 범위 밖 점수도 서버가 거부함 (B6)")
	void grade_forcedOutOfRange() throws Exception {
		for (String score : new String[] { "1000", "2147483647", "-2147483648" }) {
			mockMvc.perform(gradeAs(instructor, score, "강제"))
					.andExpect(status().isOk())
					.andExpect(model().attributeHasFieldErrors("gradeRequest", "score"));
		}
		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("POST 채점 - 점수가 비어 있거나 숫자가 아니면 에러가 표시되고 저장되지 않음")
	void grade_invalidFormat() throws Exception {
		mockMvc.perform(gradeAs(instructor, "", "x"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("점수를 입력해 주세요.")));
		mockMvc.perform(gradeAs(instructor, "abc", "x"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("gradeRequest", "score"));
		mockMvc.perform(gradeAs(instructor, "85.5", "x"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("gradeRequest", "score"));

		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("POST 채점 - 피드백이 너무 길면 에러가 표시되고 저장되지 않음")
	void grade_feedbackTooLong() throws Exception {
		mockMvc.perform(gradeAs(instructor, "80", "가".repeat(2001)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("피드백은 2000자 이하로 입력해 주세요.")));

		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("GET 채점 화면 - 이미 채점한 제출은 기존 점수와 피드백이 채워지고, 고쳐서 저장할 수 있음")
	void regrade() throws Exception {
		mockMvc.perform(gradeAs(instructor, "60", "첫 채점"));

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("채점 수정")))
				.andExpect(content().string(containsString("value=\"60\"")))
				.andExpect(content().string(containsString("첫 채점")));

		mockMvc.perform(gradeAs(instructor, "90", "다시 채점"))
				.andExpect(redirectedUrl(statusUrl()));
		assertThat(submission.getScore()).isEqualTo(90);
		assertThat(submission.getFeedback()).isEqualTo("다시 채점");
	}

	@Test
	@DisplayName("채점 후 학생이 다시 제출하면 '채점 완료된 과제입니다.'로 거부되고 내용이 바뀌지 않음 (B4)")
	void afterGrading_studentResubmitRejected() throws Exception {
		mockMvc.perform(gradeAs(instructor, "75", "채점"));

		mockMvc.perform(post("/student/assignments/" + assignment.getId() + "/submit")
						.param("content", "고치려는 내용").with(user(principal(student))).with(csrf()))
				.andExpect(flash().attribute("error", "채점 완료된 과제입니다."));

		assertThat(submission.getContent()).isEqualTo("학생이 쓴 답안");
	}

	@Test
	@DisplayName("다른 강사는 채점 화면 열기도 저장도 403이고 저장되지 않음 (B5)")
	void otherInstructor() throws Exception {
		User other = createUser("other-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get(url()).with(user(principal(other))))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("학생이 쓴 답안"))));
		mockMvc.perform(gradeAs(other, "100", "몰래 채점"))
				.andExpect(status().isForbidden());

		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("학생은 403, CSRF 토큰이 없으면 거부, 비로그인은 로그인으로 이동, 없는 제출은 404")
	void security() throws Exception {
		mockMvc.perform(get(url()).with(user(principal(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(gradeAs(student, "100", "학생이 채점"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(url()).param("score", "100").with(user(principal(instructor))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(url()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/instructor/submissions/999999/grade").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/instructor/submissions/abc/grade").with(user(principal(instructor))))
				.andExpect(status().isBadRequest());

		assertThat(submission.isGraded()).isFalse();
	}

	@Test
	@DisplayName("채점 화면 - 제출 내용 · 학생 이름의 HTML은 이스케이프되어 표시됨 (XSS)")
	void xssEscaped() throws Exception {
		submission.resubmit("<script>alert(1)</script>", LocalDateTime.now());
		submissionRepository.flush();

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));

		// 피드백에 넣은 HTML도 다시 열었을 때 이스케이프된다
		mockMvc.perform(gradeAs(instructor, "50", "<img src=x onerror=alert(2)>"));
		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(2)>"))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(2)&gt;")));
	}

	@Test
	@DisplayName("제출 현황 - 제출마다 채점 버튼이 있고, 채점한 제출은 '수정'으로 표시됨")
	void statusPage_gradeButtons() throws Exception {
		mockMvc.perform(get(statusUrl()).with(user(principal(instructor))))
				.andExpect(content().string(containsString(url())))
				.andExpect(content().string(containsString(">채점<")));

		mockMvc.perform(gradeAs(instructor, "70", ""));
		mockMvc.perform(get(statusUrl()).with(user(principal(instructor))))
				.andExpect(content().string(containsString(">수정<")));
	}
}
