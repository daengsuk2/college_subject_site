package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * B4: 채점 완료(GRADED)된 제출은 재제출할 수 없다.
 */
public class AlreadyGradedException extends CustomException {

	public AlreadyGradedException() {
		super("채점 완료된 과제입니다.", HttpStatus.CONFLICT);
	}
}
