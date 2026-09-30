package kr.ac.ync.midterm.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.dto.request.SignupRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;
import kr.ac.ync.midterm.user.exception.DuplicateEmailException;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class UserServiceImplTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private SignupRequest request(String email, String password) {
		SignupRequest request = new SignupRequest();
		request.setStudentNo("20260001");
		request.setName("홍길동");
		request.setEmail(email);
		request.setPassword(password);
		return request;
	}

	@Test
	@DisplayName("signup - 학생(STUDENT)으로 가입됨")
	void signup_success() {
		// given
		SignupRequest request = request("hong@test.com", "password123");
		// when
		UserResponse response = userService.signup(request);
		// then
		assertThat(response.id()).isNotNull();
		assertThat(response)
				.extracting(UserResponse::email, UserResponse::name, UserResponse::studentNo, UserResponse::role)
				.containsExactly("hong@test.com", "홍길동", "20260001", Role.STUDENT);
	}

	@Test
	@DisplayName("signup - 비밀번호는 암호화되어 저장됨")
	void signup_passwordEncoded() {
		// given
		SignupRequest request = request("hong@test.com", "password123");
		// when
		userService.signup(request);
		// then
		User saved = userRepository.findAll().getFirst();
		assertThat(saved.getPassword()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
	}

	@Test
	@DisplayName("signup - 이메일은 공백 제거 후 소문자로 저장됨")
	void signup_emailNormalized() {
		// given
		SignupRequest request = request("  Hong@Test.com ", "password123");
		// when
		UserResponse response = userService.signup(request);
		// then
		assertThat(response.email()).isEqualTo("hong@test.com");
		assertThat(userRepository.existsByEmail("hong@test.com")).isTrue();
	}

	@Test
	@DisplayName("signup - 중복 이메일이면 예외 (대소문자만 달라도 중복)")
	void signup_duplicateEmail() {
		// given
		userService.signup(request("hong@test.com", "password123"));
		SignupRequest duplicated = request("HONG@test.com", "password456");
		// when & then
		assertThatThrownBy(() -> userService.signup(duplicated))
				.isInstanceOf(DuplicateEmailException.class);
	}
}
