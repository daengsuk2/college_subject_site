package kr.ac.ync.midterm.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.assignment.service.AssignmentService;
import kr.ac.ync.midterm.attachment.domain.Attachment;
import kr.ac.ync.midterm.attachment.repository.AttachmentRepository;
import kr.ac.ync.midterm.attachment.service.AttachmentService;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.global.storage.InvalidFileException;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 여러 파일 첨부와 파일 · DB 일관성 (X17 + X22): 추가 첨부를 올리다 실패하거나 커밋에 실패하면 이미 저장한 파일이 남지 않고,
 * 삭제는 커밋된 뒤에만 서버의 파일이 지워지는지 확인한다. 커밋 뒤 동작이라 데이터를 실제로 커밋하고 끝난 뒤 직접 정리한다.
 */
@SpringBootTest
class MultiAttachmentConsistencyTest {

	private static final String TEACHER = "multi-teacher@test.com";
	private static final String STUDENT = "multi-student@test.com";

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
	private AttachmentRepository attachmentRepository;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private SubmissionService submissionService;

	@Autowired
	private AttachmentService attachmentService;

	@Autowired
	private FileStorage fileStorage;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User instructor;
	private User student;
	private Course course;
	private Long assignmentId;

	@BeforeEach
	void setUp() {
		cleanData();
		UploadTestSupport.clean();

		instructor = savedUser(TEACHER, Role.INSTRUCTOR, null);
		student = savedUser(STUDENT, Role.STUDENT, "20269971");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("여러 첨부").joinCode("MULTI2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		assignmentId = assignmentService.create(instructor.getId(), course.getId(),
				request(pdf("file", "대표.pdf"), pdf("extraFiles", "추가1.pdf"), pdf("extraFiles", "추가2.pdf"))).id();
	}

	@AfterEach
	void tearDown() {
		cleanData();
		UploadTestSupport.clean();
	}

	private void cleanData() {
		attachmentRepository.deleteAll();
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
		userRepository.findByEmail(STUDENT).ifPresent(userRepository::delete);
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(User.builder().email(email).password(passwordEncoder.encode("password123"))
				.name("여러첨부").studentNo(studentNo).role(role).build());
	}

	private MockMultipartFile pdf(String part, String name) {
		return new MockMultipartFile(part, name, "application/pdf", ("내용 " + name).getBytes(StandardCharsets.UTF_8));
	}

