package kr.ac.ync.midterm.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;

class GlobalExceptionHandlerTest extends BaseController {

	@Test
	@DisplayName("ForbiddenException - 403 상태와 권한 없음 페이지")
	void forbiddenException() throws Exception {
		mockMvc.perform(get("/test-errors/forbidden").with(user(new CustomUserDetails(student))))
				.andExpect(status().isForbidden())
				.andExpect(view().name("error/403"))
				.andExpect(model().attribute("message", "접근 권한이 없습니다."))
				.andExpect(content().string(containsString("접근 권한이 없습니다.")));
	}

	@Test
	@DisplayName("404 CustomException - 404 상태와 없는 페이지 (메시지 표시)")
	void notFoundException() throws Exception {
		mockMvc.perform(get("/test-errors/not-found").with(user(new CustomUserDetails(student))))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"))
				.andExpect(content().string(containsString("존재하지 않는 강좌입니다.")));
	}

	@Test
	@DisplayName("그 외 CustomException - 해당 상태와 공통 오류 페이지")
	void otherCustomException() throws Exception {
		mockMvc.perform(get("/test-errors/conflict").with(user(new CustomUserDetails(student))))
				.andExpect(status().isConflict())
				.andExpect(view().name("error/error"))
				.andExpect(content().string(containsString("이미 처리된 요청입니다.")));
	}

	@Test
	@DisplayName("로그인하지 않은 사용자에게도 오류 페이지가 그려짐 (로그인 링크만 표시, 이름·로그아웃 없음)")
	void errorPage_anonymous() throws Exception {
		mockMvc.perform(get("/error/anonymous-render"))
				.andExpect(status().isOk())
				.andExpect(view().name("error/403"))
				.andExpect(content().string(containsString("접근 권한이 없습니다.")))
				.andExpect(content().string(containsString("/login")))
				.andExpect(content().string(not(containsString("logoutModal"))));
	}
}
