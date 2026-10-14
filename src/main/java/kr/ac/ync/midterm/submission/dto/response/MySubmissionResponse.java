package kr.ac.ync.midterm.submission.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;

/**
 * 학생의 "내 제출" 목록 한 줄 (S4): 과제, 강좌, 제출 시각, 상태, 점수 / 배점.
 */
public record MySubmissionResponse(Long assignmentId, String courseName, String assignmentTitle, int maxScore,
		LocalDateTime submittedAt, SubmissionStatus status, Integer score) {

	public static MySubmissionResponse of(Submission submission) {
		return new MySubmissionResponse(submission.getAssignment().getId(),
				submission.getAssignment().getCourse().getName(), submission.getAssignment().getTitle(),
				submission.getAssignment().getMaxScore(), submission.getSubmittedAt(), submission.getStatus(),
				submission.getScore());
	}

	public boolean isGraded() {
		return status == SubmissionStatus.GRADED;
	}
}
