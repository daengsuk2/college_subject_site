package kr.ac.ync.midterm.global.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 이름이나 비밀번호를 바꾼 뒤 현재 로그인 정보를 새 값으로 바꿔 세션에 저장한다.
 * (상단바 이름이 로그인 시점의 정보를 읽으므로, 갱신하지 않으면 다시 로그인할 때까지 옛 이름이 보인다.
 * 세션을 끊지 않고 그대로 이어 가므로 변경 직후에도 로그인 상태가 유지된다.)
 */
@Component
@RequiredArgsConstructor
public class AuthenticationRefresher {

	private final UserRepository userRepository;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public void refresh(Long userId, HttpServletRequest request, HttpServletResponse response) {
		CustomUserDetails principal = new CustomUserDetails(userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("로그인한 사용자를 찾을 수 없습니다.")));

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}
