package kr.ac.ync.midterm.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 강좌 개설 폼 입력값.
 */
@Getter
@Setter
public class CourseCreateRequest {

	@NotBlank(message = "강좌명을 입력해 주세요.")
	@Size(max = 100, message = "강좌명은 100자 이하로 입력해 주세요.")
	private String name;
}
