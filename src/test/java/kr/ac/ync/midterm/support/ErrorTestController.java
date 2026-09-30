package kr.ac.ync.midterm.support;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import kr.ac.ync.midterm.global.exception.CustomException;
import kr.ac.ync.midterm.global.exception.ForbiddenException;

/**
 * GlobalExceptionHandler 검증용 테스트 전용 컨트롤러 (테스트 코드에만 존재한다).
 */
@Controller
public class ErrorTestController {

	@GetMapping("/test-errors/forbidden")
	public String forbidden() {
		throw new ForbiddenException();
	}

	@GetMapping("/test-errors/not-found")
	public String notFound() {
		throw new CustomException("존재하지 않는 강좌입니다.", HttpStatus.NOT_FOUND);
	}

	@GetMapping("/test-errors/conflict")
	public String conflict() {
		throw new CustomException("이미 처리된 요청입니다.", HttpStatus.CONFLICT);
	}

	/** 로그인하지 않은 사용자에게 오류 페이지(레이아웃 포함)가 그려지는지 확인하기 위한 경로 (/error/** 는 permitAll) */
	@GetMapping("/error/anonymous-render")
	public String anonymousRender() {
		return "error/403";
	}
}
