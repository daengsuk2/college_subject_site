package kr.ac.ync.midterm.attachment.exception;

import kr.ac.ync.midterm.global.storage.InvalidFileException;

/**
 * X17: 과제 · 제출당 첨부는 최대 개수까지만 가능하다.
 */
public class TooManyAttachmentsException extends InvalidFileException {

	public TooManyAttachmentsException(int maxFiles) {
		super("첨부파일은 최대 " + maxFiles + "개까지 올릴 수 있습니다.");
	}
}
