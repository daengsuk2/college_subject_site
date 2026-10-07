package kr.ac.ync.midterm.assignment.service;

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
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentHasSubmissionsException;
import kr.ac.ync.midterm.assignment.exception.InvalidAssignmentPeriodException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.global.storage.InvalidFileException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.UploadTestSupport;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * I3-b 첨부파일: 과제 등록 · 수정 · 삭제와 파일 저장이 함께 움직이는지 확인한다.
 */
@SpringBootTest
@Transactional
class AssignmentServiceFileTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 9, 0);
	private static final LocalDateTime END = LocalDateTime.of(2026, 10, 17, 23, 59);

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private FileStorage fileStorage;

	private User instructor;
	private Course course;

	@BeforeEach
	void setUp() {
		UploadTestSupport.clean();
		instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
	}

	@AfterEach
	void tearDown() {
		UploadTestSupport.clean();
	}

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(
				User.builder().email(email).password("encodedPassword").name("홍길동")
						.studentNo(studentNo).role(role).build());
	}

	private MockMultipartFile file(String name, String content) {
		return new MockMultipartFile("file", name, "application/octet-stream",
				content.getBytes(StandardCharsets.UTF_8));
	}

	private AssignmentRequest request(LocalDateTime start, LocalDateTime end, MockMultipartFile file) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle("1주차 과제");
		request.setContent("과제 내용");
		request.setStartAt(start);
		request.setEndAt(end);
		request.setMaxScore(100);
		request.setFile(file);
		return request;
	}

	private Assignment created(MockMultipartFile file) {
		AssignmentResponse response = assignmentService.create(instructor.getId(), course.getId(),
				request(START, END, file));
		return assignmentRepository.findById(response.id()).orElseThrow();
	}

	// ---------- 등록 ----------

	@Test
	@DisplayName("create - 파일을 첨부하면 UUID 이름으로 저장되고 원본 이름은 DB에 보관됨")
	void create_withFile() {
		// when
		Assignment saved = created(file("과제안내.pdf", "안내 내용"));

		// then
		assertThat(saved.getOriginalFilename()).isEqualTo("과제안내.pdf");
		assertThat(saved.getFilePath()).matches("[0-9a-f-]{36}\\.pdf").doesNotContain("과제안내");
		assertThat(fileStorage.exists(saved.getFilePath())).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("create - 첨부 없이도 등록됨 (파일 없음)")
	void create_withoutFile() {
		// when
		Assignment saved = created(null);

		// then
		assertThat(saved.hasFile()).isFalse();
		assertThat(saved.getOriginalFilename()).isNull();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("create - 허용하지 않는 확장자는 거부되고 과제도 파일도 저장되지 않음")
	void create_invalidExtension() {
		assertThatThrownBy(() -> assignmentService.create(instructor.getId(), course.getId(),
				request(START, END, file("virus.exe", "x"))))
				.isInstanceOf(InvalidFileException.class);

		assertThat(assignmentRepository.count()).isZero();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("create - 기간이 잘못되면(B7) 파일은 저장되지 않음")
	void create_invalidPeriodStoresNoFile() {
		assertThatThrownBy(() -> assignmentService.create(instructor.getId(), course.getId(),
				request(END, START, file("a.pdf", "x"))))
				.isInstanceOf(InvalidAssignmentPeriodException.class);

		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("create - 다른 강사의 강좌면(B5) 파일은 저장되지 않음")
	void create_otherInstructorStoresNoFile() {
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);

		assertThatThrownBy(() -> assignmentService.create(other.getId(), course.getId(),
				request(START, END, file("a.pdf", "x"))))
				.isInstanceOf(ForbiddenException.class);

		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	// ---------- 수정 ----------

	@Test
	@DisplayName("update - 새 파일을 올리면 첨부가 교체되고 기존 파일은 삭제됨")
	void update_replaceFile() {
		// given
		Assignment assignment = created(file("old.pdf", "옛날 파일"));
		String oldStoredName = assignment.getFilePath();

		// when
		assignmentService.update(instructor.getId(), assignment.getId(), request(START, END, file("new.pdf", "새 파일")));

		// then
		Assignment saved = assignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(saved.getOriginalFilename()).isEqualTo("new.pdf");
		assertThat(saved.getFilePath()).isNotEqualTo(oldStoredName);
		assertThat(fileStorage.exists(saved.getFilePath())).isTrue();
		assertThat(fileStorage.exists(oldStoredName)).isFalse();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("update - 파일을 올리지 않으면 기존 첨부가 유지됨")
	void update_keepFile() {
		// given
		Assignment assignment = created(file("keep.pdf", "유지"));
		String storedName = assignment.getFilePath();

		// when
		assignmentService.update(instructor.getId(), assignment.getId(), request(START, END, null));

		// then
		Assignment saved = assignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(saved.getFilePath()).isEqualTo(storedName);
		assertThat(saved.getOriginalFilename()).isEqualTo("keep.pdf");
		assertThat(fileStorage.exists(storedName)).isTrue();
	}

	@Test
	@DisplayName("update - 새 파일이 거부되면 기존 첨부가 그대로 남음")
	void update_invalidNewFileKeepsOld() {
		// given
		Assignment assignment = created(file("old.pdf", "옛날 파일"));
		String oldStoredName = assignment.getFilePath();

		// when & then
		assertThatThrownBy(() -> assignmentService.update(instructor.getId(), assignment.getId(),
				request(START, END, file("virus.exe", "x"))))
				.isInstanceOf(InvalidFileException.class);

		Assignment saved = assignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(saved.getFilePath()).isEqualTo(oldStoredName);
		assertThat(fileStorage.exists(oldStoredName)).isTrue();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("update - 다른 강사는 첨부를 교체할 수 없음 (B5, 파일 저장 안 됨)")
	void update_otherInstructor() {
		// given
		Assignment assignment = created(file("old.pdf", "옛날 파일"));
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);

		// when & then
		assertThatThrownBy(() -> assignmentService.update(other.getId(), assignment.getId(),
				request(START, END, file("hack.pdf", "x"))))
				.isInstanceOf(ForbiddenException.class);

		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getOriginalFilename())
				.isEqualTo("old.pdf");
	}

	// ---------- 삭제 ----------

	@Test
	@DisplayName("delete - 과제를 삭제하면 첨부 파일도 삭제됨")
	void delete_removesFile() {
		// given
		Assignment assignment = created(file("a.pdf", "x"));
		String storedName = assignment.getFilePath();

		// when
		assignmentService.delete(instructor.getId(), assignment.getId());

		// then
		assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
		assertThat(fileStorage.exists(storedName)).isFalse();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("delete - 제출이 있어 삭제할 수 없으면(B8) 첨부 파일도 그대로 남음")
	void delete_blockedBySubmissionKeepsFile() {
		// given
		Assignment assignment = created(file("a.pdf", "x"));
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		submissionRepository.saveAndFlush(
				Submission.builder().assignment(assignment).student(student).content("제출물").build());

		// when & then
		assertThatThrownBy(() -> assignmentService.delete(instructor.getId(), assignment.getId()))
				.isInstanceOf(AssignmentHasSubmissionsException.class);
		assertThat(fileStorage.exists(assignment.getFilePath())).isTrue();
	}

	@Test
	@DisplayName("delete - 다른 강사는 삭제할 수 없고(B5) 파일도 그대로 남음")
	void delete_otherInstructorKeepsFile() {
		// given
		Assignment assignment = created(file("a.pdf", "x"));
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);

		// when & then
		assertThatThrownBy(() -> assignmentService.delete(other.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
		assertThat(fileStorage.exists(assignment.getFilePath())).isTrue();
	}
}
