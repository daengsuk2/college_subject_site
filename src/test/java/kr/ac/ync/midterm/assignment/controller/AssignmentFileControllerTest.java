package kr.ac.ync.midterm.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.assignment.service.AssignmentService;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.support.UploadTestSupport;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 첨부파일 다운로드 (GET /assignments/{id}/file): 권한, 응답 헤더, 비정상 입력.
 */
class AssignmentFileControllerTest extends BaseController {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 9, 0);
	private static final String CONTENT = "첨부 파일 내용입니다";

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private AssignmentService assignmentService;

	private User instructor;
	private Course course;
	private Assignment withFile;

	@BeforeEach
	void setUpData() {
		UploadTestSupport.clean();
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());

		AssignmentRequest request = new AssignmentRequest();
		request.setTitle("1주차");
		request.setContent("내용");
		request.setStartAt(START);
		request.setEndAt(START.plusDays(7));
		request.setMaxScore(100);
		request.setFile(new MockMultipartFile("file", "과제안내.pdf", "application/pdf",
				CONTENT.getBytes(StandardCharsets.UTF_8)));
		Long id = assignmentService.create(instructor.getId(), course.getId(), request).id();
		withFile = assignmentRepository.findById(id).orElseThrow();
	}

	@AfterEach
	void tearDown() {
		UploadTestSupport.clean();
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 강사는 내려받을 수 있고 원본(한글) 이름으로 저장되는 헤더가 붙음")
	void download_owner() throws Exception {
		byte[] body = mockMvc.perform(get("/assignments/{id}/file", withFile.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
				.andExpect(header().string("Content-Disposition", not(containsString("과제안내"))))
				.andReturn().getResponse().getContentAsByteArray();

		assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 수강 등록한 학생은 내려받을 수 있음 (B2)")
	void download_enrolledStudent() throws Exception {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());

		mockMvc.perform(get("/assignments/{id}/file", withFile.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().bytes(CONTENT.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 수강 등록하지 않은 학생은 403 화면이고 내용이 노출되지 않음 (B2)")
	void download_notEnrolledStudent() throws Exception {
		mockMvc.perform(get("/assignments/{id}/file", withFile.getId()).with(user(principal(student))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"))
				.andExpect(content().string(not(containsString(CONTENT))));
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 다른 강사는 403 (B5)")
	void download_otherInstructor() throws Exception {
		User other = createUser("other@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/assignments/{id}/file", withFile.getId()).with(user(principal(other))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 로그인하지 않으면 로그인 페이지로 이동")
	void download_anonymous() throws Exception {
		mockMvc.perform(get("/assignments/{id}/file", withFile.getId()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 첨부가 없는 과제 · 없는 과제는 404")
	void download_notFound() throws Exception {
		Assignment withoutFile = assignmentRepository.save(Assignment.builder().course(course).title("첨부 없음")
				.content("내용").startAt(START).endAt(START.plusDays(7)).maxScore(10).build());

		mockMvc.perform(get("/assignments/{id}/file", withoutFile.getId()).with(user(principal(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/assignments/{id}/file", 999_999L).with(user(principal(instructor))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 번호가 숫자가 아니거나 비정상이면 400 · 404 (경로 조작 시도)")
	void download_tamperedId() throws Exception {
		mockMvc.perform(get("/assignments/abc/file").with(user(principal(instructor))))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/assignments/../../etc/passwd/file").with(user(principal(instructor))))
				.andExpect(status().is4xxClientError());
		mockMvc.perform(get("/assignments/-1/file").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /assignments/{id}/file - 항상 내려받기(octet-stream)로 응답하고 MIME 스니핑 방지 헤더가 있음")
	void download_alwaysAttachment() throws Exception {
		mockMvc.perform(get("/assignments/{id}/file", withFile.getId()).with(user(principal(instructor))))
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}
}
