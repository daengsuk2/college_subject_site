package kr.ac.ync.midterm.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import kr.ac.ync.midterm.support.BaseController;

/**
 * 로그인 화면: SB Admin 2 원본 로그인 화면 구성(환영 문구, 둥근 입력칸, 로그인 상태 유지, 회원가입 링크)과
 * 로그인 상태 유지(remember-me) 동작을 확인한다.
 */
class LoginPageTest extends BaseController {

	@Test
	@DisplayName("GET /login - 환영 문구 · 이메일 · 비밀번호 · 로그인 상태 유지 · 로그인 버튼 · 회원가입 링크가 표시됨")
	void loginPage_layout() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("환영합니다!")))
				.andExpect(content().string(containsString("이메일 주소를 입력하세요...")))
				.andExpect(content().string(containsString("name=\"password\"")))
				.andExpect(content().string(containsString("로그인 상태 유지")))
				.andExpect(content().string(containsString("name=\"remember-me\"")))
				.andExpect(content().string(containsString("계정이 없으신가요? 회원가입")))
				.andExpect(content().string(containsString("href=\"/signup\"")))
				.andExpect(content().string(not(containsString("학생 회원가입"))));
	}

	@Test
	@DisplayName("GET /login - SB Admin 2의 로그인 화면 클래스(그라데이션 배경, 카드, 둥근 입력칸 · 버튼)를 사용함")
	void loginPage_sbAdmin2() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(content().string(containsString("bg-gradient-primary")))
				.andExpect(content().string(containsString("card o-hidden border-0 shadow-lg")))
				.andExpect(content().string(containsString("form-control form-control-user")))
				.andExpect(content().string(containsString("btn btn-primary btn-user btn-block")))
				.andExpect(content().string(containsString("custom-control custom-checkbox")))
				.andExpect(content().string(containsString("sb-admin-2.min.css")));
	}

	@Test
	@DisplayName("GET /login - 오류 · 로그아웃 · 가입 완료 안내가 파라미터에 따라 표시됨")
	void loginPage_messages() throws Exception {
		mockMvc.perform(get("/login").param("error", ""))
				.andExpect(content().string(containsString("이메일 또는 비밀번호가 올바르지 않습니다.")));
		mockMvc.perform(get("/login").param("logout", ""))
				.andExpect(content().string(containsString("로그아웃 되었습니다.")));
		mockMvc.perform(get("/login").param("signup", ""))
				.andExpect(content().string(containsString("회원가입이 완료되었습니다.")));
		mockMvc.perform(get("/login"))
				.andExpect(content().string(not(containsString("이메일 또는 비밀번호가 올바르지 않습니다."))));
	}

	@Test
	@DisplayName("로그인 상태 유지를 체크하고 로그인하면 remember-me 쿠키가 14일 유효기간으로 발급됨")
	void rememberMe_checked() throws Exception {
		MvcResult result = mockMvc.perform(post("/login").with(csrf())
						.param("email", "student@test.com").param("password", "password123")
						.param("remember-me", "on"))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		Cookie cookie = result.getResponse().getCookie("remember-me");
		assertThat(cookie).isNotNull();
		assertThat(cookie.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
		assertThat(cookie.isHttpOnly()).isTrue();
	}

	@Test
	@DisplayName("체크하지 않거나 비밀번호가 틀리면 remember-me 쿠키가 발급되지 않음")
	void rememberMe_notIssued() throws Exception {
		MvcResult unchecked = mockMvc.perform(post("/login").with(csrf())
						.param("email", "student@test.com").param("password", "password123"))
				.andReturn();
		assertThat(unchecked.getResponse().getCookie("remember-me")).isNull();

		MvcResult wrong = mockMvc.perform(post("/login").with(csrf())
						.param("email", "student@test.com").param("password", "wrong-password")
						.param("remember-me", "on"))
				.andReturn();
		assertThat(wrong.getResponse().getCookie("remember-me")).isNull();
	}

	@Test
	@DisplayName("remember-me 쿠키만으로(세션 없이) 로그인이 필요한 화면에 들어갈 수 있고, 쿠키가 없으면 로그인 화면으로 이동")
	void rememberMe_cookieLogsIn() throws Exception {
		MvcResult login = mockMvc.perform(post("/login").with(csrf())
						.param("email", "student@test.com").param("password", "password123")
						.param("remember-me", "on"))
				.andReturn();
		Cookie cookie = login.getResponse().getCookie("remember-me");

		mockMvc.perform(get("/student/courses").cookie(cookie)).andExpect(status().isOk());
		mockMvc.perform(get("/student/courses")).andExpect(status().is3xxRedirection());
	}

	@Test
	@DisplayName("값을 조작한 remember-me 쿠키로는 들어갈 수 없음")
	void rememberMe_tamperedCookie() throws Exception {
		MvcResult login = mockMvc.perform(post("/login").with(csrf())
						.param("email", "student@test.com").param("password", "password123")
						.param("remember-me", "on"))
				.andReturn();
		Cookie original = login.getResponse().getCookie("remember-me");
		Cookie tampered = new Cookie("remember-me", original.getValue().replaceFirst(".$", original.getValue().endsWith("A") ? "B" : "A"));

		mockMvc.perform(get("/student/courses").cookie(tampered)).andExpect(status().is3xxRedirection());
	}
}
