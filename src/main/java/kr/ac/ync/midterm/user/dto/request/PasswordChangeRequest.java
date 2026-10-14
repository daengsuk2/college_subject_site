package kr.ac.ync.midterm.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 마이페이지 비밀번호 변경 폼. 새 비밀번호 규칙은 가입(SignupRequest)과 같다 (8자 이상).
 * 현재 비밀번호 확인, 확인 입력 일치, 현재와 같은지는 서비스에서 검사한다.
 */
@Getter
@Setter
public class PasswordChangeRequest {

	@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
	private String currentPassword;

	@NotBlank(message = "새 비밀번호를 입력해 주세요.")
	@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
	private String newPassword;

	@NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
	private String newPasswordConfirm;
}
