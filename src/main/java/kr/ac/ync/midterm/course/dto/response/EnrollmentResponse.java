package kr.ac.ync.midterm.course.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.course.domain.Enrollment;

public record EnrollmentResponse(Long id, Long courseId, String courseName, LocalDateTime joinedAt) {

	public static EnrollmentResponse from(Enrollment enrollment) {
		return new EnrollmentResponse(
				enrollment.getId(),
				enrollment.getCourse().getId(),
				enrollment.getCourse().getName(),
				enrollment.getJoinedAt());
	}
}
