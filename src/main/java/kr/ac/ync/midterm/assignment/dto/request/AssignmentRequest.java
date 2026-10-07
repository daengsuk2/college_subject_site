package kr.ac.ync.midterm.assignment.dto.request;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentResponse;
import lombok.Getter;
import lombok.Setter;

/**
 * 과제 등록 · 수정 폼 입력값. 일시는 datetime-local 입력(yyyy-MM-ddTHH:mm) 형식이다.
 * 종료일시 > 시작일시(B7)는 서비스에서 검사한다.
 */
@Getter
@Setter
public class AssignmentRequest {

	@NotBlank(message = "제목을 입력해 주세요.")
	@Size(max = 200, message = "제목은 200자 이하로 입력해 주세요.")
	private String title;

	@NotBlank(message = "내용을 입력해 주세요.")
	private String content;

	@NotNull(message = "시작일시를 입력해 주세요.")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime startAt;

	@NotNull(message = "종료일시를 입력해 주세요.")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime endAt;

	@NotNull(message = "배점을 입력해 주세요.")
	@Min(value = 1, message = "배점은 1 이상이어야 합니다.")
	private Integer maxScore;

	/** 첨부파일 (선택). 확장자 · 크기 검증은 FileStorage에서 한다. */
	private MultipartFile file;

	/** 수정 폼에 기존 값을 채우기 위한 변환 */
	public static AssignmentRequest from(AssignmentResponse assignment) {
		AssignmentRequest request = new AssignmentRequest();
		request.setTitle(assignment.title());
		request.setContent(assignment.content());
		request.setStartAt(assignment.startAt());
		request.setEndAt(assignment.endAt());
		request.setMaxScore(assignment.maxScore());
		return request;
	}
}
