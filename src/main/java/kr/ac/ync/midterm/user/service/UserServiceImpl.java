package kr.ac.ync.midterm.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.dto.request.SignupRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;
import kr.ac.ync.midterm.user.exception.DuplicateEmailException;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 학생 회원가입 (U1). 이메일은 {@link User#normalizeEmail}로 정규화해 저장한다.
	 */
	@Override
	@Transactional
	public UserResponse signup(SignupRequest request) {
		String email = User.normalizeEmail(request.getEmail());

		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		User user = User.builder()
				.email(email)
				.password(passwordEncoder.encode(request.getPassword()))
				.name(request.getName().trim())
				.studentNo(request.getStudentNo().trim())
				.role(Role.STUDENT)
				.build();

		try {
			return UserResponse.from(userRepository.saveAndFlush(user));
		} catch (DataIntegrityViolationException e) {
			// 동시 가입으로 UNIQUE(email) 제약에 걸린 경우
			throw new DuplicateEmailException();
		}
	}
}
