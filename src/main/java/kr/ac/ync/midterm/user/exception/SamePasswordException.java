package kr.ac.ync.midterm.user.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class SamePasswordException extends CustomException {

	public SamePasswordException() {
		super("새 비밀번호가 현재 비밀번호와 같습니다.", HttpStatus.BAD_REQUEST);
	}
}
