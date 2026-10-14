package kr.ac.ync.midterm.submission.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * 같은 학생의 제출이 동시에 들어와 DB의 UNIQUE(과제, 학생) 제약에 걸린 경우 (B3).
 * 한 건은 이미 저장되었으므로 화면을 새로 고쳐 확인하도록 안내한다.
 */
public class AlreadySubmittedException extends CustomException {

	public AlreadySubmittedException() {
		super("이미 제출되었습니다. 화면을 새로 고쳐 확인해 주세요.", HttpStatus.CONFLICT);
	}
}
