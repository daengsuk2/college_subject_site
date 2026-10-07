package kr.ac.ync.midterm.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
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
 * 과제 폼의 첨부파일 업로드 (I3-b): 정상 업로드, 거부(확장자 · 크기), 교체, 화면 표시, 악의적인 파일명.
 */
class InstructorAssignmentFileUploadTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	private User instructor;
	private Course course;

	@BeforeEach
	void setUpData() {
		UploadTestSupport.clean();
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
	}

	@AfterEach
	void tearDown() {
		UploadTestSupport.clean();
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private MockMultipartFile pdf(String name, String content) {
		return new MockMultipartFile("file", name, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
	}

	// 유효한 과제 폼 값을 채운 multipart POST 요청
	private MockMultipartHttpServletRequestBuilder form(MockMultipartHttpServletRequestBuilder request, User actor) {
		request.param("title", "1주차 과제")
				.param("content", "과제 내용")
				.param("startAt", "2026-10-10T09:00")
				.param("endAt", "2026-10-17T23:59")
				.param("maxScore", "100");
		request.with(user(principal(actor))).with(csrf());
		return request;
	}

	private MockMultipartHttpServletRequestBuilder createRequest() {
		MockMultipartHttpServletRequestBuilder request = multipart("/instructor/assignments/new");
		request.param("courseId", course.getId().toString());
		return request;
	}

	private Assignment savedAssignment() {
		return assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(LocalDateTime.of(2026, 10, 10, 9, 0)).endAt(LocalDateTime.of(2026, 10, 17, 23, 59))
				.maxScore(100).build());
	}

	// ---------- 등록 ----------

	@Test
	@DisplayName("POST 등록 - 파일을 첨부하면 저장되고 강좌 상세에 원본 이름의 다운로드 링크가 표시됨")
	void create_withFile() throws Exception {
		mockMvc.perform(form(createRequest().file(pdf("과제안내.pdf", "내용")), instructor))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses/" + course.getId()));

		Assignment saved = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();
		assertThat(saved.getOriginalFilename()).isEqualTo("과제안내.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("과제안내.pdf")))
				.andExpect(content().string(containsString("/assignments/" + saved.getId() + "/file")));
	}

	@Test
	@DisplayName("POST 등록 - 첨부 없이도 등록됨")
	void create_withoutFile() throws Exception {
		mockMvc.perform(form(createRequest(), instructor))
				.andExpect(status().is3xxRedirection());

		assertThat(assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId())).hasSize(1);
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 등록 - 허용하지 않는 확장자는 파일 칸에 에러가 표시되고 저장되지 않음")
	void create_rejectedExtension() throws Exception {
		mockMvc.perform(form(createRequest().file(
						new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1 })), instructor))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/assignment-form"))
				.andExpect(model().attributeHasFieldErrors("assignmentRequest", "file"))
				.andExpect(content().string(containsString("허용하지 않는 파일 형식입니다.")));

		assertThat(assignmentRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 등록 - 10MB를 넘는 파일은 파일 칸에 에러가 표시되고 저장되지 않음")
	void create_tooLarge() throws Exception {
		byte[] tooBig = new byte[10 * 1024 * 1024 + 1];

		mockMvc.perform(form(createRequest().file(
						new MockMultipartFile("file", "big.zip", "application/zip", tooBig)), instructor))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("assignmentRequest", "file"))
				.andExpect(content().string(containsString("10MB 이하")));

		assertThat(assignmentRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 등록 - 경로 조작 파일명(../../evil.pdf)도 UUID 이름으로 지정 폴더 안에만 저장됨")
	void create_pathTraversalFileName() throws Exception {
		mockMvc.perform(form(createRequest().file(pdf("../../evil.pdf", "내용")), instructor))
				.andExpect(status().is3xxRedirection());

		Assignment saved = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();
		assertThat(saved.getOriginalFilename()).isEqualTo("evil.pdf");
		assertThat(saved.getFilePath()).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST 등록 - 다른 강사의 강좌에는 파일도 과제도 저장되지 않음 (B5)")
	void create_otherInstructor() throws Exception {
		User other = createUser("other@test.com", Role.INSTRUCTOR);

		mockMvc.perform(form(createRequest().file(pdf("a.pdf", "x")), other))
				.andExpect(status().isForbidden());

		assertThat(assignmentRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 등록 - CSRF 토큰이 없으면 403 (파일 저장 안 됨)")
	void create_withoutCsrf() throws Exception {
		mockMvc.perform(createRequest().file(pdf("a.pdf", "x"))
						.param("title", "과제").param("content", "내용")
						.param("startAt", "2026-10-10T09:00").param("endAt", "2026-10-17T23:59")
						.param("maxScore", "100")
						.with(user(principal(instructor))))
				.andExpect(status().isForbidden());

		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	// ---------- 수정 ----------

	@Test
	@DisplayName("POST 수정 - 새 파일을 올리면 교체되고, 올리지 않으면 유지됨")
	void update_replaceAndKeep() throws Exception {
		// given: 파일이 있는 과제
		mockMvc.perform(form(createRequest().file(pdf("old.pdf", "옛날")), instructor));
		Assignment assignment = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();
		String oldStored = assignment.getFilePath();

		// when 1: 파일 없이 수정 → 유지
		mockMvc.perform(form(multipart("/instructor/assignments/{id}/edit", assignment.getId()), instructor))
				.andExpect(status().is3xxRedirection());
		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getFilePath()).isEqualTo(oldStored);

		// when 2: 새 파일로 수정 → 교체
		mockMvc.perform(form(multipart("/instructor/assignments/{id}/edit", assignment.getId())
						.file(pdf("new.pdf", "새로운")), instructor))
				.andExpect(status().is3xxRedirection());

		Assignment saved = assignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(saved.getOriginalFilename()).isEqualTo("new.pdf");
		assertThat(saved.getFilePath()).isNotEqualTo(oldStored);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST 수정 - 허용하지 않는 새 파일은 거부되고 기존 첨부가 남음")
	void update_rejectedKeepsOld() throws Exception {
		mockMvc.perform(form(createRequest().file(pdf("old.pdf", "옛날")), instructor));
		Assignment assignment = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();

		mockMvc.perform(form(multipart("/instructor/assignments/{id}/edit", assignment.getId())
						.file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1 })),
						instructor))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("assignmentRequest", "file"))
				.andExpect(content().string(containsString("old.pdf")));

		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getOriginalFilename())
				.isEqualTo("old.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("GET 수정 폼 - 현재 첨부 파일 이름과 다운로드 링크가 표시됨")
	void editForm_showsCurrentFile() throws Exception {
		mockMvc.perform(form(createRequest().file(pdf("과제안내.pdf", "내용")), instructor));
		Assignment assignment = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();

		mockMvc.perform(get("/instructor/assignments/{id}/edit", assignment.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("현재 첨부")))
				.andExpect(content().string(containsString("과제안내.pdf")))
				.andExpect(content().string(containsString("multipart/form-data")));
	}

	// ---------- 삭제 ----------

	@Test
	@DisplayName("POST 삭제 - 과제를 삭제하면 첨부 파일도 삭제됨")
	void delete_removesFile() throws Exception {
		mockMvc.perform(form(createRequest().file(pdf("a.pdf", "x")), instructor));
		Assignment assignment = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		mockMvc.perform(multipart("/instructor/assignments/{id}/delete", assignment.getId())
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	// ---------- 화면 표시와 XSS ----------

	@Test
	@DisplayName("학생 과제 상세 - 첨부 파일 다운로드 링크가 표시되고, 없으면 '없음'")
	void studentDetail_showsFileLink() throws Exception {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		mockMvc.perform(form(createRequest().file(pdf("과제안내.pdf", "내용")), instructor));
		Assignment withFile = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();
		Assignment withoutFile = savedAssignment();

		mockMvc.perform(get("/student/assignments/{id}", withFile.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("과제안내.pdf")))
				.andExpect(content().string(containsString("/assignments/" + withFile.getId() + "/file")));

		mockMvc.perform(get("/student/assignments/{id}", withoutFile.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("없음")))
				.andExpect(content().string(not(containsString("/assignments/" + withoutFile.getId() + "/file"))));
	}

	@Test
	@DisplayName("XSS - 파일명에 태그가 있어도 강사 · 학생 화면에 이스케이프되어 출력됨")
	void xss_fileName() throws Exception {
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		String evil = "<img src=x onerror=alert(1)>.pdf";
		mockMvc.perform(form(createRequest().file(pdf(evil, "내용")), instructor))
				.andExpect(status().is3xxRedirection());
		Assignment saved = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();

		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;.pdf")))
				.andExpect(content().string(not(containsString("<img src=x onerror"))));

		mockMvc.perform(get("/student/assignments/{id}", saved.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;.pdf")))
				.andExpect(content().string(not(containsString("<img src=x onerror"))));
	}
}
