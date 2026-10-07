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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentFileResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.exception.AttachmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.support.UploadTestSupport;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * I3-b 첨부파일 다운로드 권한: 해당 강좌의 강사(B5)와 수강 등록한 학생(B2)만 받을 수 있다.
 */
@SpringBootTest
@Transactional
class AssignmentFileServiceImplTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 9, 0);

	@Autowired
	private AssignmentFileService assignmentFileService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

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

	private Assignment assignmentWithFile(String originalName, String content) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle("1주차");
		request.setContent("내용");
		request.setStartAt(START);
		request.setEndAt(START.plusDays(7));
		request.setMaxScore(100);
		request.setFile(new MockMultipartFile("file", originalName, "application/octet-stream",
				content.getBytes(StandardCharsets.UTF_8)));
		Long id = assignmentService.create(instructor.getId(), course.getId(), request).id();
		return assignmentRepository.findById(id).orElseThrow();
	}

	private Assignment assignmentWithoutFile() {
		return assignmentRepository.save(Assignment.builder().course(course).title("첨부 없음").content("내용")
				.startAt(START).endAt(START.plusDays(7)).maxScore(100).build());
	}

	private String read(AssignmentFileResponse response) throws Exception {
		try (var in = response.resource().getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("getFile - 해당 강좌의 강사는 원본 이름과 함께 받을 수 있음")
	void getFile_ownerInstructor() throws Exception {
		Assignment assignment = assignmentWithFile("과제안내.pdf", "안내 내용");

		AssignmentFileResponse response = assignmentFileService.getFile(instructor.getId(), Role.INSTRUCTOR, assignment.getId());

		assertThat(response.originalFilename()).isEqualTo("과제안내.pdf");
		assertThat(read(response)).isEqualTo("안내 내용");
	}

	@Test
	@DisplayName("getFile - 수강 등록한 학생은 받을 수 있음 (B2)")
	void getFile_enrolledStudent() throws Exception {
		Assignment assignment = assignmentWithFile("과제안내.pdf", "안내 내용");
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());

		AssignmentFileResponse response = assignmentFileService.getFile(student.getId(), Role.STUDENT, assignment.getId());

		assertThat(read(response)).isEqualTo("안내 내용");
	}

	@Test
	@DisplayName("getFile - 수강 등록하지 않은 학생은 403 (B2)")
	void getFile_notEnrolledStudent() {
		Assignment assignment = assignmentWithFile("비공개.pdf", "비밀");
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");

		assertThatThrownBy(() -> assignmentFileService.getFile(student.getId(), Role.STUDENT, assignment.getId()))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("getFile - 다른 학생만 수강하는 강좌의 첨부는 403 (B2)")
	void getFile_otherStudentEnrolled() {
		Assignment assignment = assignmentWithFile("비공개.pdf", "비밀");
		User me = savedUser("me@test.com", Role.STUDENT, "20260001");
		User other = savedUser("other@test.com", Role.STUDENT, "20260002");
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());

		assertThatThrownBy(() -> assignmentFileService.getFile(me.getId(), Role.STUDENT, assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("getFile - 다른 강사는 403 (B5)")
	void getFile_otherInstructor() {
		Assignment assignment = assignmentWithFile("비공개.pdf", "비밀");
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);

		assertThatThrownBy(() -> assignmentFileService.getFile(other.getId(), Role.INSTRUCTOR, assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("getFile - 역할을 속여도 소속이 다르면 403 (학생 id로 강사 역할 요청)")
	void getFile_roleMismatch() {
		Assignment assignment = assignmentWithFile("비공개.pdf", "비밀");
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");

		assertThatThrownBy(() -> assignmentFileService.getFile(student.getId(), Role.INSTRUCTOR, assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("getFile - 첨부가 없는 과제는 404")
	void getFile_noAttachment() {
		Assignment assignment = assignmentWithoutFile();

		assertThatThrownBy(() -> assignmentFileService.getFile(instructor.getId(), Role.INSTRUCTOR, assignment.getId()))
				.isInstanceOf(AttachmentNotFoundException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("getFile - 권한 확인이 먼저라서 권한 없는 사용자에게는 첨부 유무를 알려 주지 않음 (404가 아니라 403)")
	void getFile_permissionBeforeExistence() {
		Assignment assignment = assignmentWithoutFile();
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");

		assertThatThrownBy(() -> assignmentFileService.getFile(student.getId(), Role.STUDENT, assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("getFile - 존재하지 않는 과제는 404")
	void getFile_assignmentNotFound() {
		assertThatThrownBy(() -> assignmentFileService.getFile(instructor.getId(), Role.INSTRUCTOR, 999_999L))
				.isInstanceOf(AssignmentNotFoundException.class);
	}

	@Test
	@DisplayName("getFile - DB에는 첨부가 있지만 실제 파일이 사라졌으면 404")
	void getFile_fileMissingOnDisk() {
		Assignment assignment = assignmentWithFile("a.pdf", "x");
		fileStorage.deleteQuietly(assignment.getFilePath());

		assertThatThrownBy(() -> assignmentFileService.getFile(instructor.getId(), Role.INSTRUCTOR, assignment.getId()))
				.isInstanceOf(AttachmentNotFoundException.class);
	}
}
