package kr.ac.ync.midterm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 흔한 공격 입력(SQL 인젝션, XSS, 입력 위조, CSRF, 비로그인 접근)에 대한 방어를 확인한다.
 * 실제 공격 문자열을 넣어 본 결과를 자동 테스트로 남기는 것이 목적이다.
 */
class SecurityAttackTest extends BaseController {

	private static final String XSS_SCRIPT = "<script>alert(1)</script>";
	private static final String XSS_SCRIPT_ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;";
	private static final String XSS_IMG = "<img src=x onerror=alert(1)>";
	private static final String XSS_IMG_ESCAPED = "&lt;img src=x onerror=alert(1)&gt;";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	// ---------- SQL 인젝션 ----------

	@ParameterizedTest(name = "이메일 입력 [{0}]")
	@ValueSource(strings = {
			"' OR '1'='1",
			"' OR '1'='1' --",
			"admin'--",
			"student@test.com' OR '1'='1",
			"\"; DROP TABLE users; --",
			"' UNION SELECT * FROM users --"
	})
	@DisplayName("로그인 - SQL 인젝션 이메일로는 로그인되지 않고 사용자 테이블도 그대로")
	void login_sqlInjectionEmail(String payload) throws Exception {
		long before = userRepository.count();

		mockMvc.perform(formLogin("/login").userParameter("email").user(payload).password("password123"))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());

