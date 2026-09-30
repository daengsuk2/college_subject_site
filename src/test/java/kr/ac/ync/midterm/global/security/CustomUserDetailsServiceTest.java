package kr.ac.ync.midterm.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
class CustomUserDetailsServiceTest {

	@Autowired
	private CustomUserDetailsService customUserDetailsService;

	@Autowired
	private UserRepository userRepository;

	private User savedUser(String email, Role role) {
		return userRepository.save(
				User.builder()
						.email(email)
						.password("encodedPassword")
						.name("홍길동")
						.studentNo(role == Role.STUDENT ? "20260001" : null)
						.role(role)
						.build()
		);
	}

	@Test
	@DisplayName("loadUserByUsername - 학생은 ROLE_STUDENT 권한을 가짐")
	void loadUserByUsername_student() {
		// given
		savedUser("hong@test.com", Role.STUDENT);
		// when
		UserDetails details = customUserDetailsService.loadUserByUsername("hong@test.com");
		// then
		assertThat(details.getUsername()).isEqualTo("hong@test.com");
		assertThat(details.getPassword()).isEqualTo("encodedPassword");
		assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_STUDENT");
		assertThat(((CustomUserDetails) details).getName()).isEqualTo("홍길동");
	}

	@Test
	@DisplayName("loadUserByUsername - 강사는 ROLE_INSTRUCTOR 권한을 가짐")
	void loadUserByUsername_instructor() {
		// given
		savedUser("teacher@test.com", Role.INSTRUCTOR);
		// when
		UserDetails details = customUserDetailsService.loadUserByUsername("teacher@test.com");
		// then
		assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_INSTRUCTOR");
	}

	@Test
	@DisplayName("loadUserByUsername - 대소문자·공백이 달라도 같은 이메일로 조회됨")
	void loadUserByUsername_emailNormalized() {
		// given
		savedUser("hong@test.com", Role.STUDENT);
		// when
		UserDetails details = customUserDetailsService.loadUserByUsername("  HONG@Test.com ");
		// then
		assertThat(details.getUsername()).isEqualTo("hong@test.com");
	}

	@Test
	@DisplayName("loadUserByUsername - 없는 이메일이면 예외")
	void loadUserByUsername_notFound() {
		assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("noSuchEmail@test.com"))
				.isInstanceOf(UsernameNotFoundException.class);
	}
}
