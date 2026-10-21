package kr.ac.ync.midterm.submission.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.submission.domain.Submission;

/**
 * 강사 상단바 알림에 보여 줄 이의 신청 한 줄 (X29): 어느 강좌 · 과제의 누가 언제 신청했는지.
 */
public record ObjectionAlertResponse(Long submissionId, String courseName, String assignmentTitle,
		String studentName, LocalDateTime objectionAt) {

	public static ObjectionAlertResponse of(Submission submission) {
		return new ObjectionAlertResponse(submission.getId(),
				submission.getAssignment().getCourse().getName(), submission.getAssignment().getTitle(),
				submission.getStudent().getName(), submission.getObjectionAt());
	}
}
