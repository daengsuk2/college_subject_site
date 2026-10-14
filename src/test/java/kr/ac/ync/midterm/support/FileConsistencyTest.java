package kr.ac.ync.midterm.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.assignment.service.AssignmentService;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 파일과 DB의 일관성 (X22): 교체 · 삭제 · 업로드가 DB 커밋에 실패(롤백)하면 이전 파일은 남고 새 파일은 정리되며,
 * 커밋되면 이전 파일만 지워지는지 확인한다. 커밋 뒤 동작이라 데이터를 실제로 커밋하고 끝난 뒤 직접 정리한다.
 */
@SpringBootTest
class FileConsistencyTest {

	private static final String TEACHER = "cons-teacher@test.com";
	private static final String STUDENT = "cons-student@test.com";

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

	private User instructor;
	private User student;
	private Course course;
	private Long assignmentId;

	@BeforeEach
	void setUp() {
		cleanData();
		UploadTestSupport.clean();

		instructor = savedUser(TEACHER, Role.INSTRUCTOR, null);
		student = savedUser(STUDENT, Role.STUDENT, "20269981");
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("파일 일관성").joinCode("CONS22").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		assignmentId = assignmentService.create(instructor.getId(), course.getId(), request(pdf("안내.pdf", "과제 안내")))
				.id();
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
		userRepository.findByEmail(STUDENT).ifPresent(userRepository::delete);
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(User.builder().email(email).password(passwordEncoder.encode("password123"))
				.name("일관성").studentNo(studentNo).role(role).build());
	}

	private MockMultipartFile pdf(String name, String content) {
		return new MockMultipartFile("file", name, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
	}

	private AssignmentRequest request(MockMultipartFile file) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle("첨부 있는 과제");
		request.setContent("과제 내용");
		request.setStartAt(LocalDateTime.now().minusDays(1));
		request.setEndAt(LocalDateTime.now().plusDays(1));
		request.setMaxScore(100);
		request.setFile(file);
		return request;
	}

	private SubmissionRequest submissionRequest(MockMultipartFile file) {
		SubmissionRequest request = new SubmissionRequest();
		request.setContent("답안");
		request.setFile(file);
		return request;
	}

	private Assignment assignment() {
		return assignmentRepository.findById(assignmentId).orElseThrow();
	}

	private Submission submission() {
		return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElseThrow();
	}

