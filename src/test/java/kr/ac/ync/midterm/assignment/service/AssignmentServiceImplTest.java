package kr.ac.ync.midterm.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentHasSubmissionsException;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.exception.InvalidAssignmentPeriodException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.exception.CourseNotFoundException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class AssignmentServiceImplTest {

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

	private User savedUser(String email, Role role, String studentNo) {
		return userRepository.save(
				User.builder()
						.email(email)
						.password("encodedPassword")
						.name("홍길동")
						.studentNo(studentNo)
						.role(role)
						.build()
		);
	}

	private Course savedCourse(User instructor, String name, String joinCode) {
		return courseRepository.save(
				Course.builder().instructor(instructor).name(name).joinCode(joinCode).build()
		);
	}

	private Assignment savedAssignment(Course course, String title, LocalDateTime startAt) {
		return assignmentRepository.save(
				Assignment.builder()
						.course(course)
						.title(title)
						.content("내용")
						.startAt(startAt)
						.endAt(startAt.plusDays(7))
						.maxScore(100)
						.build()
		);
	}

	private AssignmentRequest request(String title, LocalDateTime startAt, LocalDateTime endAt, Integer maxScore) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle(title);
		request.setContent("과제 내용");
		request.setStartAt(startAt);
		request.setEndAt(endAt);
		request.setMaxScore(maxScore);
		return request;
	}

	// ---------- 등록 ----------

	@Test
	@DisplayName("create - 본인 강좌에 과제가 등록됨 (제목 · 내용 공백은 제거)")
	void create_success() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");

		// when
		AssignmentResponse response = assignmentService.create(
				instructor.getId(), course.getId(), request("  1주차 과제  ", START, END, 100));

		// then
		assertThat(response.id()).isNotNull();
		assertThat(response.courseId()).isEqualTo(course.getId());
		assertThat(response.title()).isEqualTo("1주차 과제");
		assertThat(response.startAt()).isEqualTo(START);
		assertThat(response.endAt()).isEqualTo(END);
		assertThat(response.maxScore()).isEqualTo(100);
		assertThat(response.createdAt()).isNotNull();
		assertThat(assignmentRepository.findById(response.id())).isPresent();
	}

	@Test
	@DisplayName("create - 종료일시가 시작일시보다 이르면 예외 (B7, 저장 안 됨)")
	void create_endBeforeStart() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");

		// when & then
		assertThatThrownBy(() -> assignmentService.create(
				instructor.getId(), course.getId(), request("과제", END, START, 100)))
				.isInstanceOf(InvalidAssignmentPeriodException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
		assertThat(assignmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("create - 종료일시가 시작일시와 같아도 예외 (B7)")
	void create_endEqualsStart() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");

		// when & then
		assertThatThrownBy(() -> assignmentService.create(
				instructor.getId(), course.getId(), request("과제", START, START, 100)))
				.isInstanceOf(InvalidAssignmentPeriodException.class);
	}

	@Test
	@DisplayName("create - 다른 강사의 강좌에는 등록할 수 없음 (B5, 403)")
	void create_otherInstructor() {
		// given
		User owner = savedUser("owner@test.com", Role.INSTRUCTOR, null);
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(owner, "자바스프링", "AAAAA2");

		// when & then
		assertThatThrownBy(() -> assignmentService.create(
				other.getId(), course.getId(), request("과제", START, END, 100)))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
		assertThat(assignmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("create - 존재하지 않는 강좌면 404")
	void create_courseNotFound() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);

		// when & then
		assertThatThrownBy(() -> assignmentService.create(
				instructor.getId(), 999_999L, request("과제", START, END, 100)))
				.isInstanceOf(CourseNotFoundException.class);
	}

	// ---------- 조회 ----------

	@Test
	@DisplayName("findMyAssignments - 해당 강좌의 과제만, 시작일시가 늦은 순으로 조회됨")
	void findMyAssignments_onlyThatCourseNewestFirst() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Course java = savedCourse(instructor, "자바스프링", "AAAAA2");
		Course db = savedCourse(instructor, "데이터베이스", "BBBBB3");
		savedAssignment(java, "1주차", START);
		savedAssignment(java, "2주차", START.plusDays(7));
		savedAssignment(db, "DB 과제", START);

		// when
		List<AssignmentResponse> result = assignmentService.findMyAssignments(instructor.getId(), java.getId());

		// then
		assertThat(result).extracting(AssignmentResponse::title).containsExactly("2주차", "1주차");
	}

	@Test
	@DisplayName("findMyAssignments - 다른 강사의 강좌는 403 (B5)")
	void findMyAssignments_otherInstructor() {
		// given
		User owner = savedUser("owner@test.com", Role.INSTRUCTOR, null);
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);
		Course course = savedCourse(owner, "자바스프링", "AAAAA2");

		// when & then
		assertThatThrownBy(() -> assignmentService.findMyAssignments(other.getId(), course.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	@DisplayName("findMyAssignment - 존재하지 않는 과제면 404")
	void findMyAssignment_notFound() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);

		// when & then
		assertThatThrownBy(() -> assignmentService.findMyAssignment(instructor.getId(), 999_999L))
				.isInstanceOf(AssignmentNotFoundException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("findMyAssignment - 다른 강사 강좌의 과제는 403 (B5)")
	void findMyAssignment_otherInstructor() {
		// given
		User owner = savedUser("owner@test.com", Role.INSTRUCTOR, null);
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);
		Assignment assignment = savedAssignment(savedCourse(owner, "자바스프링", "AAAAA2"), "1주차", START);

		// when & then
		assertThatThrownBy(() -> assignmentService.findMyAssignment(other.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
	}

	// ---------- 수정 ----------

	@Test
	@DisplayName("update - 과제 내용이 수정됨")
	void update_success() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Assignment assignment = savedAssignment(savedCourse(instructor, "자바스프링", "AAAAA2"), "1주차", START);
		LocalDateTime newStart = START.plusDays(1);
		LocalDateTime newEnd = END.plusDays(1);

		// when
		AssignmentResponse response = assignmentService.update(
				instructor.getId(), assignment.getId(), request("수정된 제목", newStart, newEnd, 50));

		// then
		assertThat(response.title()).isEqualTo("수정된 제목");
		Assignment saved = assignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("수정된 제목");
		assertThat(saved.getStartAt()).isEqualTo(newStart);
		assertThat(saved.getEndAt()).isEqualTo(newEnd);
		assertThat(saved.getMaxScore()).isEqualTo(50);
	}

	@Test
	@DisplayName("update - 수정할 때도 종료일시가 시작일시보다 이르면 예외 (B7, 변경 안 됨)")
	void update_endBeforeStart() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Assignment assignment = savedAssignment(savedCourse(instructor, "자바스프링", "AAAAA2"), "1주차", START);

		// when & then
		assertThatThrownBy(() -> assignmentService.update(
				instructor.getId(), assignment.getId(), request("바꾼 제목", END, START, 10)))
				.isInstanceOf(InvalidAssignmentPeriodException.class);
		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getTitle()).isEqualTo("1주차");
	}

	@Test
	@DisplayName("update - 다른 강사의 과제는 수정할 수 없음 (B5, 변경 안 됨)")
	void update_otherInstructor() {
		// given
		User owner = savedUser("owner@test.com", Role.INSTRUCTOR, null);
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);
		Assignment assignment = savedAssignment(savedCourse(owner, "자바스프링", "AAAAA2"), "1주차", START);

		// when & then
		assertThatThrownBy(() -> assignmentService.update(
				other.getId(), assignment.getId(), request("가로챈 제목", START, END, 10)))
				.isInstanceOf(ForbiddenException.class);
		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getTitle()).isEqualTo("1주차");
	}

	// ---------- 삭제 ----------

	@Test
	@DisplayName("delete - 제출이 없는 과제는 삭제됨")
	void delete_success() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		Assignment assignment = savedAssignment(savedCourse(instructor, "자바스프링", "AAAAA2"), "1주차", START);

		// when
		assignmentService.delete(instructor.getId(), assignment.getId());

		// then
		assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
	}

	@Test
	@DisplayName("delete - 제출이 1건이라도 있으면 삭제할 수 없음 (B8)")
	void delete_withSubmission() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);
		User student = savedUser("student@test.com", Role.STUDENT, "20260001");
		Assignment assignment = savedAssignment(savedCourse(instructor, "자바스프링", "AAAAA2"), "1주차", START);
		submissionRepository.saveAndFlush(
				Submission.builder().assignment(assignment).student(student).content("제출물").build());

		// when & then
		assertThatThrownBy(() -> assignmentService.delete(instructor.getId(), assignment.getId()))
				.isInstanceOf(AssignmentHasSubmissionsException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
		assertThat(assignmentRepository.findById(assignment.getId())).isPresent();
		assertThat(submissionRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("delete - 다른 강사의 과제는 삭제할 수 없음 (B5)")
	void delete_otherInstructor() {
		// given
		User owner = savedUser("owner@test.com", Role.INSTRUCTOR, null);
		User other = savedUser("other@test.com", Role.INSTRUCTOR, null);
		Assignment assignment = savedAssignment(savedCourse(owner, "자바스프링", "AAAAA2"), "1주차", START);

		// when & then
		assertThatThrownBy(() -> assignmentService.delete(other.getId(), assignment.getId()))
				.isInstanceOf(ForbiddenException.class);
		assertThat(assignmentRepository.findById(assignment.getId())).isPresent();
	}

	@Test
	@DisplayName("delete - 존재하지 않는 과제면 404")
	void delete_notFound() {
		// given
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null);

		// when & then
		assertThatThrownBy(() -> assignmentService.delete(instructor.getId(), 999_999L))
				.isInstanceOf(AssignmentNotFoundException.class);
	}
}
