package kr.ac.ync.midterm.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.exception.AlreadySubmittedException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 저장이 실패했을 때의 처리: 방금 올린 파일이 서버에 남지 않는지, 같은 학생의 동시 제출 충돌이 안내로 바뀌는지 확인한다.
 * DB 오류는 실제 DB에서 일으키므로(UNIQUE 충돌) 이 테스트는 데이터를 커밋하고 끝난 뒤 직접 정리한다.
 * 동시성 자체(두 요청이 정말 동시에 실행되는 상황)는 재현하지 않고, "이미 제출이 있는데 서비스가 못 본 상태"를 만들어
 * 충돌 예외가 났을 때의 동작을 확인한다.
 */
@SpringBootTest
class UploadFailureCleanupTest {

	private static final String TEACHER_EMAIL = "cleanup-teacher@test.com";
	private static final String STUDENT_EMAIL = "cleanup-student@test.com";

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

	@MockitoSpyBean
	private SubmissionRepository submissionRepository;

	@Autowired
	private SubmissionService submissionService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;
	private User instructor;
	private User student;
	private Course course;
	private Assignment assignment;

	@BeforeEach
	void setUp() {
		cleanData();
		UploadTestSupport.clean();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

		instructor = userRepository.save(User.builder().email(TEACHER_EMAIL)
				.password(passwordEncoder.encode("password123")).name("정리확인강사").role(Role.INSTRUCTOR).build());
		student = userRepository.save(User.builder().email(STUDENT_EMAIL)
				.password(passwordEncoder.encode("password123")).name("정리확인학생").studentNo("20269998")
				.role(Role.STUDENT).build());
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("정리 확인").joinCode("CLEAN2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("정리 확인 과제")
				.content("내용").startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
	}

	@AfterEach
	void tearDown() {
		cleanData();
		UploadTestSupport.clean();
	}

	// 이 테스트가 커밋한 데이터를 지운다 (다른 테스트와 섞이지 않게)
	private void cleanData() {
		userRepository.findByEmail(TEACHER_EMAIL).ifPresent(teacher -> {
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
		userRepository.findByEmail(STUDENT_EMAIL).ifPresent(userRepository::delete);
	}

	private MockMultipartFile pdf(String name, String content) {
		return new MockMultipartFile("file", name, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
	}

	private SubmissionRequest submissionRequest(String content, MockMultipartFile file) {
		SubmissionRequest request = new SubmissionRequest();
		request.setContent(content);
		request.setFile(file);
		return request;
	}

	private Submission savedSubmission(String content) {
		return submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content(content).submittedAt(LocalDateTime.now().minusMinutes(5)).build());
	}

	/** 이미 제출이 있는데 서비스가 조회하지 못한 상황(동시 제출)을 만든다 */
	private void serviceMissesExistingSubmission() {
		doReturn(Optional.empty()).when(submissionRepository).findByAssignmentIdAndStudentId(anyLong(), anyLong());
	}

	private AssignmentRequest assignmentRequest(String title, MockMultipartFile file) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle(title);
		request.setContent("내용");
		request.setStartAt(LocalDateTime.of(2026, 10, 10, 9, 0));
		request.setEndAt(LocalDateTime.of(2026, 10, 17, 23, 59));
		request.setMaxScore(100);
		request.setFile(file);
		return request;
	}

	// ---------- 동시 제출 충돌 (B3) ----------

	@Test
	@DisplayName("동시 제출로 UNIQUE에 걸리면 안내 예외가 나고, 기존 제출은 그대로이며 올린 파일이 남지 않음")
	void conflict_service() {
		savedSubmission("먼저 저장된 제출");
		serviceMissesExistingSubmission();

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(),
				submissionRequest("뒤따라 온 제출", pdf("late.pdf", "파일"))))
				.isInstanceOf(AlreadySubmittedException.class)
				.hasMessage("이미 제출되었습니다. 화면을 새로 고쳐 확인해 주세요.");

		assertThat(submissionRepository.count()).isEqualTo(1);
		assertThat(submissionRepository.findAll().getFirst().getContent()).isEqualTo("먼저 저장된 제출");
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	@Test
	@DisplayName("동시 제출 충돌은 오류 화면이 아니라 과제 상세로 돌아가 안내 문구를 보여 줌")
	void conflict_controller() throws Exception {
		savedSubmission("먼저 저장된 제출");
		serviceMissesExistingSubmission();

		mockMvc.perform(multipart("/student/assignments/" + assignment.getId() + "/submit")
						.file(pdf("late.pdf", "파일")).param("content", "뒤따라 온 제출")
						.with(user(new CustomUserDetails(student))).with(csrf()))
				.andExpect(redirectedUrl("/student/assignments/" + assignment.getId()))
				.andExpect(flash().attribute("error", "이미 제출되었습니다. 화면을 새로 고쳐 확인해 주세요."));

		assertThat(submissionRepository.count()).isEqualTo(1);
		assertThat(UploadTestSupport.fileCount()).isZero();
	}

	// ---------- 저장 실패 시 방금 올린 파일 정리 ----------

	@Test
	@DisplayName("제출 저장이 실패하면 방금 올린 파일이 서버에 남지 않음")
	void submit_saveFails_fileRemoved() {
		doThrow(new IllegalStateException("DB 오류")).when(submissionRepository).save(any(Submission.class));

		assertThatThrownBy(() -> submissionService.submit(student.getId(), assignment.getId(),
				submissionRequest("제출", pdf("a.pdf", "파일"))))
				.isInstanceOf(IllegalStateException.class);

		assertThat(UploadTestSupport.fileCount()).isZero();
		assertThat(submissionRepository.count()).isZero();
	}

	@Test
	@DisplayName("과제 등록 저장이 실패하면 방금 올린 첨부파일이 서버에 남지 않음")
	void createAssignment_saveFails_fileRemoved() {
		// 제목이 null이면 과제를 만드는 중에 실패한다 (폼 검증을 거치지 않고 서비스를 직접 호출하는 경우)
		assertThatThrownBy(() -> assignmentService.create(instructor.getId(), course.getId(),
				assignmentRequest(null, pdf("guide.pdf", "안내"))))
				.isInstanceOf(RuntimeException.class);

		assertThat(UploadTestSupport.fileCount()).isZero();
		assertThat(assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()))
				.extracting(Assignment::getTitle).containsExactly("정리 확인 과제");
	}

	@Test
	@DisplayName("과제 수정이 실패하면 새로 올린 파일은 지워지고 기존 첨부는 그대로 남음")
	void updateAssignment_fails_oldFileKept() {
		Long id = assignmentService.create(instructor.getId(), course.getId(),
				assignmentRequest("첨부 있는 과제", pdf("old.pdf", "기존 파일"))).id();
		String oldStoredName = assignmentRepository.findById(id).orElseThrow().getFilePath();
		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);

		assertThatThrownBy(() -> assignmentService.update(instructor.getId(), id,
				assignmentRequest(null, pdf("new.pdf", "새 파일"))))
				.isInstanceOf(RuntimeException.class);

		assertThat(UploadTestSupport.fileCount()).isEqualTo(1);
		Assignment after = assignmentRepository.findById(id).orElseThrow();
		assertThat(after.getFilePath()).isEqualTo(oldStoredName);
		assertThat(after.getOriginalFilename()).isEqualTo("old.pdf");
	}
}
