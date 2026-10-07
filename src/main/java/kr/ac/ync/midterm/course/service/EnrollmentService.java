package kr.ac.ync.midterm.course.service;

import kr.ac.ync.midterm.course.dto.request.EnrollRequest;
import kr.ac.ync.midterm.course.dto.response.EnrollmentResponse;

public interface EnrollmentService {

	EnrollmentResponse enroll(Long studentId, EnrollRequest request);
}
