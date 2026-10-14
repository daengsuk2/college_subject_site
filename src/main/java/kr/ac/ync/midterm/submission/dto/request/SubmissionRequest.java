package kr.ac.ync.midterm.submission.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;
import lombok.Getter;
import lombok.Setter;

/**
 * 과제 제출 폼 입력값. 기간(B1) · 수강(B2) · 채점 완료(B4)는 서비스에서 검사한다.
 */
@Getter
@Setter
public class SubmissionRequest {

	@NotBlank(message = "제출 내용을 입력해 주세요.")
	@Size(max = 10000, message = "제출 내용은 10000자 이하로 입력해 주세요.")
	private String content;

	/** 첨부파일 (선택). 확장자 · 크기 검증은 FileStorage에서 한다. */
	private MultipartFile file;

	/** 재제출 폼에 기존 내용을 채우기 위한 변환 */
	public static SubmissionRequest from(SubmissionResponse submission) {
		SubmissionRequest request = new SubmissionRequest();
		request.setContent(submission.content());
		return request;
	}
}
