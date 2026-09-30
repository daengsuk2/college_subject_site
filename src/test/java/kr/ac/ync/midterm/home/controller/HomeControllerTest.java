package kr.ac.ync.midterm.home.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

class HomeControllerTest extends BaseController {

	@Test
	@DisplayName("GET / - 로그인하지 않으면 로그인 페이지로 이동")
	void home_unauthenticated() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	@DisplayName("GET / - 학생에게는 학생 메뉴만 보이고 상단에 이름이 표시됨")
	void home_student() throws Exception {
		mockMvc.perform(get("/").with(user(new CustomUserDetails(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("home"))
				.andExpect(content().string(containsString("테스터")))
				.andExpect(content().string(containsString("내 과제")))
				.andExpect(content().string(containsString("수강 등록")))
				.andExpect(content().string(not(containsString("강좌 관리"))))
				.andExpect(content().string(not(containsString("통계 대시보드"))));
	}

	@Test
	@DisplayName("GET / - 강사에게는 강사 메뉴만 보임")
	void home_instructor() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/").with(user(new CustomUserDetails(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("강좌 관리")))
				.andExpect(content().string(containsString("통계 대시보드")))
				.andExpect(content().string(not(containsString("내 과제"))))
				.andExpect(content().string(not(containsString("수강 등록"))));
	}
}
