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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

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
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 강사의 제출 현황 화면 (I4): 표시, 권한(B5), 비정상 접근, XSS, 과제 삭제 시 B8 안내.
 */
class InstructorSubmissionControllerTest extends BaseController {

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

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
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

	private Submission submit(User s, String fileName) {
		Submission submission = Submission.builder().assignment(assignment).student(s).content("답안")
				.submittedAt(LocalDateTime.now()).build();
		if (fileName != null) {
			submission.attachFile("stored-" + s.getId() + ".pdf", fileName);
		}
		return submissionRepository.save(submission);
	}

	private String url() {
		return "/instructor/assignments/" + assignment.getId() + "/submissions";
	}

	@Test
	@DisplayName("GET 제출 현황 - 수강생 3명 중 2명 제출이면 요약과 두 표가 표시됨")
	void status_shown() throws Exception {
		User a = enrolled("a@test.com", "20260001", "김제출");
		User b = enrolled("b@test.com", "20260002", "이제출");
		enrolled("c@test.com", "20260003", "박미제출");
		submit(a, null);
		Submission withFile = submit(b, "답안.pdf");

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/submissions"))
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(containsString("김제출")))
				.andExpect(content().string(containsString("이제출")))
				.andExpect(content().string(containsString("박미제출")))
				.andExpect(content().string(containsString("20260003")))
				.andExpect(content().string(containsString("3명")))
				.andExpect(content().string(containsString("2명")))
				.andExpect(content().string(containsString("1명")))
				.andExpect(content().string(containsString("제출함")))
				.andExpect(content().string(containsString("답안.pdf")))
				.andExpect(content().string(containsString("/submissions/" + withFile.getId() + "/file")));
	}

	@Test
	@DisplayName("GET 제출 현황 - 채점 완료된 제출은 점수와 배지가 표시됨")
	void status_graded() throws Exception {
		User a = enrolled("a@test.com", "20260001", "김제출");
		Submission graded = submit(a, null);
		ReflectionTestUtils.setField(graded, "status", SubmissionStatus.GRADED);
		ReflectionTestUtils.setField(graded, "score", 85);

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("채점 완료")))
				.andExpect(content().string(containsString("85 / 100")));
	}

	@Test
	@DisplayName("GET 제출 현황 - 아무도 제출하지 않았거나 모두 제출했을 때 안내 문구가 표시됨")
	void status_emptyMessages() throws Exception {
		User a = enrolled("a@test.com", "20260001", "김제출");

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("아직 제출한 학생이 없습니다.")))
				.andExpect(content().string(not(containsString("모든 수강생이 제출했습니다."))));

		submit(a, null);
		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("모든 수강생이 제출했습니다.")))
				.andExpect(content().string(not(containsString("아직 제출한 학생이 없습니다."))));
	}

	@Test
	@DisplayName("GET 제출 현황 - 수강생이 없으면 안내 문구가 표시됨")
	void status_noStudents() throws Exception {
		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("수강생이 없습니다.")));
	}

	@Test
	@DisplayName("GET 제출 현황 - 수강생이 아닌 학생의 제출은 화면에 나오지 않음")
	void status_ignoresOutsider() throws Exception {
		enrolled("a@test.com", "20260001", "김수강");
		User outsider = userRepository.save(User.builder().email("out@test.com").password("encodedPassword")
				.name("외부인").studentNo("20269999").role(Role.STUDENT).build());
		submit(outsider, null);

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("외부인"))))
				.andExpect(content().string(containsString("김수강")));
	}

	@Test
	@DisplayName("GET 제출 현황 - 다른 강사는 403 (B5)")
	void status_otherInstructor() throws Exception {
		User other = createUser("other-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get(url()).with(user(principal(other))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"));
	}

	@Test
	@DisplayName("GET 제출 현황 - 학생은 403, 비로그인은 로그인 화면으로 이동")
	void status_studentAndAnonymous() throws Exception {
		mockMvc.perform(get(url()).with(user(principal(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(url()))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@DisplayName("GET 제출 현황 - 없는 과제는 404, 숫자가 아닌 주소는 400")
	void status_invalidIds() throws Exception {
		mockMvc.perform(get("/instructor/assignments/999999/submissions").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/instructor/assignments/abc/submissions").with(user(principal(instructor))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("GET 제출 현황 - 이름 · 학번 · 파일명의 HTML은 이스케이프되어 표시됨 (XSS)")
	void status_xssEscaped() throws Exception {
		User a = enrolled("a@test.com", "<b>1</b>", "<script>alert(1)</script>");
		submit(a, "<img src=x onerror=alert(2)>.pdf");

		mockMvc.perform(get(url()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(2)>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("GET 강좌 상세 - 과제마다 제출 현황 링크가 표시됨")
	void courseDetail_hasLink() throws Exception {
		mockMvc.perform(get("/instructor/courses/" + course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(url())));
	}

	@Test
	@DisplayName("POST 과제 삭제 - 제출이 있으면 '제출물이 있어 삭제 불가' 안내가 나오고 과제가 남음 (B8)")
	void deleteAssignment_withSubmission() throws Exception {
		submit(enrolled("a@test.com", "20260001", "김제출"), null);

		mockMvc.perform(post("/instructor/assignments/" + assignment.getId() + "/delete")
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
				.andExpect(flash().attribute("error", "제출물이 있어 삭제 불가"));

		assertThat(assignmentRepository.findById(assignment.getId())).isPresent();
		assertThat(submissionRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST 과제 삭제 - 제출이 없으면 삭제됨 (B8의 반대 경우)")
	void deleteAssignment_withoutSubmission() throws Exception {
		mockMvc.perform(post("/instructor/assignments/" + assignment.getId() + "/delete")
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(flash().attribute("message", "과제가 삭제되었습니다."));

		assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
	}
}
