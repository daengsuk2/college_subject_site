package kr.ac.ync.midterm.attachment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import kr.ac.ync.midterm.attachment.domain.Attachment;
import kr.ac.ync.midterm.attachment.repository.AttachmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.global.storage.StoredFile;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.support.UploadTestSupport;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 여러 파일 첨부 (X17): 과제 · 제출에 추가 첨부, 개수 제한, 다운로드와 삭제 권한(B1 · B2 · B4 · B5), 화면 표시, XSS.
 * 커밋 뒤에 일어나는 파일 삭제와 롤백 정리는 MultiAttachmentConsistencyTest에서 확인한다.
 */
class MultiAttachmentControllerTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private AttachmentRepository attachmentRepository;

	@Autowired
	private FileStorage fileStorage;

	private User instructor;
	private User otherInstructor;
	private User otherStudent;
	private Course course;
	private Assignment assignment;

	@BeforeEach
	void setUpData() {
		UploadTestSupport.clean();
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		otherInstructor = createUser("other-teacher@test.com", Role.INSTRUCTOR);
		otherStudent = createUser("other-student@test.com", Role.STUDENT);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("MULTI1").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(otherStudent).build());
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

	private MockMultipartFile file(String part, String name, String content) {
		return new MockMultipartFile(part, name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
	}

	private MockMultipartFile extra(String name) {
		return file("extraFiles", name, "내용 " + name);
	}

	private MockMultipartHttpServletRequestBuilder assignmentForm(String url, User actor) {
		MockMultipartHttpServletRequestBuilder request = multipart(url);
		request.param("title", "2주차 과제").param("content", "과제 내용")
				.param("startAt", "2026-10-10T09:00").param("endAt", "2026-10-17T23:59").param("maxScore", "100");
		request.with(user(principal(actor))).with(csrf());
		return request;
	}

	private MockMultipartHttpServletRequestBuilder submitForm(User actor) {
		MockMultipartHttpServletRequestBuilder request = multipart("/student/assignments/" + assignment.getId() + "/submit");
		request.param("content", "내 답안");
		request.with(user(principal(actor))).with(csrf());
		return request;
	}

	private Attachment assignmentAttachment(String name) {
		StoredFile stored = fileStorage.store(file("extraFiles", name, "과제 첨부 " + name));
		return attachmentRepository.save(Attachment.ofAssignment(assignment, stored.storedName(), stored.originalName()));
	}

	private Submission submissionOf(User owner) {
		return submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), owner.getId())
				.orElseGet(() -> submissionRepository.save(Submission.builder().assignment(assignment).student(owner)
						.content("답안").submittedAt(LocalDateTime.now().minusHours(1)).build()));
	}

	private Attachment submissionAttachment(Submission submission, String name) {
		StoredFile stored = fileStorage.store(file("extraFiles", name, "제출 첨부 " + name));
		return attachmentRepository.save(Attachment.ofSubmission(submission, stored.storedName(), stored.originalName()));
	}

	// ---------- 과제에 추가 첨부 ----------

	@Test
	@DisplayName("과제 등록 - 대표 첨부와 추가 첨부 2개를 함께 올리면 모두 저장되고 수정 화면 · 학생 상세에 목록이 보임")
	void assignment_createWithExtras() throws Exception {
		MockMultipartHttpServletRequestBuilder request = assignmentForm("/instructor/assignments/new", instructor);
		request.param("courseId", course.getId().toString());
		request.file(file("file", "대표.pdf", "대표")).file(extra("보충1.pdf")).file(extra("보충2.pdf"));
		mockMvc.perform(request).andExpect(status().is3xxRedirection());

		Assignment saved = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).stream()
				.filter(a -> a.getTitle().equals("2주차 과제")).findFirst().orElseThrow();
		assertThat(saved.getOriginalFilename()).isEqualTo("대표.pdf");
		assertThat(attachmentRepository.findByAssignmentIdOrderByIdAsc(saved.getId()))
				.extracting(Attachment::getOriginalFilename).containsExactly("보충1.pdf", "보충2.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);

		mockMvc.perform(get("/instructor/assignments/" + saved.getId() + "/edit").with(user(principal(instructor))))
				.andExpect(content().string(containsString("attachmentList")))
				.andExpect(content().string(containsString("보충1.pdf")))
				.andExpect(content().string(containsString("보충2.pdf")))
				.andExpect(content().string(containsString("multiple")));
		mockMvc.perform(get("/student/assignments/" + saved.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("assignmentAttachmentList")))
				.andExpect(content().string(containsString("보충1.pdf")))
				.andExpect(content().string(containsString("/assignments/" + saved.getId() + "/file")));
	}

	@Test
	@DisplayName("과제 수정 - 대표 첨부 없이 추가 첨부만 올려도 되고, 수정 때 올린 파일은 기존 추가 첨부에 이어서 쌓임")
	void assignment_updateAddsMore() throws Exception {
		assignmentAttachment("기존.pdf");

		mockMvc.perform(assignmentForm("/instructor/assignments/" + assignment.getId() + "/edit", instructor)
						.file(extra("새것.pdf")))
				.andExpect(status().is3xxRedirection());

		assertThat(attachmentRepository.findByAssignmentIdOrderByIdAsc(assignment.getId()))
				.extracting(Attachment::getOriginalFilename).containsExactly("기존.pdf", "새것.pdf");
	}

	@Test
	@DisplayName("파일을 고르지 않아 빈 파일 칸이 넘어와도 오류 없이 등록됨")
	void assignment_emptyExtraPart() throws Exception {
		mockMvc.perform(assignmentForm("/instructor/assignments/" + assignment.getId() + "/edit", instructor)
						.file(file("extraFiles", "", "")))
				.andExpect(status().is3xxRedirection());

		assertThat(attachmentRepository.countByAssignmentId(assignment.getId())).isZero();
	}

	@Test
	@DisplayName("대표 첨부를 포함해 6개를 올리면 파일 칸에 오류가 표시되고 파일이 남지 않음, 5개는 허용됨")
	void assignment_limit() throws Exception {
		MockMultipartHttpServletRequestBuilder tooMany = assignmentForm(
				"/instructor/assignments/" + assignment.getId() + "/edit", instructor).file(file("file", "대표.pdf", "대표"));
		for (int i = 1; i <= 5; i++) {
			tooMany.file(extra("추가" + i + ".pdf"));
		}
		mockMvc.perform(tooMany)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("첨부파일은 최대 5개까지 올릴 수 있습니다.")));
		assertThat(UploadTestSupport.fileCount()).isZero();

		MockMultipartHttpServletRequestBuilder five = assignmentForm(
				"/instructor/assignments/" + assignment.getId() + "/edit", instructor).file(file("file", "대표.pdf", "대표"));
		for (int i = 1; i <= 4; i++) {
			five.file(extra("추가" + i + ".pdf"));
		}
		mockMvc.perform(five).andExpect(status().is3xxRedirection());
		assertThat(attachmentRepository.countByAssignmentId(assignment.getId())).isEqualTo(4);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("추가 첨부에 허용하지 않는 확장자가 있으면 오류가 표시됨")
	void assignment_invalidExtension() throws Exception {
		mockMvc.perform(assignmentForm("/instructor/assignments/" + assignment.getId() + "/edit", instructor)
						.file(extra("악성.exe")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("허용하지 않는 파일 형식입니다.")));
	}

	@Test
	@DisplayName("추가 첨부 파일명에 HTML이 있어도 화면에서 이스케이프됨")
	void xss_escaped() throws Exception {
		assignmentAttachment("<b>굵게.pdf");
		Submission submission = submissionOf(student);
		submissionAttachment(submission, "<i>제출.pdf");

		for (String page : new String[] { "/student/assignments/" + assignment.getId() }) {
			mockMvc.perform(get(page).with(user(principal(student))))
					.andExpect(content().string(containsString("&lt;b&gt;굵게.pdf")))
					.andExpect(content().string(containsString("&lt;i&gt;제출.pdf")))
					.andExpect(content().string(not(containsString("<b>굵게.pdf"))))
					.andExpect(content().string(not(containsString("<i>제출.pdf"))));
		}
		mockMvc.perform(get("/instructor/submissions/" + submission.getId() + "/grade").with(user(principal(instructor))))
				.andExpect(content().string(containsString("&lt;i&gt;제출.pdf")))
				.andExpect(content().string(not(containsString("<i>제출.pdf"))));
	}

	// ---------- 다운로드 ----------

	@Test
	@DisplayName("과제 추가 첨부 다운로드 - 본인 강좌의 강사와 수강 중인 학생은 내려받고, 수강하지 않는 학생 · 다른 강사는 403")
	void download_assignmentAttachment() throws Exception {
		Attachment attachment = assignmentAttachment("보충.pdf");
		String url = "/attachments/" + attachment.getId() + "/file";
		User outsider = createUser("outsider@test.com", Role.STUDENT);

		mockMvc.perform(get(url).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(content().bytes("과제 첨부 보충.pdf".getBytes(StandardCharsets.UTF_8)));
		mockMvc.perform(get(url).with(user(principal(student)))).andExpect(status().isOk());
		mockMvc.perform(get(url).with(user(principal(outsider)))).andExpect(status().isForbidden());
		mockMvc.perform(get(url).with(user(principal(otherInstructor)))).andExpect(status().isForbidden());
		mockMvc.perform(get(url)).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/attachments/999999/file").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("제출 추가 첨부 다운로드 - 본인 학생과 본인 강좌의 강사만 가능, 다른 학생 · 다른 강사는 403")
	void download_submissionAttachment() throws Exception {
		Attachment attachment = submissionAttachment(submissionOf(student), "내파일.pdf");
		String url = "/attachments/" + attachment.getId() + "/file";

		mockMvc.perform(get(url).with(user(principal(student)))).andExpect(status().isOk());
		mockMvc.perform(get(url).with(user(principal(instructor)))).andExpect(status().isOk());
		mockMvc.perform(get(url).with(user(principal(otherStudent)))).andExpect(status().isForbidden());
		mockMvc.perform(get(url).with(user(principal(otherInstructor)))).andExpect(status().isForbidden());
	}

	// ---------- 강사의 삭제 ----------

	@Test
	@DisplayName("강사가 과제 추가 첨부를 삭제하면 행이 지워지고 수정 화면으로 돌아감 (다른 첨부는 그대로)")
	void instructor_removeAttachment() throws Exception {
		Attachment first = assignmentAttachment("첫째.pdf");
		Attachment second = assignmentAttachment("둘째.pdf");

		mockMvc.perform(post("/instructor/attachments/" + first.getId() + "/delete")
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(redirectedUrl("/instructor/assignments/" + assignment.getId() + "/edit"))
				.andExpect(flash().attribute("message", "첨부를 삭제했습니다."));

		assertThat(attachmentRepository.findByAssignmentIdOrderByIdAsc(assignment.getId()))
				.extracting(Attachment::getId).containsExactly(second.getId());
	}

	@Test
	@DisplayName("과제 추가 첨부 삭제 - 다른 강사 · 학생은 403, 비로그인 · CSRF 없음 · GET은 거부, 제출 첨부나 없는 첨부는 404")
	void instructor_removeAttachment_denied() throws Exception {
		Attachment attachment = assignmentAttachment("첫째.pdf");
		String url = "/instructor/attachments/" + attachment.getId() + "/delete";
		Attachment submissionOne = submissionAttachment(submissionOf(student), "제출.pdf");

		mockMvc.perform(post(url).with(user(principal(otherInstructor))).with(csrf())).andExpect(status().isForbidden());
		mockMvc.perform(post(url).with(user(principal(student))).with(csrf())).andExpect(status().isForbidden());
		mockMvc.perform(post(url).with(csrf())).andExpect(status().is3xxRedirection());
		mockMvc.perform(post(url).with(user(principal(instructor)))).andExpect(status().isForbidden());
		mockMvc.perform(get(url).with(user(principal(instructor)))).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(post("/instructor/attachments/" + submissionOne.getId() + "/delete")
				.with(user(principal(instructor))).with(csrf())).andExpect(status().isNotFound());
		mockMvc.perform(post("/instructor/attachments/999999/delete")
				.with(user(principal(instructor))).with(csrf())).andExpect(status().isNotFound());

		assertThat(attachmentRepository.countByAssignmentId(assignment.getId())).isEqualTo(1);
		assertThat(attachmentRepository.countBySubmissionId(submissionOne.getSubmission().getId())).isEqualTo(1);
	}

	// ---------- 학생의 제출 ----------

	@Test
	@DisplayName("제출 - 대표 첨부와 추가 첨부를 함께 올리면 저장되고, 재제출에서는 추가 첨부가 이어서 쌓임")
	void student_submitWithExtras() throws Exception {
		mockMvc.perform(submitForm(student).file(file("file", "대표.pdf", "대표")).file(extra("추가1.pdf")))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));

		Submission submission = submissionOf(student);
		assertThat(submission.getOriginalFilename()).isEqualTo("대표.pdf");
		assertThat(attachmentRepository.findBySubmissionIdOrderByIdAsc(submission.getId()))
				.extracting(Attachment::getOriginalFilename).containsExactly("추가1.pdf");

		mockMvc.perform(submitForm(student).file(extra("추가2.pdf")))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));
		assertThat(attachmentRepository.findBySubmissionIdOrderByIdAsc(submission.getId()))
				.extracting(Attachment::getOriginalFilename).containsExactly("추가1.pdf", "추가2.pdf");

		mockMvc.perform(get("/student/assignments/" + assignment.getId()).with(user(principal(student))))
				.andExpect(content().string(containsString("submissionAttachmentList")))
				.andExpect(content().string(containsString("추가2.pdf")));
		mockMvc.perform(get("/student/assignments/" + assignment.getId() + "/submit").with(user(principal(student))))
				.andExpect(content().string(containsString("attachmentList")))
				.andExpect(content().string(containsString("추가1.pdf")));
		mockMvc.perform(get("/instructor/submissions/" + submission.getId() + "/grade").with(user(principal(instructor))))
				.andExpect(content().string(containsString("submissionAttachmentList")))
				.andExpect(content().string(containsString("추가2.pdf")));
	}

	@Test
	@DisplayName("제출의 첨부가 이미 5개이면 더 올릴 수 없고 오류가 표시됨 (대표 첨부 포함)")
	void student_limit() throws Exception {
		mockMvc.perform(submitForm(student).file(file("file", "대표.pdf", "대표"))
				.file(extra("1.pdf")).file(extra("2.pdf")).file(extra("3.pdf")).file(extra("4.pdf")))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(submitForm(student).file(extra("6번째.pdf")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("첨부파일은 최대 5개까지 올릴 수 있습니다.")));
	}

	@Test
	@DisplayName("학생이 제출 추가 첨부를 삭제하면 해당 첨부만 지워지고 제출 폼으로 돌아감")
	void student_removeAttachment() throws Exception {
		Submission submission = submissionOf(student);
		Attachment first = submissionAttachment(submission, "첫째.pdf");
		Attachment second = submissionAttachment(submission, "둘째.pdf");

		mockMvc.perform(post(deleteUrl(first)).with(user(principal(student))).with(csrf()))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId() + "/submit"))
				.andExpect(flash().attribute("message", "첨부를 삭제했습니다."));

		assertThat(attachmentRepository.findBySubmissionIdOrderByIdAsc(submission.getId()))
				.extracting(Attachment::getId).containsExactly(second.getId());
	}

	private String deleteUrl(Attachment attachment) {
		return "/student/assignments/" + assignment.getId() + "/attachments/" + attachment.getId() + "/delete";
	}

	@Test
	@DisplayName("채점 완료된 제출의 추가 첨부는 삭제할 수 없음 (B4)")
	void student_removeAttachment_graded() throws Exception {
		Submission submission = submissionOf(student);
		Attachment attachment = submissionAttachment(submission, "첫째.pdf");
		submission.grade(80, "피드백", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);

		mockMvc.perform(post(deleteUrl(attachment)).with(user(principal(student))).with(csrf()))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()))
				.andExpect(flash().attribute("error", "채점 완료된 과제입니다."));

		assertThat(attachmentRepository.findById(attachment.getId())).isPresent();
	}

	@Test
	@DisplayName("제출 기간이 아니면 제출의 추가 첨부를 삭제할 수 없음 (B1)")
	void student_removeAttachment_outsidePeriod() throws Exception {
		Submission submission = submissionOf(student);
		Attachment attachment = submissionAttachment(submission, "첫째.pdf");
		assignment.update(assignment.getTitle(), assignment.getContent(), LocalDateTime.now().minusDays(5),
				LocalDateTime.now().minusDays(1), assignment.getMaxScore());
		assignmentRepository.saveAndFlush(assignment);

		mockMvc.perform(post(deleteUrl(attachment)).with(user(principal(student))).with(csrf()))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));

		assertThat(attachmentRepository.findById(attachment.getId())).isPresent();
	}

	@Test
	@DisplayName("제출 추가 첨부 삭제 - 다른 학생 · 강사는 403, 비로그인 · CSRF 없음 · GET은 거부, 과제 첨부나 다른 과제 주소는 404")
	void student_removeAttachment_denied() throws Exception {
		Attachment attachment = submissionAttachment(submissionOf(student), "첫째.pdf");
		Attachment assignmentOne = assignmentAttachment("과제용.pdf");
		String url = deleteUrl(attachment);

		mockMvc.perform(post(url).with(user(principal(otherStudent))).with(csrf())).andExpect(status().isForbidden());
		mockMvc.perform(post(url).with(user(principal(instructor))).with(csrf())).andExpect(status().isForbidden());
		mockMvc.perform(post(url).with(csrf())).andExpect(status().is3xxRedirection());
		mockMvc.perform(post(url).with(user(principal(student)))).andExpect(status().isForbidden());
		mockMvc.perform(get(url).with(user(principal(student)))).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(post(deleteUrl(assignmentOne)).with(user(principal(student))).with(csrf()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/student/assignments/999999/attachments/" + attachment.getId() + "/delete")
				.with(user(principal(student))).with(csrf())).andExpect(status().isNotFound());

		assertThat(attachmentRepository.findById(attachment.getId())).isPresent();
		assertThat(attachmentRepository.findById(assignmentOne.getId())).isPresent();
	}
}
