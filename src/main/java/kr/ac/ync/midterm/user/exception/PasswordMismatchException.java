package kr.ac.ync.midterm.user.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class PasswordMismatchException extends CustomException {

	public PasswordMismatchException() {
		super("새 비밀번호와 확인이 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
	}
}
