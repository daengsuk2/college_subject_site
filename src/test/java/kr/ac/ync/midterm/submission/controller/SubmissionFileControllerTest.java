package kr.ac.ync.midterm.submission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

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
import kr.ac.ync.midterm.support.UploadTestSupport;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 제출 첨부파일 (S3-b): 업로드(정상 · 거부 · 교체), 거부된 요청에서 파일 미저장, 다운로드 권한 · 헤더.
 */
class SubmissionFileControllerTest extends BaseController {

	private static final String CONTENT = "제출 파일 내용입니다";

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
	private Assignment assignment;

	@BeforeEach
	void setUpData() {
		UploadTestSupport.clean();
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
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

	private MockMultipartHttpServletRequestBuilder submitRequest(Long assignmentId, User actor, String text) {
		MockMultipartHttpServletRequestBuilder request = multipart("/student/assignments/" + assignmentId + "/submit");
		request.param("content", text);
		request.with(user(principal(actor))).with(csrf());
		return request;
	}

	private Submission submissionOf(Assignment target, User owner) {
		return submissionRepository.findByAssignmentIdAndStudentId(target.getId(), owner.getId()).orElseThrow();
	}

	// ---------- 업로드 ----------

	@Test
	@DisplayName("POST 제출 - 파일을 첨부하면 저장되고 과제 상세에 원본 이름의 다운로드 링크가 표시됨")
	void submit_withFile() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안").file(pdf("내 답안.pdf", CONTENT)))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));

		Submission saved = submissionOf(assignment, student);
		assertThat(saved.getOriginalFilename()).isEqualTo("내 답안.pdf");
		assertThat(saved.getFilePath()).endsWith(".pdf").doesNotContain("내 답안");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("내 답안.pdf")))
				.andExpect(content().string(containsString("/submissions/" + saved.getId() + "/file")));
	}

	@Test
	@DisplayName("POST 제출 - 첨부 없이 내용만으로도 제출됨")
	void submit_withoutFile() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "내용만"))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));

		assertThat(submissionOf(assignment, student).hasFile()).isFalse();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 허용하지 않는 확장자는 파일 칸에 에러가 표시되고 저장되지 않음")
	void submit_rejectedExtension() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안").file(
						new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1 })))
				.andExpect(status().isOk())
				.andExpect(view().name("student/submission-form"))
				.andExpect(model().attributeHasFieldErrors("submissionRequest", "file"))
				.andExpect(content().string(containsString("허용하지 않는 파일 형식입니다.")));

		assertThat(submissionRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 10MB를 넘는 파일은 거부되고 10MB 정확히는 허용됨")
	void submit_sizeLimit() throws Exception {
		byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안")
						.file(new MockMultipartFile("file", "big.zip", "application/zip", tooBig)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("파일 크기는 10MB 이하여야 합니다.")));
		assertThat(submissionRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();

		byte[] exact = new byte[10 * 1024 * 1024];
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안")
						.file(new MockMultipartFile("file", "exact.zip", "application/zip", exact)))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST 제출 - 파일 칸에 에러가 있어도 기존 첨부 안내와 입력한 내용이 유지됨")
	void submit_rejected_keepsForm() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "첫 제출").file(pdf("first.pdf", CONTENT)));

		mockMvc.perform(submitRequest(assignment.getId(), student, "고친 내용").file(
						new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1 })))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("현재 첨부")))
				.andExpect(content().string(containsString("first.pdf")))
				.andExpect(content().string(containsString("고친 내용")));

		// 거부된 재제출은 기존 제출과 파일을 건드리지 않는다
		assertThat(submissionOf(assignment, student).getContent()).isEqualTo("첫 제출");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST 재제출 - 새 파일을 올리면 교체되고 이전 파일은 삭제됨, 올리지 않으면 유지됨")
	void resubmit_replaceAndKeep() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "첫 제출").file(pdf("first.pdf", "첫 파일")));
		String firstStored = submissionOf(assignment, student).getFilePath();

		// 파일 없이 다시 제출하면 기존 첨부가 유지된다
		mockMvc.perform(submitRequest(assignment.getId(), student, "내용만 수정"));
		assertThat(submissionOf(assignment, student).getFilePath()).isEqualTo(firstStored);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		// 새 파일을 올리면 교체되고 이전 파일은 지워진다
		mockMvc.perform(submitRequest(assignment.getId(), student, "파일도 교체").file(pdf("second.pdf", "둘째 파일")));
		Submission after = submissionOf(assignment, student);
		assertThat(submissionRepository.count()).isEqualTo(1);
		assertThat(after.getOriginalFilename()).isEqualTo("second.pdf");
		assertThat(after.getFilePath()).isNotEqualTo(firstStored);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	// ---------- 거부된 요청에서는 파일을 저장하지 않는다 ----------

	@Test
	@DisplayName("POST 제출 - 제출 기간이 아니면 파일이 서버에 저장되지 않음 (B1)")
	void submit_outsidePeriod_noFileStored() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		Assignment upcoming = assignmentRepository.save(Assignment.builder().course(course).title("예정").content("내용")
				.startAt(now.plusDays(1)).endAt(now.plusDays(2)).maxScore(100).build());
		Assignment closed = assignmentRepository.save(Assignment.builder().course(course).title("마감").content("내용")
				.startAt(now.minusDays(3)).endAt(now.minusDays(2)).maxScore(100).build());

		mockMvc.perform(submitRequest(upcoming.getId(), student, "답안").file(pdf("a.pdf", CONTENT)))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));
		mockMvc.perform(submitRequest(closed.getId(), student, "답안").file(pdf("a.pdf", CONTENT)))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));

		assertThat(submissionRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 수강하지 않는 학생은 403이고 파일이 저장되지 않음 (B2)")
	void submit_notEnrolled_noFileStored() throws Exception {
		User stranger = createUser("stranger@test.com", Role.STUDENT);

		mockMvc.perform(submitRequest(assignment.getId(), stranger, "답안").file(pdf("a.pdf", CONTENT)))
				.andExpect(status().isForbidden());

		assertThat(submissionRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("POST 제출 - 채점 완료된 제출에는 파일이 저장되지 않고 기존 파일이 유지됨 (B4)")
	void submit_graded_noFileStored() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "첫 제출").file(pdf("first.pdf", "첫 파일")));
		Submission saved = submissionOf(assignment, student);
		String storedName = saved.getFilePath();
		ReflectionTestUtils.setField(saved, "status", SubmissionStatus.GRADED);
		submissionRepository.flush();

		mockMvc.perform(submitRequest(assignment.getId(), student, "고치려는 내용").file(pdf("second.pdf", "둘째 파일")))
				.andExpect(flash().attribute("error", "채점 완료된 과제입니다."));

		assertThat(saved.getFilePath()).isEqualTo(storedName);
		assertThat(saved.getOriginalFilename()).isEqualTo("first.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	// ---------- 악의적인 파일명 ----------

	@Test
	@DisplayName("POST 제출 - 경로 조작 파일명(../)이어도 폴더 밖에 저장되지 않고 UUID 이름으로 저장됨")
	void submit_pathTraversalName() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안").file(pdf("../../evil.pdf", CONTENT)))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));

		Submission saved = submissionOf(assignment, student);
		assertThat(saved.getOriginalFilename()).isEqualTo("evil.pdf");
		assertThat(saved.getFilePath()).doesNotContain("..").doesNotContain("/").doesNotContain("evil");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
		assertThat(Files.exists(Path.of("build/evil.pdf").toAbsolutePath())).isFalse();
		assertThat(Files.exists(Path.of("evil.pdf").toAbsolutePath())).isFalse();
	}

	@Test
	@DisplayName("과제 상세 - 파일명에 들어 있는 HTML은 이스케이프되어 표시됨 (XSS)")
	void detail_filenameEscaped() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안")
				.file(pdf("<img src=x onerror=alert(1)>.pdf", CONTENT)));

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<img src=x onerror=alert(1)>"))))
				.andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;.pdf")));
	}

	// ---------- 다운로드 ----------

	private Long submitWithFile() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "답안").file(pdf("내 답안.pdf", CONTENT)));
		return submissionOf(assignment, student).getId();
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 제출한 본인은 내려받을 수 있고 원본(한글) 이름으로 저장되는 헤더가 붙음")
	void download_owner() throws Exception {
		Long id = submitWithFile();

		byte[] body = mockMvc.perform(get("/submissions/{id}/file", id).with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
				.andExpect(header().string("Content-Disposition", not(containsString("내 답안"))))
				.andReturn().getResponse().getContentAsByteArray();

		assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 해당 강좌의 강사는 내려받을 수 있음 (B5)")
	void download_courseInstructor() throws Exception {
		Long id = submitWithFile();

		mockMvc.perform(get("/submissions/{id}/file", id).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().bytes(CONTENT.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 같은 강좌를 듣는 다른 학생은 403이고 내용이 노출되지 않음")
	void download_otherStudent() throws Exception {
		Long id = submitWithFile();
		User other = createUser("other@test.com", Role.STUDENT);
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());

		mockMvc.perform(get("/submissions/{id}/file", id).with(user(principal(other))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"))
				.andExpect(content().string(not(containsString(CONTENT))));
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 다른 강사는 403 (B5)")
	void download_otherInstructor() throws Exception {
		Long id = submitWithFile();
		User otherInstructor = createUser("other-teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/submissions/{id}/file", id).with(user(principal(otherInstructor))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 비로그인은 로그인 화면으로 이동")
	void download_anonymous() throws Exception {
		Long id = submitWithFile();

		mockMvc.perform(get("/submissions/{id}/file", id))
				.andExpect(status().is3xxRedirection())
				.andExpect(content().string(not(containsString(CONTENT))));
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 첨부가 없는 제출과 없는 제출은 404")
	void download_notFound() throws Exception {
		mockMvc.perform(submitRequest(assignment.getId(), student, "내용만"));
		Long noFileId = submissionOf(assignment, student).getId();

		mockMvc.perform(get("/submissions/{id}/file", noFileId).with(user(principal(student))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/submissions/{id}/file", 999999L).with(user(principal(student))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /submissions/{id}/file - 서버에서 파일이 사라졌으면 404")
	void download_fileMissingOnDisk() throws Exception {
		Long id = submitWithFile();
		try (Stream<Path> files = Files.walk(UploadTestSupport.TEST_UPLOAD_DIR.toAbsolutePath())) {
			files.filter(Files::isRegularFile).forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}

		mockMvc.perform(get("/submissions/{id}/file", id).with(user(principal(student))))
				.andExpect(status().isNotFound());
	}
}
