package kr.ac.ync.midterm.course.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import kr.ac.ync.midterm.course.domain.Course;

/**
 * 강좌 상세 (강좌 정보 + 수강생 목록).
 */
public record CourseDetailResponse(Long id, String name, String joinCode, LocalDateTime createdAt,
		List<EnrolledStudentResponse> students) {

	public static CourseDetailResponse of(Course course, List<EnrolledStudentResponse> students) {
		return new CourseDetailResponse(
				course.getId(),
				course.getName(),
				course.getJoinCode(),
				course.getCreatedAt(),
				students);
	}
}
