package kr.ac.ync.midterm.submission.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 강사의 이의 답변 입력값 (X29). 점수를 입력하면 재채점으로 점수가 바뀐다. (배점 이하인지(B6)는 서비스에서 검사)
 */
@Getter
@Setter
public class ObjectionResponseRequest {

	@NotBlank(message = "답변을 입력해 주세요.")
	@Size(max = ObjectionRequest.MAX_LENGTH, message = "답변은 500자 이하로 입력해 주세요.")
	private String response;

	@Min(value = 0, message = "점수는 0 이상이어야 합니다.")
	private Integer score;
}
