package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * X29: 이의 신청 규칙 위반 (채점 전, 이미 신청함, 신청이 없음, 이미 답변함).
 */
public class ObjectionException extends CustomException {

	public ObjectionException(String message) {
		super(message, HttpStatus.CONFLICT);
	}
}
