package kr.ac.ync.midterm.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 마이페이지 화면 (X1). 실제 로그인 세션으로 이름 변경이 상단바에 바로 반영되는지,
 * 비밀번호 변경 후 로그인 상태가 유지되고 새 비밀번호로만 로그인되는지를 확인한다.
 */
class MypageControllerTest extends BaseController {

	private static final String PASSWORD = "password123";
	private static final Pattern ID = Pattern.compile("\\sid=\"([^\"]+)\"");

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// 실제 로그인 폼으로 로그인해 세션을 얻는다
	private MockHttpSession login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
				.andExpect(authenticated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession();
	}

	private MockHttpServletRequestBuilder changeName(MockHttpSession session, String name) {
		return post("/mypage/name").param("name", name).session(session).with(csrf());
	}

	private MockHttpServletRequestBuilder changePassword(MockHttpSession session, String current, String next,
			String confirm) {
		return post("/mypage/password").param("currentPassword", current).param("newPassword", next)
				.param("newPasswordConfirm", confirm).session(session).with(csrf());
	}

	private User reload(User u) {
		return userRepository.findById(u.getId()).orElseThrow();
	}

	// ---------- 화면 ----------

	@Test
	@DisplayName("GET /mypage - 학생: 이메일 · 학번 · 이름이 표시되고 비밀번호 값은 나오지 않으며 상단바에 링크가 있음")
	void page_student() throws Exception {
		mockMvc.perform(get("/mypage").with(user(new CustomUserDetails(student))))
				.andExpect(status().isOk())
				.andExpect(view().name("mypage"))
				.andExpect(content().string(containsString("student@test.com")))
				.andExpect(content().string(containsString("20260000")))
				.andExpect(content().string(containsString("테스터")))
				.andExpect(content().string(containsString("학생")))
				.andExpect(content().string(containsString("/mypage")))
				.andExpect(content().string(not(containsString(student.getPassword()))));
	}

