package kr.ac.ync.midterm.assignment.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;

/**
 * 학생에게 보여 주는 과제 (강좌명, 현재 상태, 첨부파일 이름 포함).
 */
public record StudentAssignmentResponse(Long id, Long courseId, String courseName, String title, String content,
		LocalDateTime startAt, LocalDateTime endAt, int maxScore, AssignmentStatus status,
		String originalFilename) {

	public static StudentAssignmentResponse of(Assignment assignment, AssignmentStatus status) {
		return new StudentAssignmentResponse(
				assignment.getId(),
				assignment.getCourse().getId(),
				assignment.getCourse().getName(),
				assignment.getTitle(),
				assignment.getContent(),
				assignment.getStartAt(),
				assignment.getEndAt(),
				assignment.getMaxScore(),
				status,
				assignment.hasFile() ? assignment.getOriginalFilename() : null);
	}

	public boolean hasFile() {
		return originalFilename != null;
	}
}
