package kr.ac.ync.midterm.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 수강 등록 폼 입력값 (참여코드).
 */
@Getter
@Setter
public class EnrollRequest {

	@NotBlank(message = "참여코드를 입력해 주세요.")
	@Size(max = 20, message = "참여코드가 올바르지 않습니다.")
	private String joinCode;
}
