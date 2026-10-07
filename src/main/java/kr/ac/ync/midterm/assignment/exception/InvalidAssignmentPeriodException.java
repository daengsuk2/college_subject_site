package kr.ac.ync.midterm.assignment.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * B7: 종료일시는 시작일시보다 뒤여야 한다.
 */
public class InvalidAssignmentPeriodException extends CustomException {

	public InvalidAssignmentPeriodException() {
		super("종료일시는 시작일시보다 뒤여야 합니다.", HttpStatus.BAD_REQUEST);
	}
}
