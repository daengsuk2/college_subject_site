package kr.ac.ync.midterm.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

class InstructorAssignmentControllerTest extends BaseController {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 9, 0);

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	private Course savedCourse(User instructor) {
		return courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
	}

	private Assignment savedAssignment(Course course, String title) {
		return assignmentRepository.save(
				Assignment.builder().course(course).title(title).content("내용")
						.startAt(START).endAt(START.plusDays(7)).maxScore(100).build());
	}

	// 과제 폼 입력값을 채운 POST 요청. 기본값은 유효한 값이고, overrides(이름, 값 쌍)로 일부를 바꾼다.
	// MockMvc의 param()은 같은 이름을 덮어쓰지 않고 추가하므로 값을 합쳐서 한 번만 넣는다.
	private MockHttpServletRequestBuilder withForm(MockHttpServletRequestBuilder request, User actor,
			String... overrides) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("title", "1주차 과제");
		params.put("content", "과제 내용");
		params.put("startAt", "2026-10-10T09:00");
		params.put("endAt", "2026-10-17T23:59");
		params.put("maxScore", "100");
		for (int i = 0; i < overrides.length; i += 2) {
			params.put(overrides[i], overrides[i + 1]);
		}
		params.forEach(request::param);
		return request.with(user(principal(actor))).with(csrf());
	}

	// ---------- 등록 폼 ----------

	@Test
	@DisplayName("GET /instructor/assignments/new - 본인 강좌면 등록 폼이 표시됨")
	void newForm_owner() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(get("/instructor/assignments/new").param("courseId", course.getId().toString())
						.with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/assignment-form"))
				.andExpect(content().string(containsString("과제 등록")))
				.andExpect(content().string(containsString("자바스프링")));
	}

	@Test
	@DisplayName("GET /instructor/assignments/new - 강좌 없이 들어오면 강좌 목록으로 안내")
	void newForm_withoutCourseId() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/instructor/assignments/new").with(user(principal(instructor))))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses"))
				.andExpect(flash().attribute("message", containsString("강좌를 먼저 선택")));
	}

	@Test
	@DisplayName("GET /instructor/assignments/new - 다른 강사의 강좌는 403, 없는 강좌는 404, 학생은 403")
	void newForm_forbiddenAndNotFound() throws Exception {
		User owner = createUser("owner@test.com", Role.INSTRUCTOR);
		User other = createUser("other@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(owner);

		mockMvc.perform(get("/instructor/assignments/new").param("courseId", course.getId().toString())
						.with(user(principal(other))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/instructor/assignments/new").param("courseId", "999999")
						.with(user(principal(owner))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/instructor/assignments/new").param("courseId", course.getId().toString())
						.with(user(principal(student))))
				.andExpect(status().isForbidden());
	}

	// ---------- 등록 ----------

	@Test
	@DisplayName("POST /instructor/assignments/new - 1.정상 등록 후 강좌 상세로 이동")
	void create_success() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(withForm(post("/instructor/assignments/new").param("courseId", course.getId().toString()),
						instructor))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
				.andExpect(flash().attribute("message", "과제가 등록되었습니다."));

		assertThat(assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()))
				.hasSize(1)
				.first()
				.satisfies(a -> {
					assertThat(a.getTitle()).isEqualTo("1주차 과제");
					assertThat(a.getStartAt()).isEqualTo(START);
					assertThat(a.getMaxScore()).isEqualTo(100);
				});
	}

	@Test
	@DisplayName("POST /instructor/assignments/new - 2.필수값이 비어 있으면 폼 에러 (저장 안 됨)")
	void create_missingFields() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(post("/instructor/assignments/new").param("courseId", course.getId().toString())
						.with(user(principal(instructor))).with(csrf())
						.param("title", " ").param("content", " "))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/assignment-form"))
				.andExpect(model().attributeHasFieldErrors("assignmentRequest",
						"title", "content", "startAt", "endAt", "maxScore"));

		assertThat(assignmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST /instructor/assignments/new - 3.배점이 1 미만이면 폼 에러")
	void create_maxScoreTooSmall() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(withForm(post("/instructor/assignments/new").param("courseId", course.getId().toString()),
						instructor, "maxScore", "0"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("assignmentRequest", "maxScore"));

		assertThat(assignmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST /instructor/assignments/new - 4.종료일시가 시작일시보다 이르면 종료일시 폼 에러 (B7)")
	void create_endBeforeStart() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(withForm(post("/instructor/assignments/new").param("courseId", course.getId().toString()),
						instructor, "startAt", "2026-10-17T23:59", "endAt", "2026-10-10T09:00"))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/assignment-form"))
				.andExpect(model().attributeHasFieldErrors("assignmentRequest", "endAt"))
				.andExpect(content().string(containsString("종료일시는 시작일시보다 뒤여야 합니다.")));

		assertThat(assignmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST /instructor/assignments/new - 5.다른 강사의 강좌에는 등록할 수 없음 (B5)")
	void create_otherInstructor() throws Exception {
		User owner = createUser("owner@test.com", Role.INSTRUCTOR);
		User other = createUser("other@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(owner);

		mockMvc.perform(withForm(post("/instructor/assignments/new").param("courseId", course.getId().toString()),
						other))
				.andExpect(status().isForbidden());

		assertThat(assignmentRepository.count()).isZero();
	}

	@Test
	@DisplayName("POST /instructor/assignments/new - 6.CSRF 토큰이 없으면 403")
	void create_withoutCsrf() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(post("/instructor/assignments/new").param("courseId", course.getId().toString())
						.with(user(principal(instructor)))
						.param("title", "과제").param("content", "내용")
						.param("startAt", "2026-10-10T09:00").param("endAt", "2026-10-17T23:59")
						.param("maxScore", "100"))
				.andExpect(status().isForbidden());

		assertThat(assignmentRepository.count()).isZero();
	}

	// ---------- 수정 ----------

	@Test
	@DisplayName("GET /instructor/assignments/{id}/edit - 기존 값이 채워진 수정 폼이 표시됨")
	void editForm_prefilled() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Assignment assignment = savedAssignment(savedCourse(instructor), "1주차 과제");

		mockMvc.perform(get("/instructor/assignments/{id}/edit", assignment.getId())
						.with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(view().name("instructor/assignment-form"))
				.andExpect(content().string(containsString("과제 수정")))
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(containsString("2026-10-10T09:00")));
	}

	@Test
	@DisplayName("GET /instructor/assignments/{id}/edit - 다른 강사는 403, 없는 과제는 404")
	void editForm_forbiddenAndNotFound() throws Exception {
		User owner = createUser("owner@test.com", Role.INSTRUCTOR);
		User other = createUser("other@test.com", Role.INSTRUCTOR);
		Assignment assignment = savedAssignment(savedCourse(owner), "1주차 과제");

		mockMvc.perform(get("/instructor/assignments/{id}/edit", assignment.getId())
						.with(user(principal(other))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/instructor/assignments/{id}/edit", 999_999L)
						.with(user(principal(owner))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("POST /instructor/assignments/{id}/edit - 1.정상 수정 후 강좌 상세로 이동")
	void update_success() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);
		Assignment assignment = savedAssignment(course, "1주차 과제");

		mockMvc.perform(withForm(post("/instructor/assignments/{id}/edit", assignment.getId()), instructor,
						"title", "수정된 제목", "maxScore", "50"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
				.andExpect(flash().attribute("message", "과제가 수정되었습니다."));

		Assignment saved = assignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("수정된 제목");
		assertThat(saved.getMaxScore()).isEqualTo(50);
	}

	@Test
	@DisplayName("POST /instructor/assignments/{id}/edit - 2.수정 시에도 종료일시 검증이 적용됨 (B7)")
	void update_endBeforeStart() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Assignment assignment = savedAssignment(savedCourse(instructor), "1주차 과제");

		mockMvc.perform(withForm(post("/instructor/assignments/{id}/edit", assignment.getId()), instructor,
						"title", "바뀌면 안 되는 제목",
						"startAt", "2026-10-17T23:59", "endAt", "2026-10-10T09:00"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("assignmentRequest", "endAt"));

		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getTitle()).isEqualTo("1주차 과제");
	}

	@Test
	@DisplayName("POST /instructor/assignments/{id}/edit - 3.다른 강사의 과제는 수정할 수 없음 (B5)")
	void update_otherInstructor() throws Exception {
		User owner = createUser("owner@test.com", Role.INSTRUCTOR);
		User other = createUser("other@test.com", Role.INSTRUCTOR);
		Assignment assignment = savedAssignment(savedCourse(owner), "1주차 과제");

		mockMvc.perform(withForm(post("/instructor/assignments/{id}/edit", assignment.getId()), other,
						"title", "가로챈 제목"))
				.andExpect(status().isForbidden());

		assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getTitle()).isEqualTo("1주차 과제");
	}

	// ---------- 삭제 ----------

	@Test
	@DisplayName("POST /instructor/assignments/{id}/delete - 1.제출이 없으면 삭제되고 강좌 상세로 이동")
	void delete_success() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);
		Assignment assignment = savedAssignment(course, "1주차 과제");

		mockMvc.perform(post("/instructor/assignments/{id}/delete", assignment.getId())
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
				.andExpect(flash().attribute("message", "과제가 삭제되었습니다."));

		assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
	}

	@Test
	@DisplayName("POST /instructor/assignments/{id}/delete - 2.제출이 있으면 삭제되지 않고 안내됨 (B8)")
	void delete_withSubmission() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);
		Assignment assignment = savedAssignment(course, "1주차 과제");
		submissionRepository.saveAndFlush(
				Submission.builder().assignment(assignment).student(student).content("제출물").build());

		mockMvc.perform(post("/instructor/assignments/{id}/delete", assignment.getId())
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/instructor/courses/" + course.getId()))
				.andExpect(flash().attribute("error", "제출물이 있어 삭제 불가"));

		assertThat(assignmentRepository.findById(assignment.getId())).isPresent();
	}

	@Test
	@DisplayName("POST /instructor/assignments/{id}/delete - 3.다른 강사는 403 (B5), 학생은 403, CSRF 없으면 403")
	void delete_forbidden() throws Exception {
		User owner = createUser("owner@test.com", Role.INSTRUCTOR);
		User other = createUser("other@test.com", Role.INSTRUCTOR);
		Assignment assignment = savedAssignment(savedCourse(owner), "1주차 과제");

		mockMvc.perform(post("/instructor/assignments/{id}/delete", assignment.getId())
						.with(user(principal(other))).with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/instructor/assignments/{id}/delete", assignment.getId())
						.with(user(principal(student))).with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/instructor/assignments/{id}/delete", assignment.getId())
						.with(user(principal(owner))))
				.andExpect(status().isForbidden());

		assertThat(assignmentRepository.findById(assignment.getId())).isPresent();
	}

	@Test
	@DisplayName("POST /instructor/assignments/{id}/delete - 4.없는 과제는 404")
	void delete_notFound() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(post("/instructor/assignments/{id}/delete", 999_999L)
						.with(user(principal(instructor))).with(csrf()))
				.andExpect(status().isNotFound());
	}

	// ---------- 강좌 상세의 과제 목록 ----------

	@Test
	@DisplayName("GET /instructor/courses/{id} - 과제 목록과 등록 버튼이 표시됨")
	void courseDetail_showsAssignments() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);
		savedAssignment(course, "1주차 과제");

		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("1주차 과제")))
				.andExpect(content().string(containsString("/instructor/assignments/new?courseId=" + course.getId())))
				.andExpect(content().string(not(containsString("등록된 과제가 없습니다."))));
	}

	@Test
	@DisplayName("GET /instructor/courses/{id} - 과제가 없으면 안내 문구")
	void courseDetail_noAssignments() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);

		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("등록된 과제가 없습니다.")));
	}

	@Test
	@DisplayName("XSS - 과제 제목의 스크립트는 목록과 수정 폼에서 이스케이프되어 출력됨")
	void xss_assignmentTitle() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = savedCourse(instructor);
		Assignment assignment = savedAssignment(course, "<script>alert(1)</script>");

		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))));

		mockMvc.perform(get("/instructor/assignments/{id}/edit", assignment.getId())
						.with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
	}
}