	// 첫 파일은 대표 첨부(file), 나머지는 추가 첨부(extraFiles)
	private AssignmentRequest request(MockMultipartFile main, MockMultipartFile... extras) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle("첨부 있는 과제");
		request.setContent("과제 내용");
		request.setStartAt(LocalDateTime.now().minusDays(1));
		request.setEndAt(LocalDateTime.now().plusDays(1));
		request.setMaxScore(100);
		request.setFile(main);
		request.setExtraFiles(List.of(extras));
		return request;
	}

	private SubmissionRequest submissionRequest(MockMultipartFile... extras) {
		SubmissionRequest request = new SubmissionRequest();
		request.setContent("답안");
		request.setExtraFiles(List.of(extras));
		return request;
	}

	private void rollbackAfter(Runnable work) {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			work.run();
			throw new IllegalStateException("커밋 단계의 DB 오류");
		})).isInstanceOf(IllegalStateException.class);
	}

	private List<Attachment> extras() {
		return attachmentRepository.findByAssignmentIdOrderByIdAsc(assignmentId);
	}

	@Test
	@DisplayName("처음 등록 - 대표 첨부 1개와 추가 첨부 2개가 저장됨")
	void created() {
		assertThat(extras()).extracting(Attachment::getOriginalFilename).containsExactly("추가1.pdf", "추가2.pdf");
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("추가 첨부 중 하나가 허용되지 않는 형식이면 등록이 실패하고 먼저 저장한 파일도 남지 않음")
	void invalidExtraCleansEarlierFiles() {
		UploadTestSupport.clean();
		long before = assignmentRepository.count();

		assertThatThrownBy(() -> assignmentService.create(instructor.getId(), course.getId(),
				request(pdf("file", "대표.pdf"), pdf("extraFiles", "정상.pdf"), pdf("extraFiles", "악성.exe"))))
				.isInstanceOf(InvalidFileException.class);

		assertThat(assignmentRepository.count()).isEqualTo(before);
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("개수를 넘기면 등록이 실패하고 파일도 남지 않음 (대표 1개 + 추가 5개)")
	void tooManyCleansFiles() {
		UploadTestSupport.clean();
		long before = assignmentRepository.count();

		assertThatThrownBy(() -> assignmentService.create(instructor.getId(), course.getId(),
				request(pdf("file", "대표.pdf"), pdf("extraFiles", "1.pdf"), pdf("extraFiles", "2.pdf"),
						pdf("extraFiles", "3.pdf"), pdf("extraFiles", "4.pdf"), pdf("extraFiles", "5.pdf"))))
				.isInstanceOf(InvalidFileException.class);

		assertThat(assignmentRepository.count()).isEqualTo(before);
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("수정 도중 실패해도 기존 첨부(대표 · 추가)는 그대로 남고 새로 올린 파일은 정리됨")
	void updateFailureKeepsEverything() {
		String mainStored = assignmentRepository.findById(assignmentId).orElseThrow().getFilePath();

		assertThatThrownBy(() -> assignmentService.update(instructor.getId(), assignmentId,
				request(pdf("file", "새대표.pdf"), pdf("extraFiles", "새추가.pdf"), pdf("extraFiles", "악성.exe"))))
				.isInstanceOf(InvalidFileException.class);

		assertThat(assignmentRepository.findById(assignmentId).orElseThrow().getFilePath()).isEqualTo(mainStored);
		assertThat(fileStorage.exists(mainStored)).isTrue();
		assertThat(extras()).hasSize(2);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("추가 첨부 저장 롤백 - 올린 파일이 서버에 남지 않고 행도 없음 / 커밋 - 파일과 행이 남음")
	void addRolledBackAndCommitted() {
		var assignment = assignmentRepository.findById(assignmentId).orElseThrow();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);

		rollbackAfter(() -> attachmentService.addToAssignment(assignment, List.of(pdf("extraFiles", "롤백.pdf"))));
		assertThat(extras()).hasSize(2);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);

		new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> attachmentService.addToAssignment(assignment, List.of(pdf("extraFiles", "커밋.pdf"))));
		assertThat(extras()).hasSize(3);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(4);
	}

	@Test
	@DisplayName("추가 첨부 삭제 - 롤백되면 행과 파일이 남고, 커밋되면 행과 파일이 모두 지워짐")
	void removeRolledBackAndCommitted() {
		Long id = extras().getFirst().getId();
		String stored = extras().getFirst().getStoredName();

		rollbackAfter(() -> attachmentService.removeFromAssignment(instructor.getId(), id));
		assertThat(attachmentRepository.findById(id)).isPresent();
		assertThat(fileStorage.exists(stored)).isTrue();

		attachmentService.removeFromAssignment(instructor.getId(), id);
		assertThat(attachmentRepository.findById(id)).isEmpty();
		assertThat(fileStorage.exists(stored)).isFalse();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("과제 삭제 - 롤백되면 과제 · 추가 첨부 · 파일이 모두 남고, 커밋되면 모두 지워짐")
	void deleteAssignmentRolledBackAndCommitted() {
		rollbackAfter(() -> assignmentService.delete(instructor.getId(), assignmentId));
		assertThat(assignmentRepository.findById(assignmentId)).isPresent();
		assertThat(extras()).hasSize(2);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);

		assignmentService.delete(instructor.getId(), assignmentId);
		assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
		assertThat(extras()).isEmpty();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("제출 - 추가 첨부 롤백이면 제출도 파일도 남지 않고, 커밋이면 저장됨")
	void submissionRolledBackAndCommitted() {
		rollbackAfter(() -> submissionService.submit(student.getId(), assignmentId,
				submissionRequest(pdf("extraFiles", "롤백.pdf"))));
		assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())).isEmpty();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(3);

		submissionService.submit(student.getId(), assignmentId, submissionRequest(pdf("extraFiles", "커밋.pdf")));
		Long submissionId = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())
				.orElseThrow().getId();
		assertThat(attachmentRepository.findBySubmissionIdOrderByIdAsc(submissionId)).hasSize(1);
		assertThat(UploadTestSupport.fileCount()).isEqualTo(4);
	}

	@Test
	@DisplayName("제출의 추가 첨부 삭제 - 커밋되면 파일이 지워짐")
	void submissionRemoveCommitted() {
		submissionService.submit(student.getId(), assignmentId, submissionRequest(pdf("extraFiles", "내파일.pdf")));
		Long submissionId = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())
				.orElseThrow().getId();
		Attachment attachment = attachmentRepository.findBySubmissionIdOrderByIdAsc(submissionId).getFirst();

		attachmentService.removeFromSubmission(student.getId(), assignmentId, attachment.getId());

		assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
		assertThat(fileStorage.exists(attachment.getStoredName())).isFalse();
	}
}
