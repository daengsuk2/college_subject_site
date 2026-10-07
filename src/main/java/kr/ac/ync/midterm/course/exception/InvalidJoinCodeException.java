package kr.ac.ync.midterm.course.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class InvalidJoinCodeException extends CustomException {

	public InvalidJoinCodeException() {
		super("참여코드가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
	}
}
