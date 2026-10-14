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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 이의 신청 (X29): 학생 신청(채점 후 1회, B2), 강사 답변과 재채점(B5, B6), 권한, 화면 표시, XSS.
 */
class ObjectionControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private User instructor;
	private User otherInstructor;
	private Course course;
	private Assignment assignment;
	private Submission submission;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		otherInstructor = createUser("other-teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("OBJEC1").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
		submission = submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content("답안").submittedAt(now.minusHours(1)).build());
	}

	private void grade() {
		submission.grade(70, "피드백", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);
	}

	private void raised(String content) {
		submission.raiseObjection(content, LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);
	}

	private Submission reload() {
		submissionRepository.flush();
		return submissionRepository.findById(submission.getId()).orElseThrow();
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private String raiseUrl() {
		return "/student/assignments/" + assignment.getId() + "/submission/objection";
	}

	private String respondUrl() {
		return "/instructor/submissions/" + submission.getId() + "/objection";
	}

	private String detailUrl() {
		return "/student/assignments/" + assignment.getId();
	}

	private String gradeUrl() {
		return "/instructor/submissions/" + submission.getId() + "/grade";
	}

	private MockHttpServletRequestBuilder raiseAs(User actor, String content) {
		return post(raiseUrl()).param("content", content).with(user(principal(actor))).with(csrf());
	}

	private MockHttpServletRequestBuilder respondAs(User actor, String response, String score) {
		MockHttpServletRequestBuilder request = post(respondUrl()).param("response", response)
				.with(user(principal(actor))).with(csrf());
		return score == null ? request : request.param("score", score);
	}

	// ---------- 학생 신청 ----------

	@Test
	@DisplayName("채점 완료된 본인 제출에 이의를 신청하면 내용과 시각이 저장되고 안내가 나옴")
	void raise_success() throws Exception {
		grade();

		mockMvc.perform(raiseAs(student, "  점수가 낮은 것 같습니다.  "))
				.andExpect(redirectedUrl(detailUrl()))
				.andExpect(flash().attribute("message", "이의를 신청했습니다."));

		Submission saved = reload();
		assertThat(saved.getObjectionContent()).isEqualTo("점수가 낮은 것 같습니다.");
		assertThat(saved.getObjectionAt()).isNotNull();
		assertThat(saved.hasObjectionResponse()).isFalse();
		// 신청해도 점수와 채점 상태는 그대로
		assertThat(saved.getScore()).isEqualTo(70);
		assertThat(saved.isGraded()).isTrue();
	}

	@Test
	@DisplayName("과제 상세 - 채점 후에는 이의 신청 폼, 신청 후에는 내용과 답변 대기가 보이고 폼은 사라짐, 채점 전에는 보이지 않음")
	void detail_display() throws Exception {
		mockMvc.perform(get(detailUrl()).with(user(principal(student))))
				.andExpect(content().string(not(containsString("objectionForm"))))
				.andExpect(content().string(not(containsString("objectionArea"))));

		grade();
		mockMvc.perform(get(detailUrl()).with(user(principal(student))))
				.andExpect(content().string(containsString("objectionForm")))
				.andExpect(content().string(containsString(raiseUrl())));

		raised("재확인 부탁드립니다");
		mockMvc.perform(get(detailUrl()).with(user(principal(student))))
				.andExpect(content().string(containsString("재확인 부탁드립니다")))
				.andExpect(content().string(containsString("접수됨")))
				.andExpect(content().string(containsString("강사의 답변을 기다리고 있습니다.")))
				.andExpect(content().string(not(containsString("objectionForm"))));

		submission.answerObjection("확인했습니다", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);
		mockMvc.perform(get(detailUrl()).with(user(principal(student))))
				.andExpect(content().string(containsString("답변 완료")))
				.andExpect(content().string(containsString("확인했습니다")))
				.andExpect(content().string(not(containsString("objectionForm"))));
	}

	@Test
	@DisplayName("채점 전에는 이의를 신청할 수 없고 저장되지 않음")
	void raise_notGraded() throws Exception {
		mockMvc.perform(raiseAs(student, "이의"))
				.andExpect(redirectedUrl(detailUrl()))
				.andExpect(flash().attribute("error", "채점 완료된 제출에만 이의를 신청할 수 있습니다."));

		assertThat(reload().hasObjection()).isFalse();
	}

	@Test
	@DisplayName("이의 신청은 제출당 1회만 가능하고 두 번째 내용은 저장되지 않음")
	void raise_onlyOnce() throws Exception {
		grade();
		mockMvc.perform(raiseAs(student, "첫 이의"));

		mockMvc.perform(raiseAs(student, "두 번째 이의"))
				.andExpect(flash().attribute("error", "이미 이의를 신청했습니다."));

		assertThat(reload().getObjectionContent()).isEqualTo("첫 이의");
	}

	@Test
	@DisplayName("이의 내용이 비었거나 공백뿐이거나 500자를 넘으면 거부되고, 500자는 허용됨")
	void raise_validation() throws Exception {
		grade();

		mockMvc.perform(raiseAs(student, "")).andExpect(flash().attribute("error", "이의 내용을 입력해 주세요."));
		mockMvc.perform(raiseAs(student, "   ")).andExpect(flash().attribute("error", "이의 내용을 입력해 주세요."));
		mockMvc.perform(raiseAs(student, "가".repeat(501)))
				.andExpect(flash().attribute("error", "이의 내용은 500자 이하로 입력해 주세요."));
		assertThat(reload().hasObjection()).isFalse();

		mockMvc.perform(raiseAs(student, "가".repeat(500)))
				.andExpect(flash().attribute("message", "이의를 신청했습니다."));
		assertThat(reload().getObjectionContent()).hasSize(500);
	}

	@Test
	@DisplayName("수강하지 않는 학생은 403, 수강 중이어도 자기 제출이 없으면 404이고 다른 학생의 제출에는 영향이 없음 (B2)")
	void raise_otherStudents() throws Exception {
		grade();
		User outsider = createUser("outsider@test.com", Role.STUDENT);

		mockMvc.perform(raiseAs(outsider, "남의 제출")).andExpect(status().isForbidden());

		enrollmentRepository.save(Enrollment.builder().course(course).student(outsider).build());
		mockMvc.perform(raiseAs(outsider, "남의 제출")).andExpect(status().isNotFound());
		mockMvc.perform(post("/student/assignments/999999/submission/objection").param("content", "이의")
				.with(user(principal(student))).with(csrf())).andExpect(status().isNotFound());

		assertThat(reload().hasObjection()).isFalse();
	}

	@Test
	@DisplayName("이의 신청 - 강사는 403, 비로그인은 로그인 화면으로, CSRF 토큰이 없으면 거부, GET은 허용되지 않음")
	void raise_security() throws Exception {
		grade();

		mockMvc.perform(raiseAs(instructor, "강사가 신청")).andExpect(status().isForbidden());
		mockMvc.perform(post(raiseUrl()).param("content", "이의").with(csrf()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post(raiseUrl()).param("content", "이의").with(user(principal(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(raiseUrl()).with(user(principal(student))))
				.andExpect(status().isMethodNotAllowed());

		assertThat(reload().hasObjection()).isFalse();
	}

	@Test
	@DisplayName("이의 내용과 답변에 HTML이 있어도 학생 · 강사 화면에서 이스케이프됨")
	void xss_escaped() throws Exception {
		grade();
		raised("<script>alert(1)</script>");
		submission.answerObjection("<img src=x onerror=alert(2)>", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);

		for (String page : new String[] { detailUrl() }) {
			mockMvc.perform(get(page).with(user(principal(student))))
					.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
					.andExpect(content().string(containsString("&lt;img src=x onerror=alert(2)&gt;")))
					.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
					.andExpect(content().string(not(containsString("<img src=x onerror=alert(2)>"))));
		}
		mockMvc.perform(get(gradeUrl()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(2)&gt;")))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(2)>"))));
	}

	// ---------- 강사 답변 ----------

	@Test
	@DisplayName("강사가 답변하면 답변과 시각이 저장되고 점수는 그대로이며 채점 화면으로 돌아감")
	void respond_success() throws Exception {
		grade();
		raised("이의");

		mockMvc.perform(respondAs(instructor, "  다시 확인했고 점수는 유지합니다.  ", null))
				.andExpect(redirectedUrl(gradeUrl()))
				.andExpect(flash().attribute("message", "이의에 답변했습니다."));

		Submission saved = reload();
		assertThat(saved.getObjectionResponse()).isEqualTo("다시 확인했고 점수는 유지합니다.");
		assertThat(saved.getObjectionRespondedAt()).isNotNull();
		assertThat(saved.getScore()).isEqualTo(70);
		assertThat(saved.getFeedback()).isEqualTo("피드백");
	}

	@Test
	@DisplayName("답변할 때 점수를 함께 보내면 재채점되고 피드백은 유지됨, 0점과 배점(100)도 허용됨")
	void respond_regrade() throws Exception {
		grade();
		raised("이의");

		mockMvc.perform(respondAs(instructor, "점수를 고쳤습니다.", "85"));

		Submission saved = reload();
		assertThat(saved.getScore()).isEqualTo(85);
		assertThat(saved.getFeedback()).isEqualTo("피드백");
		assertThat(saved.isGraded()).isTrue();
		assertThat(saved.hasObjectionResponse()).isTrue();
	}

	@Test
	@DisplayName("재채점 점수가 0 미만이거나 배점을 넘으면 거부되고 답변도 점수도 저장되지 않음 (B6)")
	void respond_invalidScore() throws Exception {
		grade();
		raised("이의");

		mockMvc.perform(respondAs(instructor, "답변", "101"))
				.andExpect(flash().attribute("error", "점수는 0 이상 100 이하여야 합니다."));
		mockMvc.perform(respondAs(instructor, "답변", "-1"))
				.andExpect(flash().attribute("error", "점수는 0 이상이어야 합니다."));

		Submission saved = reload();
		assertThat(saved.hasObjectionResponse()).isFalse();
		assertThat(saved.getScore()).isEqualTo(70);

		mockMvc.perform(respondAs(instructor, "만점으로 수정", "100"));
		assertThat(reload().getScore()).isEqualTo(100);
	}

	@Test
	@DisplayName("답변이 비었거나 500자를 넘으면 거부됨")
	void respond_validation() throws Exception {
		grade();
		raised("이의");

		mockMvc.perform(respondAs(instructor, "  ", null)).andExpect(flash().attribute("error", "답변을 입력해 주세요."));
		mockMvc.perform(respondAs(instructor, "가".repeat(501), null))
				.andExpect(flash().attribute("error", "답변은 500자 이하로 입력해 주세요."));

		assertThat(reload().hasObjectionResponse()).isFalse();
	}

	@Test
	@DisplayName("이의 신청이 없거나 이미 답변한 제출에는 답변할 수 없음")
	void respond_state() throws Exception {
		grade();

		mockMvc.perform(respondAs(instructor, "답변", null))
				.andExpect(flash().attribute("error", "이의 신청이 없는 제출입니다."));

		raised("이의");
		mockMvc.perform(respondAs(instructor, "첫 답변", null));
		mockMvc.perform(respondAs(instructor, "두 번째 답변", "10"))
				.andExpect(flash().attribute("error", "이미 답변한 이의 신청입니다."));

		Submission saved = reload();
		assertThat(saved.getObjectionResponse()).isEqualTo("첫 답변");
		assertThat(saved.getScore()).isEqualTo(70);
	}

	@Test
	@DisplayName("다른 강사는 403, 없는 제출은 404 (B5)")
	void respond_ownership() throws Exception {
		grade();
		raised("이의");

		mockMvc.perform(respondAs(otherInstructor, "남의 강좌", "0")).andExpect(status().isForbidden());
		mockMvc.perform(post("/instructor/submissions/999999/objection").param("response", "답변")
				.with(user(principal(instructor))).with(csrf())).andExpect(status().isNotFound());

		Submission saved = reload();
		assertThat(saved.hasObjectionResponse()).isFalse();
		assertThat(saved.getScore()).isEqualTo(70);
	}

	@Test
	@DisplayName("이의 답변 - 학생은 403, 비로그인은 로그인 화면으로, CSRF 토큰이 없으면 거부, GET은 허용되지 않음")
	void respond_security() throws Exception {
		grade();
		raised("이의");

		mockMvc.perform(respondAs(student, "학생이 답변", null)).andExpect(status().isForbidden());
		mockMvc.perform(post(respondUrl()).param("response", "답변").with(csrf()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post(respondUrl()).param("response", "답변").with(user(principal(instructor))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(respondUrl()).with(user(principal(instructor))))
				.andExpect(status().isMethodNotAllowed());

		assertThat(reload().hasObjectionResponse()).isFalse();
	}

	// ---------- 강사 화면 ----------

	@Test
	@DisplayName("채점 화면 - 이의가 접수되면 내용과 답변 폼이 보이고, 답변하면 폼이 사라지며, 신청이 없으면 이의 영역이 없음")
	void gradePage_display() throws Exception {
		grade();
		mockMvc.perform(get(gradeUrl()).with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("objectionCard"))));

		raised("점수 재확인 요청");
		mockMvc.perform(get(gradeUrl()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("점수 재확인 요청")))
				.andExpect(content().string(containsString("접수됨")))
				.andExpect(content().string(containsString("objectionResponseForm")))
				.andExpect(content().string(containsString(respondUrl())));

		submission.answerObjection("확인 완료", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);
		mockMvc.perform(get(gradeUrl()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("확인 완료")))
				.andExpect(content().string(containsString("답변 완료")))
				.andExpect(content().string(not(containsString("objectionResponseForm"))));
	}

	@Test
	@DisplayName("제출 현황 - 이의가 답변 전이면 이의 접수 배지가 보이고, 답변하거나 신청이 없으면 보이지 않음")
	void statusPage_badge() throws Exception {
		String statusUrl = "/instructor/assignments/" + assignment.getId() + "/submissions";
		grade();
		mockMvc.perform(get(statusUrl).with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("이의 접수"))));

		raised("이의");
		mockMvc.perform(get(statusUrl).with(user(principal(instructor))))
				.andExpect(content().string(containsString("이의 접수")));

		submission.answerObjection("답변", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);
		mockMvc.perform(get(statusUrl).with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("이의 접수"))));
	}
}
