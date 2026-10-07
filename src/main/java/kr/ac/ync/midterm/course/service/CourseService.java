package kr.ac.ync.midterm.course.service;

import java.util.List;

import kr.ac.ync.midterm.course.dto.request.CourseCreateRequest;
import kr.ac.ync.midterm.course.dto.response.CourseDetailResponse;
import kr.ac.ync.midterm.course.dto.response.CourseResponse;

public interface CourseService {

	CourseResponse create(Long instructorId, CourseCreateRequest request);

	List<CourseResponse> findMyCourses(Long instructorId);

	CourseDetailResponse findMyCourseDetail(Long instructorId, Long courseId);
}
