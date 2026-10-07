package kr.ac.ync.midterm.global.storage;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

/**
 * 허용하지 않는 파일(확장자, 크기, 이름)을 올렸을 때의 예외.
 */
public class InvalidFileException extends CustomException {

	public InvalidFileException(String message) {
		super(message, HttpStatus.BAD_REQUEST);
	}
}
