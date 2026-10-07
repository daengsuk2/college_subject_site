package kr.ac.ync.midterm.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.dto.request.CourseCreateRequest;
import kr.ac.ync.midterm.course.dto.response.CourseDetailResponse;
import kr.ac.ync.midterm.course.dto.response.CourseResponse;
import kr.ac.ync.midterm.course.exception.CourseNotFoundException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class CourseServiceImplTest {

	@Autowired
	private CourseService courseService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	private User savedUser(String email, String name, Role role, String studentNo) {
		return userRepository.save(
				User.builder()
						.email(email)
						.password("encodedPassword")
						.name(name)
						.studentNo(studentNo)
						.role(role)
						.build()
		);
	}

	private Course savedCourse(User instructor, String name, String joinCode) {
		return courseRepository.save(
				Course.builder()
						.instructor(instructor)
						.name(name)
						.joinCode(joinCode)
						.build()
		);
	}

	private void enroll(Course course, User student) {
		// saveAndFlush: 등록 시각(joinedAt)이 학생마다 다르게 찍히도록 바로 저장
		enrollmentRepository.saveAndFlush(
				Enrollment.builder().course(course).student(student).build()
		);
	}

	private CourseCreateRequest createRequest(String name) {
		CourseCreateRequest request = new CourseCreateRequest();
		request.setName(name);
		return request;
	}

	// ---------- I1 강좌 개설 ----------

	@Test
	@DisplayName("create - 참여코드가 6자리로 발급되고 개설한 강사가 저장됨")
	void create_success() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		// when
		CourseResponse response = courseService.create(instructor.getId(), createRequest("자바스프링"));
		// then
		assertThat(response.id()).isNotNull();
		assertThat(response.name()).isEqualTo("자바스프링");
		assertThat(response.joinCode()).matches("[A-HJ-NP-Z2-9]{6}");
		assertThat(response.createdAt()).isNotNull();
		Course saved = courseRepository.findById(response.id()).orElseThrow();
		assertThat(saved.getInstructor().getId()).isEqualTo(instructor.getId());
	}

	@Test
	@DisplayName("create - 강좌명 앞뒤 공백은 제거되어 저장됨")
	void create_nameTrimmed() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		// when
		CourseResponse response = courseService.create(instructor.getId(), createRequest("  자바스프링  "));
		// then
		assertThat(response.name()).isEqualTo("자바스프링");
	}

	@Test
	@DisplayName("create - 여러 강좌를 개설해도 참여코드가 중복되지 않음")
	void create_uniqueJoinCodes() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		// when
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 30; i++) {
			codes.add(courseService.create(instructor.getId(), createRequest("강좌" + i)).joinCode());
		}
		// then
		assertThat(codes).hasSize(30);
	}

	@Test
	@DisplayName("findMyCourses - 본인이 개설한 강좌만 조회됨 (B5)")
	void findMyCourses_onlyMine() {
		// given
		User instructorA = savedUser("a@test.com", "강사A", Role.INSTRUCTOR, null);
		User instructorB = savedUser("b@test.com", "강사B", Role.INSTRUCTOR, null);
		courseService.create(instructorA.getId(), createRequest("A의 강좌1"));
		courseService.create(instructorA.getId(), createRequest("A의 강좌2"));
		courseService.create(instructorB.getId(), createRequest("B의 강좌"));
		// when
		List<CourseResponse> mine = courseService.findMyCourses(instructorA.getId());
		// then
		assertThat(mine).extracting(CourseResponse::name).containsExactlyInAnyOrder("A의 강좌1", "A의 강좌2");
	}

	@Test
	@DisplayName("findMyCourses - 최근에 개설한 강좌가 먼저 조회됨")
	void findMyCourses_newestFirst() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		courseService.create(instructor.getId(), createRequest("첫번째"));
		courseService.create(instructor.getId(), createRequest("두번째"));
		// when
		List<CourseResponse> mine = courseService.findMyCourses(instructor.getId());
		// then
		assertThat(mine).extracting(CourseResponse::name).containsExactly("두번째", "첫번째");
	}

	@Test
	@DisplayName("findMyCourses - 개설한 강좌가 없으면 빈 목록")
	void findMyCourses_empty() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		// when & then
		assertThat(courseService.findMyCourses(instructor.getId())).isEmpty();
	}

	// ---------- I2 강좌 상세 · 수강생 목록 ----------

	@Test
	@DisplayName("findMyCourseDetail - 본인 강좌의 수강생이 등록한 순서대로 조회됨")
	void findMyCourseDetail_success() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");
		User first = savedUser("first@test.com", "첫번째", Role.STUDENT, "20260001");
		User second = savedUser("second@test.com", "두번째", Role.STUDENT, "20260002");
		enroll(course, first);
		enroll(course, second);

		// when
		CourseDetailResponse detail = courseService.findMyCourseDetail(instructor.getId(), course.getId());

		// then
		assertThat(detail.name()).isEqualTo("자바스프링");
		assertThat(detail.joinCode()).isEqualTo("AAAAA2");
		assertThat(detail.students())
				.extracting("name", "studentNo")
				.containsExactly(
						tuple("첫번째", "20260001"),
						tuple("두번째", "20260002"));
	}

	@Test
	@DisplayName("findMyCourseDetail - 수강생이 없으면 빈 목록")
	void findMyCourseDetail_noStudents() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		Course course = savedCourse(instructor, "자바스프링", "AAAAA2");

		// when
		CourseDetailResponse detail = courseService.findMyCourseDetail(instructor.getId(), course.getId());

		// then
		assertThat(detail.students()).isEmpty();
	}

	@Test
	@DisplayName("findMyCourseDetail - 다른 강좌의 수강생은 섞이지 않음")
	void findMyCourseDetail_onlyThatCourse() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);
		Course java = savedCourse(instructor, "자바스프링", "AAAAA2");
		Course db = savedCourse(instructor, "데이터베이스", "BBBBB3");
		User student = savedUser("student@test.com", "학생", Role.STUDENT, "20260001");
		enroll(db, student);

		// when
		CourseDetailResponse detail = courseService.findMyCourseDetail(instructor.getId(), java.getId());

		// then
		assertThat(detail.students()).isEmpty();
	}

	@Test
	@DisplayName("findMyCourseDetail - 다른 강사의 강좌면 403 (B5)")
	void findMyCourseDetail_otherInstructor() {
		// given: 강사 A의 강좌를 강사 B가 열려고 함
		User instructorA = savedUser("a@test.com", "강사A", Role.INSTRUCTOR, null);
		User instructorB = savedUser("b@test.com", "강사B", Role.INSTRUCTOR, null);
		Course courseOfA = savedCourse(instructorA, "A의 강좌", "AAAAA2");

		// when & then
		assertThatThrownBy(() -> courseService.findMyCourseDetail(instructorB.getId(), courseOfA.getId()))
				.isInstanceOf(ForbiddenException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("findMyCourseDetail - 존재하지 않는 강좌면 404")
	void findMyCourseDetail_notFound() {
		// given
		User instructor = savedUser("teacher@test.com", "강사", Role.INSTRUCTOR, null);

		// when & then
		assertThatThrownBy(() -> courseService.findMyCourseDetail(instructor.getId(), 999_999L))
				.isInstanceOf(CourseNotFoundException.class)
				.hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
	}
}
