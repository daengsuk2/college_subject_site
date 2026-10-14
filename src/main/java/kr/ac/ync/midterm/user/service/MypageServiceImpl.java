package kr.ac.ync.midterm.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.dto.request.PasswordChangeRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;
import kr.ac.ync.midterm.user.exception.PasswordMismatchException;
import kr.ac.ync.midterm.user.exception.SamePasswordException;
import kr.ac.ync.midterm.user.exception.WrongCurrentPasswordException;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 마이페이지 (X1). 대상 사용자는 요청 값이 아니라 로그인 정보에서 정해진 userId이므로 본인만 바꿀 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MypageServiceImpl implements MypageService {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public UserResponse findMe(Long userId) {
		return UserResponse.from(findUser(userId));
	}

	@Override
	@Transactional
	public UserResponse changeName(Long userId, String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("이름은 비어 있을 수 없습니다.");
		}
		User user = findUser(userId);
		user.changeName(name.trim());
		return UserResponse.from(user);
	}

	@Override
	@Transactional
	public void changePassword(Long userId, PasswordChangeRequest request) {
		User user = findUser(userId);

		if (request.getCurrentPassword() == null
				|| !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
			throw new WrongCurrentPasswordException();
		}
		String newPassword = request.getNewPassword();
		if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
		}
		if (!newPassword.equals(request.getNewPasswordConfirm())) {
			throw new PasswordMismatchException();
		}
		if (newPassword.equals(request.getCurrentPassword())) {
			throw new SamePasswordException();
		}

		user.changePassword(passwordEncoder.encode(newPassword));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("로그인한 사용자를 찾을 수 없습니다."));
	}
}
