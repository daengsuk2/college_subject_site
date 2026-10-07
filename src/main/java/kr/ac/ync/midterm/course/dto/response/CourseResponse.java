package kr.ac.ync.midterm.course.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.course.domain.Course;

public record CourseResponse(Long id, String name, String joinCode, LocalDateTime createdAt) {

	public static CourseResponse from(Course course) {
		return new CourseResponse(
				course.getId(),
				course.getName(),
				course.getJoinCode(),
				course.getCreatedAt());
	}
}
