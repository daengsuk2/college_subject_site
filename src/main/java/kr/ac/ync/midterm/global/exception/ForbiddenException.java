package kr.ac.ync.midterm.global.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends CustomException {

	public ForbiddenException() {
		super("접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
	}
}
