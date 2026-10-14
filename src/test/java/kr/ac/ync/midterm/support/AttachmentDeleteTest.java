package kr.ac.ync.midterm.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.assignment.service.AssignmentService;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 첨부 파일 삭제 (X18 과제 첨부, X23 제출 파일): 서버의 파일이 실제로 지워지는지, 규칙(B1 · B2 · B4 · B5)에 따라
 * 거부될 때 파일이 남는지, DB 변경이 확정된 뒤에만 파일을 지우는지 확인한다.
 * 파일 삭제는 커밋 뒤에 일어나므로 이 테스트는 데이터를 실제로 커밋하고, 끝난 뒤 직접 정리한다.
 */
@SpringBootTest
class AttachmentDeleteTest {

	private static final String TEACHER = "delete-teacher@test.com";
	private static final String OTHER_TEACHER = "delete-other-teacher@test.com";
	private static final String STUDENT = "delete-student@test.com";
	private static final String OTHER_STUDENT = "delete-other-student@test.com";

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private SubmissionService submissionService;

	@Autowired
	private FileStorage fileStorage;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;
	private User instructor;
	private User otherInstructor;
	private User student;
	private User otherStudent;
	private Course course;
	private Long assignmentId;

	@BeforeEach
	void setUp() {
		cleanData();
		UploadTestSupport.clean();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

		instructor = savedUser(TEACHER, Role.INSTRUCTOR, null);
		otherInstructor = savedUser(OTHER_TEACHER, Role.INSTRUCTOR, null);
		student = savedUser(STUDENT, Role.STUDENT, "20269991");
		otherStudent = savedUser(OTHER_STUDENT, Role.STUDENT, "20269992");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("삭제 확인").joinCode("DELET2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		assignmentId = createAssignment("첨부 있는 과제", pdf("안내.pdf", "과제 안내"));
	}

	@AfterEach
	void tearDown() {
		cleanData();
		UploadTestSupport.clean();
	}

	private void cleanData() {
		userRepository.findByEmail(TEACHER).ifPresent(teacher -> {
			courseRepository.findByInstructorIdOrderByCreatedAtDesc(teacher.getId()).forEach(c -> {
				assignmentRepository.findByCourseIdOrderByStartAtDesc(c.getId()).forEach(a -> {
					submissionRepository.deleteAll(submissionRepository.findAll().stream()
							.filter(s -> s.getAssignment().getId().equals(a.getId())).toList());
					assignmentRepository.delete(a);
				});
				enrollmentRepository.deleteAll(enrollmentRepository.findAll().stream()
						.filter(e -> e.getCourse().getId().equals(c.getId())).toList());
				courseRepository.delete(c);
			});
			userRepository.delete(teacher);
		});
		for (String email : new String[] { OTHER_TEACHER, STUDENT, OTHER_STUDENT }) {
			userRepository.findByEmail(email).ifPresent(userRepository::delete);
		}
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(User.builder().email(email).password(passwordEncoder.encode("password123"))
				.name("삭제확인").studentNo(studentNo).role(role).build());
	}

	private MockMultipartFile pdf(String name, String content) {
		return new MockMultipartFile("file", name, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
	}

	private Long createAssignment(String title, MockMultipartFile file) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle(title);
		request.setContent("과제 내용");
		request.setStartAt(LocalDateTime.now().minusDays(1));
		request.setEndAt(LocalDateTime.now().plusDays(1));
		request.setMaxScore(100);
		request.setFile(file);
		return assignmentService.create(instructor.getId(), course.getId(), request).id();
	}

	private void submitWithFile(String text, MockMultipartFile file) {
		SubmissionRequest request = new SubmissionRequest();
		request.setContent(text);
		request.setFile(file);
		submissionService.submit(student.getId(), assignmentId, request);
	}

	private Assignment assignment() {
		return assignmentRepository.findById(assignmentId).orElseThrow();
	}

	private Submission submission() {
		return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElseThrow();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder removeAssignmentFile(User actor) {
		return post("/instructor/assignments/" + assignmentId + "/file/delete")
				.with(user(new CustomUserDetails(actor))).with(csrf());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder removeSubmissionFile(User actor) {
		return post("/student/assignments/" + assignmentId + "/submission/file/delete")
				.with(user(new CustomUserDetails(actor))).with(csrf());
	}

	// ---------- 커밋 뒤에만 파일 삭제 ----------

	@Test
	@DisplayName("deleteAfterCommit - 커밋되면 파일이 지워지고, 롤백되면 남으며, 트랜잭션 밖에서는 바로 지워짐")
	void deleteAfterCommit() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		String committed = fileStorage.store(pdf("a.pdf", "커밋")).storedName();
		tx.executeWithoutResult(status -> fileStorage.deleteAfterCommit(committed));
		assertThat(fileStorage.exists(committed)).isFalse();

		String rolledBack = fileStorage.store(pdf("b.pdf", "롤백")).storedName();
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			fileStorage.deleteAfterCommit(rolledBack);
			throw new IllegalStateException("DB 오류");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(fileStorage.exists(rolledBack)).isTrue();

		String outside = fileStorage.store(pdf("c.pdf", "밖")).storedName();
		fileStorage.deleteAfterCommit(outside);
		assertThat(fileStorage.exists(outside)).isFalse();

		fileStorage.deleteAfterCommit(null);
	}

	@Test
	@DisplayName("서비스가 끝까지 커밋되지 못하면(롤백) DB의 첨부 정보와 서버의 파일이 모두 그대로 남음")
	void removeAttachment_rolledBackKeepsBoth() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		String stored = assignment().getFilePath();

		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			assignmentService.removeAttachment(instructor.getId(), assignmentId);
			throw new IllegalStateException("뒤이은 DB 오류");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(assignment().getFilePath()).isEqualTo(stored);
		assertThat(fileStorage.exists(stored)).isTrue();
	}

	// ---------- 과제 첨부 삭제 (X18) ----------

	@Test
	@DisplayName("과제 첨부 삭제 - 서버의 파일과 DB의 첨부 정보가 지워지고 과제의 다른 값은 그대로이며 수정 화면으로 돌아감")
	void assignment_remove() throws Exception {
		Assignment before = assignment();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		mockMvc.perform(removeAssignmentFile(instructor))
				.andExpect(redirectedUrl("/instructor/assignments/" + assignmentId + "/edit"))
				.andExpect(flash().attribute("message", "첨부를 삭제했습니다."));

		Assignment after = assignment();
		assertThat(after.getFilePath()).isNull();
		assertThat(after.getOriginalFilename()).isNull();
		assertThat(after.getTitle()).isEqualTo(before.getTitle());
		assertThat(after.getContent()).isEqualTo(before.getContent());
		assertThat(after.getStartAt()).isEqualTo(before.getStartAt());
		assertThat(after.getEndAt()).isEqualTo(before.getEndAt());
		assertThat(after.getMaxScore()).isEqualTo(before.getMaxScore());
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("과제 첨부 삭제 후 - 다운로드는 404, 학생 과제 상세와 수정 화면에서 첨부가 사라지고 새 파일을 다시 올릴 수 있음")
	void assignment_afterRemove() throws Exception {
		mockMvc.perform(removeAssignmentFile(instructor));

		mockMvc.perform(get("/assignments/" + assignmentId + "/file").with(user(new CustomUserDetails(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/student/assignments/" + assignmentId).with(user(new CustomUserDetails(student))))
				.andExpect(content().string(not(containsString("안내.pdf"))))
				.andExpect(content().string(containsString("없음")));
		mockMvc.perform(get("/instructor/assignments/" + assignmentId + "/edit")
						.with(user(new CustomUserDetails(instructor))))
				.andExpect(content().string(not(containsString("현재 첨부"))))
				.andExpect(content().string(not(containsString("deleteAttachmentButton"))));

		AssignmentRequest again = new AssignmentRequest();
		again.setTitle("첨부 있는 과제");
		again.setContent("과제 내용");
		again.setStartAt(LocalDateTime.now().minusDays(1));
		again.setEndAt(LocalDateTime.now().plusDays(1));
		again.setMaxScore(100);
		again.setFile(pdf("새 안내.pdf", "새 파일"));
		assignmentService.update(instructor.getId(), assignmentId, again);
		assertThat(assignment().getOriginalFilename()).isEqualTo("새 안내.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("과제 수정 화면 - 첨부가 있을 때만 삭제 버튼과 확인 창이 붙은 삭제 폼이 보임")
	void assignment_editFormButton() throws Exception {
		mockMvc.perform(get("/instructor/assignments/" + assignmentId + "/edit")
						.with(user(new CustomUserDetails(instructor))))
				.andExpect(content().string(containsString("deleteAttachmentButton")))
				.andExpect(content().string(containsString("/instructor/assignments/" + assignmentId + "/file/delete")))
				.andExpect(content().string(containsString("confirm(")));
	}

	@Test
	@DisplayName("제출이 있는 과제도 첨부는 삭제할 수 있음 (B8은 과제 삭제에 대한 규칙)")
	void assignment_removeWithSubmissions() throws Exception {
		submitWithFile("답안", null);

		mockMvc.perform(removeAssignmentFile(instructor))
				.andExpect(flash().attribute("message", "첨부를 삭제했습니다."));

		assertThat(assignment().hasFile()).isFalse();
		assertThat(submissionRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("다른 강사의 과제 첨부는 삭제할 수 없고(403) 파일이 그대로 남음 (B5)")
	void assignment_otherInstructor() throws Exception {
		mockMvc.perform(removeAssignmentFile(otherInstructor)).andExpect(status().isForbidden());

		assertThat(assignment().hasFile()).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("과제 첨부 삭제 - 학생은 403, 비로그인은 로그인 화면으로, CSRF 토큰이 없으면 거부, GET은 허용되지 않음")
	void assignment_security() throws Exception {
		mockMvc.perform(removeAssignmentFile(student)).andExpect(status().isForbidden());
		mockMvc.perform(post("/instructor/assignments/" + assignmentId + "/file/delete").with(csrf()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/instructor/assignments/" + assignmentId + "/file/delete")
						.with(user(new CustomUserDetails(instructor))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/instructor/assignments/" + assignmentId + "/file/delete")
						.with(user(new CustomUserDetails(instructor))))
				.andExpect(status().isMethodNotAllowed());

		assertThat(assignment().hasFile()).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("첨부가 없는 과제나 이미 삭제한 과제는 오류 화면이 아니라 안내가 나옴, 없는 과제는 404")
	void assignment_noAttachment() throws Exception {
		mockMvc.perform(removeAssignmentFile(instructor));

		mockMvc.perform(removeAssignmentFile(instructor))
				.andExpect(redirectedUrl("/instructor/assignments/" + assignmentId + "/edit"))
				.andExpect(flash().attribute("error", "삭제할 첨부가 없습니다."));
		mockMvc.perform(post("/instructor/assignments/999999/file/delete")
						.with(user(new CustomUserDetails(instructor))).with(csrf()))
				.andExpect(status().isNotFound());
	}

	// ---------- 제출 파일 삭제 (X23) ----------

	@Test
	@DisplayName("제출 파일 삭제 - 서버의 파일과 DB의 첨부 정보가 지워지고 제출 내용 · 시각은 그대로이며 제출 폼으로 돌아감")
	void submission_remove() throws Exception {
		submitWithFile("내 답안", pdf("내 답안.pdf", "제출 파일"));
		Submission before = submission();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(2);

		mockMvc.perform(removeSubmissionFile(student))
				.andExpect(redirectedUrl("/student/assignments/" + assignmentId + "/submit"))
				.andExpect(flash().attribute("message", "첨부를 삭제했습니다."));

		Submission after = submission();
		assertThat(after.getFilePath()).isNull();
		assertThat(after.getOriginalFilename()).isNull();
		assertThat(after.getContent()).isEqualTo("내 답안");
		assertThat(after.getSubmittedAt()).isEqualTo(before.getSubmittedAt());
		assertThat(after.getStatus()).isEqualTo(before.getStatus());
		// 과제의 첨부 1개만 남는다
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
		assertThat(fileStorage.exists(before.getFilePath())).isFalse();
	}

	@Test
	@DisplayName("제출 파일 삭제 후 - 다운로드는 404, 제출 폼에서 첨부가 사라지고 새 파일로 다시 제출할 수 있음")
	void submission_afterRemove() throws Exception {
		submitWithFile("내 답안", pdf("내 답안.pdf", "제출 파일"));
		Long submissionId = submission().getId();
		mockMvc.perform(get("/student/assignments/" + assignmentId + "/submit").with(user(new CustomUserDetails(student))))
				.andExpect(content().string(containsString("deleteSubmissionFileButton")))
				.andExpect(content().string(containsString("/student/assignments/" + assignmentId + "/submission/file/delete")))
				.andExpect(content().string(containsString("confirm(")));

		mockMvc.perform(removeSubmissionFile(student));

		mockMvc.perform(get("/submissions/" + submissionId + "/file").with(user(new CustomUserDetails(student))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/submissions/" + submissionId + "/file").with(user(new CustomUserDetails(instructor))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/student/assignments/" + assignmentId + "/submit").with(user(new CustomUserDetails(student))))
				.andExpect(content().string(not(containsString("deleteSubmissionFileButton"))))
				.andExpect(content().string(not(containsString("내 답안.pdf"))));

		submitWithFile("다시 낸 답안", pdf("새 답안.pdf", "새 제출 파일"));
		assertThat(submission().getOriginalFilename()).isEqualTo("새 답안.pdf");
		assertThat(submissionRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("제출 기간이 아니면(마감 · 시작 전) 제출 파일을 삭제할 수 없고 파일이 남음 (B1)")
	void submission_outsidePeriod() throws Exception {
		submitWithFile("답안", pdf("답안.pdf", "제출 파일"));
		String stored = submission().getFilePath();

		Assignment closed = assignment();
		closed.update(closed.getTitle(), closed.getContent(), LocalDateTime.now().minusDays(5),
				LocalDateTime.now().minusDays(1), closed.getMaxScore());
		assignmentRepository.save(closed);
		mockMvc.perform(removeSubmissionFile(student))
				.andExpect(redirectedUrl("/student/assignments/" + assignmentId))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));

		Assignment upcoming = assignment();
		upcoming.update(upcoming.getTitle(), upcoming.getContent(), LocalDateTime.now().plusDays(1),
				LocalDateTime.now().plusDays(5), upcoming.getMaxScore());
		assignmentRepository.save(upcoming);
		mockMvc.perform(removeSubmissionFile(student))
				.andExpect(flash().attribute("error", "제출 기간이 아닙니다."));

		assertThat(submission().getFilePath()).isEqualTo(stored);
		assertThat(fileStorage.exists(stored)).isTrue();
	}

	@Test
	@DisplayName("채점 완료된 제출은 파일을 삭제할 수 없고 파일이 남음 (B4)")
	void submission_graded() throws Exception {
		submitWithFile("답안", pdf("답안.pdf", "제출 파일"));
		Submission graded = submission();
		graded.grade(90, "피드백", LocalDateTime.now());
		submissionRepository.save(graded);
		String stored = graded.getFilePath();

		mockMvc.perform(removeSubmissionFile(student))
				.andExpect(redirectedUrl("/student/assignments/" + assignmentId))
				.andExpect(flash().attribute("error", "채점 완료된 과제입니다."));

		assertThat(submission().getFilePath()).isEqualTo(stored);
		assertThat(fileStorage.exists(stored)).isTrue();
	}

	@Test
	@DisplayName("수강하지 않는 학생은 403이고, 다른 학생의 제출 파일은 건드릴 수 없음 (B2)")
	void submission_notEnrolledAndOthers() throws Exception {
		submitWithFile("답안", pdf("답안.pdf", "제출 파일"));
		String stored = submission().getFilePath();

		mockMvc.perform(removeSubmissionFile(otherStudent)).andExpect(status().isForbidden());
		// 수강 등록한 다른 학생이라도 자기 제출이 없으면 삭제할 것이 없다 (내 제출만 대상)
		enrollmentRepository.save(Enrollment.builder().course(course).student(otherStudent).build());
		mockMvc.perform(removeSubmissionFile(otherStudent))
				.andExpect(flash().attribute("error", "삭제할 첨부가 없습니다."));

		assertThat(submission().getFilePath()).isEqualTo(stored);
		assertThat(fileStorage.exists(stored)).isTrue();
	}

	@Test
	@DisplayName("제출이 없거나 첨부 없이 낸 제출은 삭제할 것이 없다는 안내가 나옴, 없는 과제는 404")
	void submission_nothingToDelete() throws Exception {
		mockMvc.perform(removeSubmissionFile(student))
				.andExpect(flash().attribute("error", "삭제할 첨부가 없습니다."));

		submitWithFile("첨부 없이 낸 답안", null);
		mockMvc.perform(removeSubmissionFile(student))
				.andExpect(flash().attribute("error", "삭제할 첨부가 없습니다."));
		mockMvc.perform(post("/student/assignments/999999/submission/file/delete")
						.with(user(new CustomUserDetails(student))).with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("제출 파일 삭제 - 강사는 403, 비로그인은 로그인 화면으로, CSRF 토큰이 없으면 거부, GET은 허용되지 않음")
	void submission_security() throws Exception {
		submitWithFile("답안", pdf("답안.pdf", "제출 파일"));
		String stored = submission().getFilePath();

		mockMvc.perform(removeSubmissionFile(instructor)).andExpect(status().isForbidden());
		mockMvc.perform(post("/student/assignments/" + assignmentId + "/submission/file/delete").with(csrf()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/student/assignments/" + assignmentId + "/submission/file/delete")
						.with(user(new CustomUserDetails(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/student/assignments/" + assignmentId + "/submission/file/delete")
						.with(user(new CustomUserDetails(student))))
				.andExpect(status().isMethodNotAllowed());

		assertThat(fileStorage.exists(stored)).isTrue();
	}
}
