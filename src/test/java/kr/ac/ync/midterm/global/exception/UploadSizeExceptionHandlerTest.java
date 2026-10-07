package kr.ac.ync.midterm.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;

class UploadSizeExceptionHandlerTest extends BaseController {

	@Test
	@DisplayName("MaxUploadSizeExceededException - 413 상태와 10MB 안내 문구를 보여 줌")
	void maxUploadSizeExceeded() throws Exception {
		mockMvc.perform(get("/test-errors/too-large").with(user(new CustomUserDetails(student))))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(view().name("error/error"))
				.andExpect(content().string(containsString("10MB 이하")));
	}
}
