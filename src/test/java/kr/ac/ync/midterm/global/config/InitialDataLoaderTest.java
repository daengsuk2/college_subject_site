package kr.ac.ync.midterm.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class InitialDataLoaderTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private InitialDataLoader loader(String email, String name, String password) {
		return new InitialDataLoader(userRepository, passwordEncoder, email, name, password);
	}

	@Test
	@DisplayName("run - 강사 계정이 없으면 INSTRUCTOR로 생성됨 (비밀번호는 암호화)")
	void run_createsInstructor() {
		// when
		loader("Teacher@Test.com", "이복동", "password123").run(null);
		// then
		User saved = userRepository.findByEmail("teacher@test.com").orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.INSTRUCTOR);
		assertThat(saved.getName()).isEqualTo("이복동");
		assertThat(saved.getStudentNo()).isNull();
		assertThat(saved.getPassword()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
	}

	@Test
	@DisplayName("run - 두 번 실행해도 중복 생성되지 않고 기존 비밀번호도 바꾸지 않음")
	void run_idempotent() {
		// given
		loader("teacher@test.com", "이복동", "password123").run(null);
		long before = userRepository.count();
		// when
		loader("teacher@test.com", "다른이름", "otherPassword").run(null);
		// then
		assertThat(userRepository.count()).isEqualTo(before);
		User saved = userRepository.findByEmail("teacher@test.com").orElseThrow();
		assertThat(saved.getName()).isEqualTo("이복동");
		assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
	}

	@Test
	@DisplayName("run - 비밀번호가 비어 있으면 계정을 만들지 않음")
	void run_blankPassword() {
		// given
		long before = userRepository.count();
		// when
		loader("teacher@test.com", "이복동", "").run(null);
		// then
		assertThat(userRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("run - 이메일이 비어 있으면 계정을 만들지 않음")
	void run_blankEmail() {
		// given
		long before = userRepository.count();
		// when
		loader("", "이복동", "password123").run(null);
		// then
		assertThat(userRepository.count()).isEqualTo(before);
	}
}
