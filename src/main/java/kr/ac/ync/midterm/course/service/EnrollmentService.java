package kr.ac.ync.midterm.course.service;

import java.util.List;

import kr.ac.ync.midterm.course.dto.request.EnrollRequest;
import kr.ac.ync.midterm.course.dto.response.MyCourseResponse;
import kr.ac.ync.midterm.course.dto.response.EnrollmentResponse;

public interface EnrollmentService {

	EnrollmentResponse enroll(Long studentId, EnrollRequest request);

	/** 내 수강 강좌 (X3). 본인이 수강 등록한 강좌만, 최근 등록순으로 돌려준다. */
	List<MyCourseResponse> findMyCourses(Long studentId);
}