	// 서비스 호출은 바깥 트랜잭션에 참여하고, 바깥에서 예외가 나면 전체가 롤백된다 (커밋 단계 실패를 흉내 낸다)
	private void rollbackAfter(Runnable work) {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			work.run();
			throw new IllegalStateException("커밋 단계의 DB 오류");
		})).isInstanceOf(IllegalStateException.class);
	}

	// ---------- 과제 첨부 ----------

	@Test
	@DisplayName("과제 수정(교체) 커밋 - 이전 파일은 지워지고 새 파일만 남음")
	void assignment_replaceCommitted() {
		String old = assignment().getFilePath();

		assignmentService.update(instructor.getId(), assignmentId, request(pdf("새.pdf", "새 파일")));

		String now = assignment().getFilePath();
		assertThat(now).isNotEqualTo(old);
		assertThat(fileStorage.exists(old)).isFalse();
		assertThat(fileStorage.exists(now)).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("과제 수정(교체) 롤백 - 이전 파일과 DB 정보는 그대로고 새로 올린 파일은 정리됨")
	void assignment_replaceRolledBack() {
		String old = assignment().getFilePath();

		rollbackAfter(() -> assignmentService.update(instructor.getId(), assignmentId, request(pdf("새.pdf", "새 파일"))));

		assertThat(assignment().getFilePath()).isEqualTo(old);
		assertThat(assignment().getOriginalFilename()).isEqualTo("안내.pdf");
		assertThat(fileStorage.exists(old)).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("과제 삭제 롤백 - 과제와 파일이 모두 남음 / 커밋 - 첨부 파일도 지워짐")
	void assignment_deleteRolledBackAndCommitted() {
		String stored = assignment().getFilePath();

		rollbackAfter(() -> assignmentService.delete(instructor.getId(), assignmentId));
		assertThat(assignmentRepository.findById(assignmentId)).isPresent();
		assertThat(fileStorage.exists(stored)).isTrue();

		assignmentService.delete(instructor.getId(), assignmentId);
		assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
		assertThat(fileStorage.exists(stored)).isFalse();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("과제 생성 롤백 - 올린 파일이 서버에 남지 않음 / 커밋 - 파일이 남음")
	void assignment_createRolledBackAndCommitted() {
		UploadTestSupport.clean();

		rollbackAfter(() -> assignmentService.create(instructor.getId(), course.getId(), request(pdf("a.pdf", "롤백"))));
		assertThat(UploadTestSupport.fileCount()).isZero();

		assignmentService.create(instructor.getId(), course.getId(), request(pdf("b.pdf", "커밋")));
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("파일을 올리지 않은 수정은 기존 파일을 그대로 두고, 롤백돼도 마찬가지임")
	void assignment_keepWhenNoNewFile() {
		String old = assignment().getFilePath();

		rollbackAfter(() -> assignmentService.update(instructor.getId(), assignmentId, request(null)));
		assertThat(fileStorage.exists(old)).isTrue();

		assignmentService.update(instructor.getId(), assignmentId, request(null));
		assertThat(assignment().getFilePath()).isEqualTo(old);
		assertThat(fileStorage.exists(old)).isTrue();
	}

	// ---------- 제출 파일 ----------

	@Test
	@DisplayName("첫 제출 롤백 - 올린 파일이 정리되고 제출도 없음 / 커밋 - 제출과 파일이 남음")
	void submission_firstRolledBackAndCommitted() {
		rollbackAfter(() -> submissionService.submit(student.getId(), assignmentId,
				submissionRequest(pdf("답안.pdf", "롤백"))));
		assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())).isEmpty();
		// 과제 안내 파일 1개만 남는다
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		submissionService.submit(student.getId(), assignmentId, submissionRequest(pdf("답안.pdf", "커밋")));
		assertThat(fileStorage.exists(submission().getFilePath())).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("재제출(교체) 롤백 - 이전 파일이 남고 새 파일은 정리됨 / 커밋 - 이전 제출 파일이 지워짐")
	void submission_resubmit() {
		submissionService.submit(student.getId(), assignmentId, submissionRequest(pdf("첫.pdf", "첫 파일")));
		String first = submission().getFilePath();

		rollbackAfter(() -> submissionService.submit(student.getId(), assignmentId,
				submissionRequest(pdf("둘.pdf", "둘째 파일"))));
		assertThat(submission().getFilePath()).isEqualTo(first);
		assertThat(fileStorage.exists(first)).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(2);

		submissionService.submit(student.getId(), assignmentId, submissionRequest(pdf("셋.pdf", "셋째 파일")));
		String third = submission().getFilePath();
		assertThat(third).isNotEqualTo(first);
		assertThat(fileStorage.exists(first)).isFalse();
		assertThat(fileStorage.exists(third)).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("재제출에서 파일을 올리지 않으면 이전 제출 파일이 유지됨")
	void submission_resubmitWithoutFile() {
		submissionService.submit(student.getId(), assignmentId, submissionRequest(pdf("첫.pdf", "첫 파일")));
		String first = submission().getFilePath();

		submissionService.submit(student.getId(), assignmentId, submissionRequest(null));

		assertThat(submission().getFilePath()).isEqualTo(first);
		assertThat(fileStorage.exists(first)).isTrue();
	}

	// ---------- 동기화 ----------

	@Test
	@DisplayName("deleteOnRollback - 롤백되면 파일이 지워지고, 커밋되면 남으며, 트랜잭션 밖 · null은 아무 일도 하지 않음")
	void deleteOnRollback() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		String kept = fileStorage.store(pdf("a.pdf", "커밋")).storedName();
		tx.executeWithoutResult(status -> fileStorage.deleteOnRollback(kept));
		assertThat(fileStorage.exists(kept)).isTrue();

		String removed = fileStorage.store(pdf("b.pdf", "롤백")).storedName();
		rollbackAfter(() -> fileStorage.deleteOnRollback(removed));
		assertThat(fileStorage.exists(removed)).isFalse();

		String outside = fileStorage.store(pdf("c.pdf", "밖")).storedName();
		fileStorage.deleteOnRollback(outside);
		assertThat(fileStorage.exists(outside)).isTrue();

		fileStorage.deleteOnRollback(null);
	}
}
