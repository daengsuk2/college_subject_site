package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * B6: 점수는 0 이상 배점(max_score) 이하여야 한다.
 */
public class InvalidScoreException extends CustomException {

	public InvalidScoreException(int maxScore) {
		super("점수는 0 이상 " + maxScore + " 이하여야 합니다.", HttpStatus.BAD_REQUEST);
	}
}
