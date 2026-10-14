package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * B1: 제출은 시작일시 ≤ 현재 ≤ 종료일시 일 때만 가능하다.
 */
public class SubmissionPeriodException extends CustomException {

	public SubmissionPeriodException() {
		super("제출 기간이 아닙니다.", HttpStatus.BAD_REQUEST);
	}
}
