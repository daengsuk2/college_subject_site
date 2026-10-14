package kr.ac.ync.midterm.submission.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.ac.ync.midterm.submission.dto.response.GradeFormResponse;
import lombok.Getter;
import lombok.Setter;

/**
 * 채점 폼 입력값. 배점 이하인지(B6)는 과제마다 달라서 서비스에서 최종 검사한다.
 */
@Getter
@Setter
public class GradeRequest {

	@NotNull(message = "점수를 입력해 주세요.")
	@Min(value = 0, message = "점수는 0 이상이어야 합니다.")
	private Integer score;

	@Size(max = 2000, message = "피드백은 2000자 이하로 입력해 주세요.")
	private String feedback;

	/** 이미 채점한 제출을 다시 열 때 기존 값을 채우기 위한 변환 */
	public static GradeRequest from(GradeFormResponse form) {
		GradeRequest request = new GradeRequest();
		request.setScore(form.score());
		request.setFeedback(form.feedback());
		return request;
	}
}
