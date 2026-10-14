package kr.ac.ync.midterm.submission.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import kr.ac.ync.midterm.submission.dto.request.GradeRequest;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.submission.service.GradingService;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 학생의 결과 확인 (S4): 내 제출 목록, 과제 상세의 채점 결과, 본인 것만 보임, 재채점 반영, XSS.
 */
class StudentMySubmissionControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private GradingService gradingService;

	private User instructor;
	private Course course;
	private Assignment assignment;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		assignment = assignment("1주차 과제", 100);
	}

	private Assignment assignment(String title, int maxScore) {
		LocalDateTime now = LocalDateTime.now();
		return assignmentRepository.save(Assignment.builder().course(course).title(title).content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(maxScore).build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Submission submit(Assignment target, User owner, String text, LocalDateTime at) {
		return submissionRepository.save(Submission.builder().assignment(target).student(owner).content(text)
				.submittedAt(at).build());
	}

	private void grade(Submission submission, int score, String feedback) {
		GradeRequest request = new GradeRequest();
		request.setScore(score);
		request.setFeedback(feedback);
		gradingService.grade(instructor.getId(), submission.getId(), request);
	}

	private User otherEnrolledStudent() {
		User other = createUser("other@test.com", Role.STUDENT);
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());
		return other;
	}

	@Test
	@DisplayName("GET 내 제출 목록 - 채점 전에는 '채점 대기', 채점 후에는 점수 / 배점이 표시됨")
	void list_beforeAndAfterGrading() throws Exception {
		Submission submission = submit(assignment, student, "답안", LocalDateTime.now());

		mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("student/submissions"))
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(containsString("채점 대기")))
				.andExpect(content().string(not(containsString("채점 완료"))))
				.andExpect(content().string(not(containsString(" / 100"))));

		grade(submission, 92, "잘했어요");

		mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(content().string(containsString("채점 완료")))
				.andExpect(content().string(containsString("92 / 100")))
				.andExpect(content().string(not(containsString("채점 대기"))));
	}

	@Test
	@DisplayName("GET 내 제출 목록 - 내가 제출한 과제만 최근 제출순으로 나오고 미제출 과제는 나오지 않음")
	void list_onlyMineNewestFirst() throws Exception {
		Assignment second = assignment("2주차 과제", 50);
		assignment("3주차 미제출 과제", 100);
		submit(assignment, student, "첫째", LocalDateTime.now().minusHours(5));
		submit(second, student, "둘째", LocalDateTime.now().minusHours(1));

		String html = mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("3주차 미제출 과제"))))
				.andReturn().getResponse().getContentAsString();

		assertThat(html.indexOf("2주차 과제")).isPositive().isLessThan(html.indexOf("1주차 과제"));
	}

	@Test
	@DisplayName("GET 내 제출 목록 - 다른 학생의 제출과 점수는 보이지 않음")
	void list_notOthers() throws Exception {
		User other = otherEnrolledStudent();
		Assignment othersOnly = assignment("다른 학생만 낸 과제", 100);
		Submission othersSubmission = submit(othersOnly, other, "남의 답안", LocalDateTime.now());
		grade(othersSubmission, 77, "남의 피드백");
		submit(assignment, student, "내 답안", LocalDateTime.now());

		mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(not(containsString("다른 학생만 낸 과제"))))
				.andExpect(content().string(not(containsString("77 / 100"))));
	}

	@Test
	@DisplayName("GET 내 제출 목록 - 제출이 없으면 안내 문구와 내 과제 링크가 표시됨")
	void list_empty() throws Exception {
		mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 제출한 과제가 없습니다.")))
				.andExpect(content().string(containsString("/student/assignments")));
	}

	@Test
	@DisplayName("GET 내 제출 목록 - 강사는 403, 비로그인은 로그인 화면으로 이동")
	void list_security() throws Exception {
		mockMvc.perform(get("/student/submissions").with(user(principal(instructor))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/student/submissions"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@DisplayName("GET 과제 상세 - 채점 전에는 점수 · 피드백이 보이지 않고 '채점 대기'가 표시됨")
	void detail_beforeGrading() throws Exception {
		submit(assignment, student, "답안", LocalDateTime.now());

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("채점 대기")))
				.andExpect(content().string(not(containsString("gradeResult"))))
				.andExpect(content().string(not(containsString("피드백이 없습니다."))));
	}

	@Test
	@DisplayName("GET 과제 상세 - 채점 후에는 점수와 피드백이 표시됨")
	void detail_afterGrading() throws Exception {
		Submission submission = submit(assignment, student, "답안", LocalDateTime.now());
		grade(submission, 88, "구조는 좋은데 예외 처리를 더 해 보세요.");

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("채점 완료")))
				.andExpect(content().string(containsString("88 / 100")))
				.andExpect(content().string(containsString("구조는 좋은데 예외 처리를 더 해 보세요.")));
	}

	@Test
	@DisplayName("GET 과제 상세 - 피드백 없이 채점하면 '피드백이 없습니다.'가 표시됨")
	void detail_noFeedback() throws Exception {
		Submission submission = submit(assignment, student, "답안", LocalDateTime.now());
		grade(submission, 60, null);

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("60 / 100")))
				.andExpect(content().string(containsString("피드백이 없습니다.")));
	}

	@Test
	@DisplayName("재채점하면 학생 화면의 목록과 상세에도 바뀐 점수가 보임")
	void regradeReflected() throws Exception {
		Submission submission = submit(assignment, student, "답안", LocalDateTime.now());
		grade(submission, 60, "첫 채점");
		grade(submission, 95, "다시 채점");

		mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(content().string(containsString("95 / 100")))
				.andExpect(content().string(not(containsString("60 / 100"))));
		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("95 / 100")))
				.andExpect(content().string(containsString("다시 채점")))
				.andExpect(content().string(not(containsString("첫 채점"))));
	}

	@Test
	@DisplayName("같은 과제를 듣는 다른 학생은 내 점수와 피드백을 볼 수 없고 자기 제출만 봄")
	void detail_otherStudentSeesOnlyOwn() throws Exception {
		User other = otherEnrolledStudent();
		Submission mine = submit(assignment, student, "내 답안", LocalDateTime.now());
		grade(mine, 99, "내 비밀 피드백");

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(other))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("아직 제출하지 않았습니다.")))
				.andExpect(content().string(not(containsString("99 / 100"))))
				.andExpect(content().string(not(containsString("내 비밀 피드백"))))
				.andExpect(content().string(not(containsString("내 답안"))));
	}

	@Test
	@DisplayName("수강하지 않는 학생은 과제 상세도 403이라 점수를 볼 수 없음 (B2)")
	void detail_notEnrolled() throws Exception {
		Submission mine = submit(assignment, student, "내 답안", LocalDateTime.now());
		grade(mine, 99, "내 비밀 피드백");
		User stranger = createUser("stranger@test.com", Role.STUDENT);

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(stranger))))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("내 비밀 피드백"))));
	}

	@Test
	@DisplayName("과제 상세 - 피드백의 HTML은 이스케이프되어 표시됨 (XSS)")
	void detail_feedbackEscaped() throws Exception {
		Submission submission = submit(assignment, student, "답안", LocalDateTime.now());
		grade(submission, 70, "<script>alert(1)</script>");

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("사이드바의 '내 제출 · 점수' 메뉴가 이 화면 주소로 연결됨")
	void sidebarLink() throws Exception {
		mockMvc.perform(get("/student/assignments").with(user(principal(student))))
				.andExpect(content().string(containsString("/student/submissions")));
		mockMvc.perform(get("/student/submissions").with(user(principal(student))))
				.andExpect(status().isOk());
	}
}
