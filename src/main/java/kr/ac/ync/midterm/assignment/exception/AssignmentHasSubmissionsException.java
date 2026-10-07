package kr.ac.ync.midterm.assignment.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * B8: 제출이 1건이라도 있는 과제는 삭제할 수 없다.
 */
public class AssignmentHasSubmissionsException extends CustomException {

	public AssignmentHasSubmissionsException() {
		super("제출물이 있어 삭제할 수 없습니다.", HttpStatus.CONFLICT);
	}
}
