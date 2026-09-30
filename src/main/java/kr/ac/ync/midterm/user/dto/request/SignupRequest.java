package kr.ac.ync.midterm.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 학생 회원가입 폼 입력값. role 필드를 두지 않아 가입자는 항상 STUDENT가 된다.
 */
@Getter
@Setter
public class SignupRequest {

	@NotBlank(message = "학번을 입력해 주세요.")
	@Pattern(regexp = "^\\d*$", message = "학번은 숫자만 입력할 수 있습니다.")
	private String studentNo;

	@NotBlank(message = "이름을 입력해 주세요.")
	private String name;

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
	private String password;
}