	@Test
	@DisplayName("GET /mypage - 강사: 학번 항목이 없고 역할이 강사로 표시됨")
	void page_instructor() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);

		mockMvc.perform(get("/mypage").with(user(new CustomUserDetails(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("teacher@test.com")))
				.andExpect(content().string(containsString("강사")))
				.andExpect(content().string(not(containsString(">학번<"))));
	}

	@Test
	@DisplayName("GET /mypage - 화면 안에 중복 id가 없음")
	void page_noDuplicateIds() throws Exception {
		String html = mockMvc.perform(get("/mypage").with(user(new CustomUserDetails(student))))
				.andReturn().getResponse().getContentAsString();

		Set<String> seen = new HashSet<>();
		Matcher m = ID.matcher(html);
		while (m.find()) {
			assertThat(seen.add(m.group(1))).as("중복 id: " + m.group(1)).isTrue();
		}
	}

	@Test
	@DisplayName("비로그인은 로그인 화면으로 이동하고, CSRF 토큰이 없는 변경 요청은 거부됨")
	void anonymousAndCsrf() throws Exception {
		mockMvc.perform(get("/mypage")).andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/mypage/name").param("name", "해킹").with(csrf()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/mypage/name").param("name", "해킹").with(user(new CustomUserDetails(student))))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/mypage/password").param("currentPassword", PASSWORD).param("newPassword", "newpassword1")
						.param("newPasswordConfirm", "newpassword1").with(user(new CustomUserDetails(student))))
				.andExpect(status().isForbidden());

		assertThat(reload(student).getName()).isEqualTo("테스터");
		assertThat(passwordEncoder.matches(PASSWORD, reload(student).getPassword())).isTrue();
	}

	// ---------- 이름 변경 ----------

	@Test
	@DisplayName("이름 변경 - 저장되고, 다시 로그인하지 않아도 상단바와 마이페이지에 새 이름이 바로 보임")
	void changeName_reflectedImmediately() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changeName(session, "  새이름  "))
				.andExpect(redirectedUrl("/mypage"));

		assertThat(reload(student).getName()).isEqualTo("새이름");
		mockMvc.perform(get("/mypage").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("이름을 변경했습니다.")))
				.andExpect(content().string(containsString("새이름")))
				.andExpect(content().string(not(containsString("테스터"))));
		// 다른 화면의 상단바에도 새 이름
		mockMvc.perform(get("/student/assignments").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("새이름")))
				.andExpect(content().string(not(containsString("테스터"))));
	}

	@Test
	@DisplayName("이름 변경 - 빈 이름 · 공백뿐 · 51자는 에러가 표시되고 저장되지 않음, 50자는 허용")
	void changeName_validation() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		for (String invalid : new String[] { "", "   ", "가".repeat(51) }) {
			mockMvc.perform(changeName(session, invalid))
					.andExpect(status().isOk())
					.andExpect(view().name("mypage"))
					.andExpect(model().attributeHasFieldErrors("nameChangeRequest", "name"));
		}
		assertThat(reload(student).getName()).isEqualTo("테스터");

		mockMvc.perform(changeName(session, "가".repeat(50))).andExpect(redirectedUrl("/mypage"));
		assertThat(reload(student).getName()).hasSize(50);
	}

	@Test
	@DisplayName("이름 변경 - 요청에 이메일 · 학번 · 역할 · 아이디를 실어 보내도 무시되고 이름만 바뀜")
	void changeName_forgedParamsIgnored() throws Exception {
		User instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(post("/mypage/name").param("name", "새이름")
						.param("email", "hacker@evil.com").param("studentNo", "99999999")
						.param("role", "INSTRUCTOR").param("id", instructor.getId().toString())
						.param("userId", instructor.getId().toString())
						.session(session).with(csrf()))
				.andExpect(redirectedUrl("/mypage"));

		User after = reload(student);
		assertThat(after.getName()).isEqualTo("새이름");
		assertThat(after.getEmail()).isEqualTo("student@test.com");
		assertThat(after.getStudentNo()).isEqualTo("20260000");
		assertThat(after.getRole()).isEqualTo(Role.STUDENT);
		assertThat(reload(instructor).getName()).isEqualTo("테스터");
		// 권한도 그대로라 강사 화면은 여전히 403
		mockMvc.perform(get("/instructor/courses").session(session)).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("이름 변경 - 다른 사용자의 이름은 바뀌지 않음 (항상 로그인한 본인만)")
	void changeName_onlySelf() throws Exception {
		User other = createUser("other@test.com", Role.STUDENT);
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changeName(session, "새이름")).andExpect(redirectedUrl("/mypage"));

		assertThat(reload(other).getName()).isEqualTo("테스터");
	}

	@Test
	@DisplayName("이름 변경 - 이름의 HTML은 상단바와 마이페이지에서 이스케이프되어 표시됨 (XSS)")
	void changeName_xssEscaped() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changeName(session, "<script>alert(1)</script>")).andExpect(redirectedUrl("/mypage"));

		mockMvc.perform(get("/mypage").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("이름 변경 - 강사 계정도 변경되고 상단바에 반영됨")
	void changeName_instructor() throws Exception {
		createUser("teacher@test.com", Role.INSTRUCTOR);
		MockHttpSession session = login("teacher@test.com", PASSWORD);

		mockMvc.perform(changeName(session, "박강사")).andExpect(redirectedUrl("/mypage"));

		mockMvc.perform(get("/instructor/courses").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("박강사")));
	}

	// ---------- 비밀번호 변경 ----------

	@Test
	@DisplayName("비밀번호 변경 - 변경 직후에도 로그인이 유지되고, 새 비밀번호로 로그인되며 이전 비밀번호는 거부됨")
	void changePassword_sessionKeptAndLoginSwitches() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changePassword(session, PASSWORD, "newpassword1", "newpassword1"))
				.andExpect(redirectedUrl("/mypage"));

		// 같은 세션은 계속 로그인 상태
		mockMvc.perform(get("/mypage").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("비밀번호를 변경했습니다.")));
		mockMvc.perform(get("/student/assignments").session(session)).andExpect(status().isOk());
		// 새 비밀번호로 로그인, 이전 비밀번호는 거부
		mockMvc.perform(formLogin("/login").userParameter("email").user("student@test.com").password("newpassword1"))
				.andExpect(authenticated());
		mockMvc.perform(formLogin("/login").userParameter("email").user("student@test.com").password(PASSWORD))
				.andExpect(unauthenticated());
		assertThat(reload(student).getPassword()).startsWith("$2").isNotEqualTo("newpassword1");
	}

	@Test
	@DisplayName("비밀번호 변경 - 현재 비밀번호가 틀리면 에러가 표시되고 바뀌지 않음, 입력한 비밀번호는 응답에 나오지 않음")
	void changePassword_wrongCurrent() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changePassword(session, "wrongpassword", "newpassword1", "newpassword1"))
				.andExpect(status().isOk())
				.andExpect(view().name("mypage"))
				.andExpect(model().attributeHasFieldErrors("passwordChangeRequest", "currentPassword"))
				.andExpect(content().string(containsString("현재 비밀번호가 올바르지 않습니다.")))
				.andExpect(content().string(not(containsString("wrongpassword"))))
				.andExpect(content().string(not(containsString("newpassword1"))));

		assertThat(passwordEncoder.matches(PASSWORD, reload(student).getPassword())).isTrue();
	}

	@Test
	@DisplayName("비밀번호 변경 - 7자는 에러, 8자는 허용 (경계)")
	void changePassword_lengthBoundary() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changePassword(session, PASSWORD, "abcdefg", "abcdefg"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("passwordChangeRequest", "newPassword"))
				.andExpect(content().string(containsString("비밀번호는 8자 이상이어야 합니다.")));
		assertThat(passwordEncoder.matches(PASSWORD, reload(student).getPassword())).isTrue();

		mockMvc.perform(changePassword(session, PASSWORD, "abcdefgh", "abcdefgh"))
				.andExpect(redirectedUrl("/mypage"));
		assertThat(passwordEncoder.matches("abcdefgh", reload(student).getPassword())).isTrue();
	}

	@Test
	@DisplayName("비밀번호 변경 - 확인이 다르거나, 현재와 같거나, 비어 있으면 에러가 표시되고 바뀌지 않음")
	void changePassword_otherErrors() throws Exception {
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(changePassword(session, PASSWORD, "newpassword1", "newpassword2"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("passwordChangeRequest", "newPasswordConfirm"))
				.andExpect(content().string(containsString("새 비밀번호와 확인이 일치하지 않습니다.")));
		mockMvc.perform(changePassword(session, PASSWORD, PASSWORD, PASSWORD))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("passwordChangeRequest", "newPassword"))
				.andExpect(content().string(containsString("새 비밀번호가 현재 비밀번호와 같습니다.")));
		mockMvc.perform(changePassword(session, "", "", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("현재 비밀번호를 입력해 주세요.")))
				.andExpect(content().string(containsString("새 비밀번호를 입력해 주세요.")));

		assertThat(passwordEncoder.matches(PASSWORD, reload(student).getPassword())).isTrue();
	}

	@Test
	@DisplayName("비밀번호 변경 - 요청에 역할 · 이메일을 실어 보내도 무시되고, 다른 사용자의 비밀번호는 바뀌지 않음")
	void changePassword_forgedAndSelfOnly() throws Exception {
		User other = createUser("other@test.com", Role.STUDENT);
		String otherBefore = other.getPassword();
		MockHttpSession session = login("student@test.com", PASSWORD);

		mockMvc.perform(post("/mypage/password").param("currentPassword", PASSWORD)
						.param("newPassword", "newpassword1").param("newPasswordConfirm", "newpassword1")
						.param("role", "INSTRUCTOR").param("email", "other@test.com")
						.param("id", other.getId().toString())
						.session(session).with(csrf()))
				.andExpect(redirectedUrl("/mypage"));

		assertThat(reload(other).getPassword()).isEqualTo(otherBefore);
		assertThat(reload(student).getRole()).isEqualTo(Role.STUDENT);
		assertThat(reload(student).getEmail()).isEqualTo("student@test.com");
	}

	@Test
	@DisplayName("비밀번호 변경 - 강사 계정도 변경되고 새 비밀번호로 로그인됨")
	void changePassword_instructor() throws Exception {
		createUser("teacher@test.com", Role.INSTRUCTOR);
		MockHttpSession session = login("teacher@test.com", PASSWORD);

		mockMvc.perform(changePassword(session, PASSWORD, "newpassword1", "newpassword1"))
				.andExpect(redirectedUrl("/mypage"));

		mockMvc.perform(get("/instructor/courses").session(session)).andExpect(status().isOk());
		mockMvc.perform(formLogin("/login").userParameter("email").user("teacher@test.com").password("newpassword1"))
				.andExpect(authenticated());
		mockMvc.perform(formLogin("/login").userParameter("email").user("teacher@test.com").password(PASSWORD))
				.andExpect(unauthenticated());
	}
}
