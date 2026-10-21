package kr.ac.ync.midterm.global.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** 로그인 상태 유지 쿠키(14일)의 유효 기간 */
	private static final int REMEMBER_ME_SECONDS = 14 * 24 * 60 * 60;

	/**
	 * 로그인 상태 유지 쿠키의 서명 키. 환경변수 REMEMBER_ME_KEY로 지정하면 서버를 다시 시작해도 유지되고,
	 * 지정하지 않으면 시작할 때마다 새로 만들어져 이전 쿠키는 쓸 수 없게 된다. (키를 코드에 적지 않는다)
	 */
	@Value("${app.remember-me.key:}")
	private String rememberMeKey;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		String key = rememberMeKey == null || rememberMeKey.isBlank() ? UUID.randomUUID().toString() : rememberMeKey;
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/css/**", "/js/**", "/vendor/**", "/img/**").permitAll()
				.requestMatchers("/login", "/signup", "/error/**").permitAll()
				.requestMatchers("/instructor/**").hasRole("INSTRUCTOR")
				.requestMatchers("/student/**").hasRole("STUDENT")
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("email")
				.defaultSuccessUrl("/", true)
				.permitAll())
			.rememberMe(remember -> remember
				.key(key)
				.rememberMeParameter("remember-me")
				.tokenValiditySeconds(REMEMBER_ME_SECONDS))
			.logout(logout -> logout
				.logoutSuccessUrl("/login?logout")
				.permitAll());
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
