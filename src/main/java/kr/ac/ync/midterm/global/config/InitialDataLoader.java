package kr.ac.ync.midterm.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 강사 초기 계정 생성 (U4). 강사는 회원가입으로 만들 수 없고 앱 시작 시 이 로더가 만든다.
 * 이메일·이름·비밀번호는 .env(INSTRUCTOR_*)에서 읽으며, 비밀번호가 비어 있으면 만들지 않는다.
 * 이미 같은 이메일이 있으면 건너뛰므로(비밀번호도 바꾸지 않는다) 여러 번 실행해도 안전하다.
 */
@Component
public class InitialDataLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(InitialDataLoader.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String name;
	private final String password;

	public InitialDataLoader(UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.instructor.email:}") String email,
			@Value("${app.instructor.name:강사}") String name,
			@Value("${app.instructor.password:}") String password) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.name = name;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (isBlank(email) || isBlank(password)) {
			log.warn("강사 초기 계정을 만들지 않았습니다. .env에 INSTRUCTOR_EMAIL, INSTRUCTOR_PASSWORD를 설정하세요.");
			return;
		}

		String normalizedEmail = User.normalizeEmail(email);
		if (userRepository.existsByEmail(normalizedEmail)) {
			log.info("강사 초기 계정이 이미 있어 건너뜁니다.");
			return;
		}

		userRepository.save(User.builder()
				.email(normalizedEmail)
				.password(passwordEncoder.encode(password))
				.name(isBlank(name) ? "강사" : name.trim())
				.role(Role.INSTRUCTOR)
				.build());
		log.info("강사 초기 계정을 생성했습니다.");
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
