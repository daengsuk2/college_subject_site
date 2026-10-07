package kr.ac.ync.midterm.course.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class CourseNotFoundException extends CustomException {

	public CourseNotFoundException() {
		super("존재하지 않는 강좌입니다.", HttpStatus.NOT_FOUND);
	}
}
