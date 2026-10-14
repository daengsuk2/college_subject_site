package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class SubmissionNotFoundException extends CustomException {

	public SubmissionNotFoundException() {
		super("존재하지 않는 제출입니다.", HttpStatus.NOT_FOUND);
	}
}
