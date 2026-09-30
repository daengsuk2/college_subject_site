package kr.ac.ync.midterm.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

class AuthControllerTest extends BaseController {

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("GET /login - 로그인하지 않아도 접근 가능")
	void login_page() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(view().name("auth/login"));
	}

	@Test
	@DisplayName("GET /signup - 로그인하지 않아도 접근 가능")
	void signup_page() throws Exception {
		mockMvc.perform(get("/signup"))
				.andExpect(status().isOk())
				.andExpect(view().name("auth/signup"));
	}

	@Test
	@DisplayName("POST /signup - 1.정상 데이터")
	void signup_endpoint_validData() throws Exception {
		mockMvc.perform(
						post("/signup")
								.with(csrf())
								.param("studentNo", "20260001")
								.param("name", "홍길동")
								.param("email", "hong@test.com")
								.param("password", "password123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?signup"));

		assertThat(userRepository.existsByEmail("hong@test.com")).isTrue();
	}

	@Test
	@DisplayName("POST /signup - 2.필수 데이터 누락 / 형식 오류")
	void signup_endpoint_invalidData() throws Exception {
		mockMvc.perform(
						post("/signup")
								.with(csrf())
								.param("studentNo", "abc")
								.param("name", "")
								.param("email", "not-an-email")
								.param("password", "123"))
				.andExpect(status().isOk())
				.andExpect(view().name("auth/signup"))
				.andExpect(model().attributeHasFieldErrors("signupRequest",
						"studentNo", "name", "email", "password"));

		assertThat(userRepository.existsByEmail("not-an-email")).isFalse();
	}

	@Test
	@DisplayName("POST /signup - 3.중복 이메일이면 폼 에러")
	void signup_endpoint_duplicateEmail() throws Exception {
		mockMvc.perform(
						post("/signup")
								.with(csrf())
								.param("studentNo", "20260002")
								.param("name", "다른사람")
								.param("email", student.getEmail())
								.param("password", "password123"))
				.andExpect(status().isOk())
				.andExpect(view().name("auth/signup"))
				.andExpect(model().attributeHasFieldErrors("signupRequest", "email"));
	}

	@Test
	@DisplayName("POST /signup - 4.role 파라미터를 끼워 넣어도 항상 STUDENT")
	void signup_endpoint_roleIgnored() throws Exception {
		mockMvc.perform(
						post("/signup")
								.with(csrf())
								.param("studentNo", "20260003")
								.param("name", "침입자")
								.param("email", "intruder@test.com")
								.param("password", "password123")
								.param("role", "INSTRUCTOR"))
				.andExpect(status().is3xxRedirection());

		User saved = userRepository.findAll().stream()
				.filter(u -> u.getEmail().equals("intruder@test.com"))
				.findFirst().orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
	}

	@Test
	@DisplayName("POST /signup - 5.CSRF 토큰이 없으면 403")
	void signup_endpoint_withoutCsrf() throws Exception {
		mockMvc.perform(
						post("/signup")
								.param("studentNo", "20260004")
								.param("name", "홍길동")
								.param("email", "nocsrf@test.com")
								.param("password", "password123"))
				.andExpect(status().isForbidden());
	}
}
