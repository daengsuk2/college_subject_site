package kr.ac.ync.midterm.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 마이페이지 이름 변경 폼. 이메일 · 학번 · 역할은 받지 않는다 (요청에 실어 보내도 무시된다).
 */
@Getter
@Setter
public class NameChangeRequest {

	@NotBlank(message = "이름을 입력해 주세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
	private String name;
}
