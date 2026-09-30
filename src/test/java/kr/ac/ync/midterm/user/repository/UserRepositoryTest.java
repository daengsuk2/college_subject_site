package kr.ac.ync.midterm.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

@SpringBootTest
@Transactional
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	private User savedUser(String email) {
		return userRepository.saveAndFlush(
				User.builder()
						.email(email)
						.password("encodedPassword")
						.name("홍길동")
						.studentNo("20260001")
						.role(Role.STUDENT)
						.build()
		);
	}

	@Test
	@DisplayName("existsByEmail - 중복 이메일 확인")
	void existsByEmail_success() {
		// given
		savedUser("hong@test.com");

		// when & then
		assertThat(userRepository.existsByEmail("hong@test.com")).isTrue();
		assertThat(userRepository.existsByEmail("noSuchEmail@test.com")).isFalse();
	}

	@Test
	@DisplayName("save - 같은 이메일은 UNIQUE 제약으로 저장할 수 없음")
	void save_duplicateEmail() {
		// given
		savedUser("hong@test.com");

		// when & then
		assertThatThrownBy(() -> savedUser("hong@test.com"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
