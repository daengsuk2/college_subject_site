package kr.ac.ync.midterm.submission.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;

/**
 * 학생에게 보여 주는 내 제출.
 */
public record SubmissionResponse(Long id, String content, LocalDateTime submittedAt, SubmissionStatus status) {

	public static SubmissionResponse of(Submission submission) {
		return new SubmissionResponse(submission.getId(), submission.getContent(), submission.getSubmittedAt(),
				submission.getStatus());
	}

	public boolean isGraded() {
		return status == SubmissionStatus.GRADED;
	}
}
