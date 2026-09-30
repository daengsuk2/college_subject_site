package kr.ac.ync.midterm.global.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	/**
	 * 로그인 (U2). 이메일로 사용자를 찾는다. 가입 때와 같은 방식으로 이메일을 정규화한다.
	 * 없는 이메일이어도 Spring Security가 "이메일 또는 비밀번호 오류"로 통일해서 처리한다.
	 */
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(User.normalizeEmail(email))
				.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
		return new CustomUserDetails(user);
	}
}
