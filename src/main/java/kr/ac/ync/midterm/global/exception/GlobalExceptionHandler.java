package kr.ac.ync.midterm.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 컨트롤러에서 처리하지 않은 CustomException을 화면으로 변환한다.
 * 403은 권한 없음 페이지, 404는 없는 페이지, 그 외는 공통 오류 페이지를 보여 준다.
 * (폼 에러나 알림은 각 컨트롤러가 직접 처리한다.)
 */
@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CustomException.class)
	public String handleCustomException(CustomException e, HttpServletResponse response, Model model) {
		response.setStatus(e.getStatus().value());
		model.addAttribute("message", e.getMessage());

		if (e.getStatus() == HttpStatus.FORBIDDEN) {
			return "error/403";
		}
		if (e.getStatus() == HttpStatus.NOT_FOUND) {
			return "error/404";
		}
		return "error/error";
	}

	/**
	 * 업로드 파일이 최대 크기(10MB)를 넘으면 컨트롤러에 닿기 전에 발생한다. 413과 안내 문구를 보여 준다.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public String handleMaxUploadSize(HttpServletResponse response, Model model) {
		response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
		model.addAttribute("message", "파일 크기는 10MB 이하여야 합니다. 뒤로 가서 더 작은 파일을 선택해 주세요.");
		return "error/error";
	}
}
