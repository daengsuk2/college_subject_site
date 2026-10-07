package kr.ac.ync.midterm.assignment.exception;

import org.springframework.http.HttpStatus;

import kr.ac.ync.midterm.global.exception.CustomException;

public class AssignmentNotFoundException extends CustomException {

	public AssignmentNotFoundException() {
		super("존재하지 않는 과제입니다.", HttpStatus.NOT_FOUND);
	}
}
