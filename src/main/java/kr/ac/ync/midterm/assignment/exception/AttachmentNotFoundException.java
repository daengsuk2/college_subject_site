package kr.ac.ync.midterm.assignment.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class AttachmentNotFoundException extends CustomException {

	public AttachmentNotFoundException() {
		super("첨부 파일이 없습니다.", HttpStatus.NOT_FOUND);
	}
}
