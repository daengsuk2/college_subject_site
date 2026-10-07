package kr.ac.ync.midterm.assignment.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.assignment.domain.Assignment;

public record AssignmentResponse(Long id, Long courseId, String title, String content,
		LocalDateTime startAt, LocalDateTime endAt, int maxScore, LocalDateTime createdAt) {

	public static AssignmentResponse from(Assignment assignment) {
		return new AssignmentResponse(
				assignment.getId(),
				assignment.getCourse().getId(),
				assignment.getTitle(),
				assignment.getContent(),
				assignment.getStartAt(),
				assignment.getEndAt(),
				assignment.getMaxScore(),
				assignment.getCreatedAt());
	}
}
