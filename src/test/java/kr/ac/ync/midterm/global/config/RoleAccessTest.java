package kr.ac.ync.midterm.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

class RoleAccessTest extends BaseController {

	private CustomUserDetails studentDetails() {
		return new CustomUserDetails(student);
	}

	private CustomUserDetails instructorDetails() {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		return new CustomUserDetails(instructor);
	}

	@Test
	@DisplayName("/instructor/** - 학생은 403")
	void instructorUrl_student() throws Exception {
		mockMvc.perform(get("/instructor/courses").with(user(studentDetails())))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("/student/** - 강사는 403")
	void studentUrl_instructor() throws Exception {
		mockMvc.perform(get("/student/assignments").with(user(instructorDetails())))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("/instructor/** - 로그인하지 않으면 로그인 페이지로 이동")
	void instructorUrl_anonymous() throws Exception {
		mockMvc.perform(get("/instructor/courses"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	@DisplayName("/student/** - 로그인하지 않으면 로그인 페이지로 이동")
	void studentUrl_anonymous() throws Exception {
		mockMvc.perform(get("/student/assignments"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	@DisplayName("/instructor/** - 강사는 권한을 통과함 (화면이 아직 없으면 404, 403이 아님)")
	void instructorUrl_instructor() throws Exception {
		mockMvc.perform(get("/instructor/courses").with(user(instructorDetails())))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
	}

	@Test
	@DisplayName("/student/** - 학생은 권한을 통과함 (화면이 아직 없으면 404, 403이 아님)")
	void studentUrl_student() throws Exception {
		mockMvc.perform(get("/student/assignments").with(user(studentDetails())))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
	}
}
