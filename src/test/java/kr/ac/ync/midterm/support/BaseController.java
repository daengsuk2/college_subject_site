package kr.ac.ync.midterm.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

@SpringBootTest
@Transactional
public abstract class BaseController {

	@Autowired
	protected WebApplicationContext webApplicationContext;
	// Spring Web 애플리케이션 컨텍스트

	protected MockMvc mockMvc;
	// Controller 테스트를 위한 MockMVC 객체

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	protected User student;
	// 기본 학생 사용자

	@BeforeEach
	protected void setUpMockMvc() {
		// Spring MVC 처럼 test환경의 MVC (Security 필터까지 적용)
		this.mockMvc =
				MockMvcBuilders
						.webAppContextSetup(webApplicationContext)
						.apply(springSecurity())
						.build();
		this.student = createUser("student@test.com", Role.STUDENT);
	}

	protected User createUser(String email, Role role) {
		return userRepository.save(
				User.builder()
						.email(email)
						.password(passwordEncoder.encode("password123"))
						.name("테스터")
						.studentNo(role == Role.STUDENT ? "20260000" : null)
						.role(role)
						.build()
		);
	}
}
