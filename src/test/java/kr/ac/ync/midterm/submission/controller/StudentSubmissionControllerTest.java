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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
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

class StudentSubmissionControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private Course course;

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Course course() {
		if (course == null) {
			User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
			course = courseRepository.save(
					Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
			enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		}
		return course;
	}

	private Assignment assignment(LocalDateTime startAt, LocalDateTime endAt) {
		return assignmentRepository.save(Assignment.builder().course(course()).title("1주차 과제").content("내용")
				.startAt(startAt).endAt(endAt).maxScore(100).build());
	}

	private Assignment openAssignment() {
		LocalDateTime now = LocalDateTime.now();
		return assignment(now.minusDays(1), now.plusDays(1));
	}

	private MockHttpServletRequestBuilder submitAs(User actor, Long assignmentId, String content) {
		return post("/student/assignments/" + assignmentId + "/submit")
				.param("content", content)
				.with(user(principal(actor))).with(csrf());
	}

	@Test
	@DisplayName("GET 제출 폼 - 진행중이면 폼 표시")
	void form_open() throws Exception {
		Assignment assignment = openAssignment();

		mockMvc.perform(get("/student/assignments/" + assignment.getId() + "/submit").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("student/submission-form"))
				.andExpect(content().string(containsString("1주차 과제")));
	}

	@Test
	@DisplayName("GET 제출 폼 - 재제출이면 기존 내용이 채워짐")
	void form_resubmitPrefilled() throws Exception {
		Assignment assignment = openAssignment();
		submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content("이전에 낸 내용").submittedAt(LocalDateTime.now()).build());

		mockMvc.perform(get("/student/assignments/" + assignment.getId() + "/submit").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("이전에 낸 내용")))
				.andExpect(content().string(containsString("다시 제출")));
	}

	@Test
	@DisplayName("GET 제출 폼 - 기간이 아니면 상세로 돌려보내고 안내 (B1)")
	void form_closed() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		Assignment upcoming = assignment(now.plusDays(1), now.plusDays(2));
		Assignment closed = assignment(now.minusDays(3), now.minusDays(2));

		mockMvc.perform(get("/student/assignments/" + upcoming.getId() + "/submit").with(user(principal(student))))
				.andExpect(redirectedUrl("/student/assignments/" + upcoming.getId()))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));
		mockMvc.perform(get("/student/assignments/" + closed.getId() + "/submit").with(user(principal(student))))
				.andExpect(redirectedUrl("/student/assignments/" + closed.getId()))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));
	}

	@Test
	@DisplayName("GET 제출 폼 - 수강하지 않는 강좌의 과제는 403 (B2)")
	void form_notEnrolled() throws Exception {
		Assignment assignment = openAssignment();
		User stranger = createUser("stranger@test.com", Role.STUDENT);

		mockMvc.perform(get("/student/assignments/" + assignment.getId() + "/submit").with(user(principal(stranger))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("POST 제출 - 진행중이면 저장하고 상세로 이동")
	void submit_ok() throws Exception {
		Assignment assignment = openAssignment();

		mockMvc.perform(submitAs(student, assignment.getId(), "제 답안입니다"))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()))
				.andExpect(flash().attribute("message", "제출했습니다."));

		assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId()))
				.get().extracting(Submission::getContent).isEqualTo("제 답안입니다");
	}

	@Test
	@DisplayName("POST 제출 - 다시 제출하면 1건이 유지되고 내용이 바뀜 (B3)")
	void submit_twice() throws Exception {
		Assignment assignment = openAssignment();

		mockMvc.perform(submitAs(student, assignment.getId(), "처음"));
		mockMvc.perform(submitAs(student, assignment.getId(), "다시"))
				.andExpect(flash().attribute("message", "다시 제출했습니다."));

		assertThat(submissionRepository.count()).isEqualTo(1);
		assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId()))
				.get().extracting(Submission::getContent).isEqualTo("다시");
	}

	@Test
	@DisplayName("POST 제출 - 화면 버튼과 무관하게 기간 밖 요청은 서버가 거부함 (B1)")
	void submit_outsidePeriod_forced() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		Assignment upcoming = assignment(now.plusDays(1), now.plusDays(2));
		Assignment closed = assignment(now.minusDays(3), now.minusDays(2));

		mockMvc.perform(submitAs(student, upcoming.getId(), "강제 제출"))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));
		mockMvc.perform(submitAs(student, closed.getId(), "강제 제출"))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));

		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 수강하지 않는 학생은 403이고 저장되지 않음 (B2)")
	void submit_notEnrolled() throws Exception {
		Assignment assignment = openAssignment();
		User stranger = createUser("stranger@test.com", Role.STUDENT);

		mockMvc.perform(submitAs(stranger, assignment.getId(), "몰래 제출"))
				.andExpect(status().isForbidden());

		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 채점 완료된 제출은 재제출 거부 (B4)")
	void submit_graded() throws Exception {
		Assignment assignment = openAssignment();
		Submission graded = submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content("채점된 내용").submittedAt(LocalDateTime.now()).build());
		ReflectionTestUtils.setField(graded, "status", SubmissionStatus.GRADED);
		submissionRepository.flush();

		mockMvc.perform(submitAs(student, assignment.getId(), "고치려는 내용"))
				.andExpect(flash().attribute("error", "채점 완료된 과제입니다."));

		assertThat(graded.getContent()).isEqualTo("채점된 내용");
	}

	@Test
	@DisplayName("POST 제출 - 빈 내용은 에러 메시지와 함께 폼을 다시 보여 주고 저장하지 않음")
	void submit_blank() throws Exception {
		Assignment assignment = openAssignment();

		mockMvc.perform(submitAs(student, assignment.getId(), "   "))
				.andExpect(status().isOk())
				.andExpect(view().name("student/submission-form"))
				.andExpect(content().string(containsString("제출 내용을 입력해 주세요.")));

		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 제출 내용의 HTML은 이스케이프되어 표시됨 (XSS)")
	void submit_xssEscaped() throws Exception {
		Assignment assignment = openAssignment();
		mockMvc.perform(submitAs(student, assignment.getId(), "<script>alert(1)</script>"));

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("POST 제출 - 강사 계정은 403, CSRF 토큰이 없으면 거부, 비로그인은 로그인으로")
	void submit_security() throws Exception {
		Assignment assignment = openAssignment();
		User instructor = createUser("other-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(submitAs(instructor, assignment.getId(), "강사가 제출"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/student/assignments/" + assignment.getId() + "/submit").param("content", "토큰 없음")
						.with(user(principal(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/student/assignments/" + assignment.getId() + "/submit").param("content", "비로그인")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("과제 상세 - 상태에 맞는 제출 버튼 · 안내가 표시됨")
	void detail_buttons() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		Assignment open = openAssignment();
		Assignment upcoming = assignment(now.plusDays(1), now.plusDays(2));
		Assignment closed = assignment(now.minusDays(3), now.minusDays(2));

		mockMvc.perform(get("/student/assignments/" + open.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("아직 제출하지 않았습니다.")))
				.andExpect(content().string(containsString("제출하기")));
		mockMvc.perform(get("/student/assignments/" + upcoming.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("아직 제출 기간이 아닙니다.")))
				.andExpect(content().string(not(containsString("제출하기"))));
		mockMvc.perform(get("/student/assignments/" + closed.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("제출 기간이 지났습니다.")))
				.andExpect(content().string(not(containsString("제출하기"))));
	}
}