		assertThat(userRepository.count()).isEqualTo(before);
		assertThat(userRepository.findByEmail("student@test.com")).isPresent();
	}

	@ParameterizedTest(name = "비밀번호 입력 [{0}]")
	@ValueSource(strings = {
			"' OR '1'='1",
			"' OR ''='",
			"password123' --"
	})
	@DisplayName("로그인 - SQL 인젝션 비밀번호로는 로그인되지 않음")
	void login_sqlInjectionPassword(String payload) throws Exception {
		mockMvc.perform(formLogin("/login").userParameter("email").user("student@test.com").password(payload))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	@Test
	@DisplayName("회원가입 - SQL 문장이 이름에 들어 있어도 문자 그대로 저장되고 테이블은 그대로")
	void signup_sqlInjectionName() throws Exception {
		// given
		String payload = "Robert'); DROP TABLE users;--";

		// when
		mockMvc.perform(post("/signup")
						.with(csrf())
						.param("studentNo", "20260009")
						.param("name", payload)
						.param("email", "robert@test.com")
						.param("password", "password123"))
				.andExpect(redirectedUrl("/login?signup"));

		// then
		assertThat(userRepository.findByEmail("robert@test.com")).get()
				.extracting(User::getName).isEqualTo(payload);
		assertThat(userRepository.findByEmail("student@test.com")).isPresent();
	}

	@Test
	@DisplayName("회원가입 - SQL 인젝션 형태의 이메일 · 학번은 입력 검증에서 거부됨")
	void signup_sqlInjectionRejectedByValidation() throws Exception {
		long before = userRepository.count();

		mockMvc.perform(post("/signup")
						.with(csrf())
						.param("studentNo", "1' OR '1'='1")
						.param("name", "홍길동")
						.param("email", "a@b.com' OR '1'='1")
						.param("password", "password123"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("signupRequest", "studentNo", "email"));

		assertThat(userRepository.count()).isEqualTo(before);
	}

	// ---------- XSS (화면 출력 이스케이프) ----------

	@Test
	@DisplayName("XSS - 이름에 스크립트가 있어도 상단바에 이스케이프되어 출력됨")
	void xss_userNameInTopbar() throws Exception {
		// given
		mockMvc.perform(post("/signup")
				.with(csrf())
				.param("studentNo", "20260008")
				.param("name", XSS_SCRIPT)
				.param("email", "xss@test.com")
				.param("password", "password123"));
		User attacker = userRepository.findByEmail("xss@test.com").orElseThrow();

		// when & then
		mockMvc.perform(get("/").with(user(principal(attacker))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(XSS_SCRIPT_ESCAPED)))
				.andExpect(content().string(not(containsString(XSS_SCRIPT))));
	}

	@Test
	@DisplayName("XSS - 강좌명에 태그가 있어도 목록과 상세에 이스케이프되어 출력됨")
	void xss_courseName() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		mockMvc.perform(post("/instructor/courses")
				.with(user(principal(instructor))).with(csrf()).param("name", XSS_IMG));
		Course course = courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructor.getId()).getFirst();

		// when & then: 목록
		mockMvc.perform(get("/instructor/courses").with(user(principal(instructor))))
				.andExpect(content().string(containsString(XSS_IMG_ESCAPED)))
				.andExpect(content().string(not(containsString(XSS_IMG))));

		// when & then: 상세
		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(content().string(containsString(XSS_IMG_ESCAPED)))
				.andExpect(content().string(not(containsString(XSS_IMG))));
	}

	@Test
	@DisplayName("XSS - 수강생 이름 · 이메일에 스크립트가 있어도 강좌 상세에 이스케이프되어 출력됨")
	void xss_studentNameInCourseDetail() throws Exception {
		// given
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		Course course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		User attacker = userRepository.save(User.builder()
				.email("attacker@test.com").password(passwordEncoder.encode("password123"))
				.name(XSS_SCRIPT).studentNo("20260007").role(Role.STUDENT).build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(attacker).build());

		// when & then
		mockMvc.perform(get("/instructor/courses/{id}", course.getId()).with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(XSS_SCRIPT_ESCAPED)))
				.andExpect(content().string(not(containsString(XSS_SCRIPT))));
	}

	@Test
	@DisplayName("XSS - 수강 등록 안내 메시지(강좌명 포함)도 이스케이프되어 출력됨")
	void xss_flashMessage() throws Exception {
		mockMvc.perform(get("/student/courses/join")
						.with(user(principal(student)))
						.flashAttr("message", XSS_SCRIPT + " 강좌에 수강 등록되었습니다."))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(XSS_SCRIPT_ESCAPED)))
				.andExpect(content().string(not(containsString(XSS_SCRIPT))));
	}

	// ---------- 입력 위조 ----------

	@Test
	@DisplayName("회원가입 - role · id를 끼워 넣어도 무시되고 학생으로 저장됨")
	void signup_massAssignment() throws Exception {
		mockMvc.perform(post("/signup")
						.with(csrf())
						.param("studentNo", "20260006")
						.param("name", "침입자")
						.param("email", "intruder@test.com")
						.param("password", "password123")
						.param("role", "INSTRUCTOR")
						.param("id", "1"))
				.andExpect(redirectedUrl("/login?signup"));

		User saved = userRepository.findByEmail("intruder@test.com").orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
		assertThat(saved.getId()).isNotEqualTo(1L);
	}

	@Test
	@DisplayName("경로의 강좌 번호가 숫자가 아니거나 비정상이면 오류 화면 없이 400 · 404로 처리됨")
	void tamperedPathVariable() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/instructor/courses/abc").with(user(principal(instructor))))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/instructor/courses/99999999999999999999").with(user(principal(instructor))))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/instructor/courses/-1").with(user(principal(instructor))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("참여코드에 아주 긴 값을 넣어도 서버 오류 없이 폼 에러로 처리됨")
	void enroll_oversizedInput() throws Exception {
		mockMvc.perform(post("/student/courses/join")
						.with(user(principal(student)))
						.with(csrf())
						.param("joinCode", "A".repeat(10_000)))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("enrollRequest", "joinCode"));

		assertThat(enrollmentRepository.count()).isZero();
	}

	// ---------- CSRF ----------

	@Test
	@DisplayName("CSRF - 토큰 없이 로그인 · 로그아웃을 요청하면 403")
	void csrf_loginAndLogout() throws Exception {
		mockMvc.perform(post("/login")
						.param("email", "student@test.com").param("password", "password123"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/logout").with(user(principal(student))))
				.andExpect(status().isForbidden());
	}

	// ---------- 비로그인 접근 ----------

	@ParameterizedTest(name = "비로그인 접근 [{0}]")
	@ValueSource(strings = {
			"/",
			"/instructor/courses",
			"/instructor/courses/1",
			"/student/courses/join"
	})
	@DisplayName("로그인하지 않으면 보호된 화면은 로그인 페이지로 이동")
	void anonymousAccess(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	// ---------- 보안 헤더 ----------

	@Test
	@DisplayName("보안 헤더 - 클릭재킹 · MIME 스니핑 방지 헤더가 응답에 포함됨")
	void securityHeaders() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}
}
