package kr.ac.ync.midterm.course.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.user.domain.User;

public record EnrolledStudentResponse(Long studentId, String studentNo, String name, String email,
		LocalDateTime joinedAt) {

	public static EnrolledStudentResponse from(Enrollment enrollment) {
		User student = enrollment.getStudent();
		return new EnrolledStudentResponse(
				student.getId(),
				student.getStudentNo(),
				student.getName(),
				student.getEmail(),
				enrollment.getJoinedAt());
	}
}
