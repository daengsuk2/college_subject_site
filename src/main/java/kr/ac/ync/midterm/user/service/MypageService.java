package kr.ac.ync.midterm.user.service;

import kr.ac.ync.midterm.user.dto.request.PasswordChangeRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;

public interface MypageService {

	/** 로그인한 본인의 정보. */
	UserResponse findMe(Long userId);

	/** 이름 변경 (앞뒤 공백 제거). 변경된 정보를 돌려준다. */
	UserResponse changeName(Long userId, String name);

	/**
	 * 비밀번호 변경. 현재 비밀번호가 맞아야 하고, 새 비밀번호와 확인이 같아야 하며, 현재와 달라야 한다.
	 * 8자 이상 규칙은 폼 검증과 함께 서비스에서도 확인한다.
	 */
	void changePassword(Long userId, PasswordChangeRequest request);
}
