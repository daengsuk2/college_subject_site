package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class SubmissionFileNotFoundException extends CustomException {

	public SubmissionFileNotFoundException() {
		super("첨부된 파일이 없습니다.", HttpStatus.NOT_FOUND);
	}
}
