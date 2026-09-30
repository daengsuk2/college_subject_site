package kr.ac.ync.midterm.user.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class DuplicateEmailException extends CustomException {

	public DuplicateEmailException() {
		super("이미 가입된 이메일입니다.", HttpStatus.CONFLICT);
	}
}
