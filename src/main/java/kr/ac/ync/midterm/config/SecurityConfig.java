package kr.ac.ync.midterm.config;

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

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/css/**", "/js/**", "/vendor/**", "/img/**").permitAll()
				.requestMatchers("/login", "/signup", "/error/**").permitAll()
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("email")
				.defaultSuccessUrl("/", true)
				.permitAll())
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
