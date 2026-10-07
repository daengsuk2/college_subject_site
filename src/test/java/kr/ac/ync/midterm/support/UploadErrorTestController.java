package kr.ac.ync.midterm.support;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 업로드 최대 크기 초과 처리 확인용 테스트 전용 컨트롤러 (테스트 코드에만 존재한다).
 * MockMvc는 서블릿의 업로드 크기 제한을 적용하지 않으므로 같은 예외를 직접 던진다.
 */
@Controller
public class UploadErrorTestController {

	@GetMapping("/test-errors/too-large")
	public String tooLarge() {
		throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
	}
}
