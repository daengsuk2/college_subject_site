package kr.ac.ync.midterm.user.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class WrongCurrentPasswordException extends CustomException {

	public WrongCurrentPasswordException() {
		super("현재 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
	}
}
