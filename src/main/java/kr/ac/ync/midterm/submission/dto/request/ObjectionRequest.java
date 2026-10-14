package kr.ac.ync.midterm.submission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 학생의 이의 신청 입력값 (X29).
 */
@Getter
@Setter
public class ObjectionRequest {

	public static final int MAX_LENGTH = 500;

	@NotBlank(message = "이의 내용을 입력해 주세요.")
	@Size(max = MAX_LENGTH, message = "이의 내용은 500자 이하로 입력해 주세요.")
	private String content;
}
