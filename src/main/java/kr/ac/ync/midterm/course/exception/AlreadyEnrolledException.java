package kr.ac.ync.midterm.course.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class AlreadyEnrolledException extends CustomException {

	public AlreadyEnrolledException() {
		super("이미 등록된 강좌입니다.", HttpStatus.CONFLICT);
	}
}
