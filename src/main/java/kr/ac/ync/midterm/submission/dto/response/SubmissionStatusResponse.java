package kr.ac.ync.midterm.submission.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 강사에게 보여 주는 과제별 제출 현황 (I4): 요약, 제출자 목록, 미제출자 목록.
 */
public record SubmissionStatusResponse(Long assignmentId, Long courseId, String courseName, String assignmentTitle,
		int maxScore, long enrolledCount, List<SubmittedRow> submitted, List<NotSubmittedRow> notSubmitted) {

	public int submittedCount() {
		return submitted.size();
	}

	public int notSubmittedCount() {
		return notSubmitted.size();
	}

	/** 제출자 한 줄. 점수는 채점 전이면 null이다. */
	public record SubmittedRow(Long submissionId, String studentNo, String studentName, LocalDateTime submittedAt,
			SubmissionStatus status, Integer score, String originalFilename, boolean hasObjection,
			boolean objectionAnswered) {

		public static SubmittedRow of(Submission submission) {
			User student = submission.getStudent();
			return new SubmittedRow(submission.getId(), student.getStudentNo(), student.getName(),
					submission.getSubmittedAt(), submission.getStatus(), submission.getScore(),
					submission.hasFile() ? submission.getOriginalFilename() : null, submission.hasObjection(),
					submission.hasObjectionResponse());
		}

		public boolean isGraded() {
			return status == SubmissionStatus.GRADED;
		}

		/** 이의가 접수되었고 아직 답변하지 않음 (X29) */
		public boolean isObjectionPending() {
			return hasObjection && !objectionAnswered;
		}

		public boolean hasFile() {
			return originalFilename != null;
		}
	}

	/** 미제출자 한 줄 */
	public record NotSubmittedRow(String studentNo, String studentName) {

		public static NotSubmittedRow of(User student) {
			return new NotSubmittedRow(student.getStudentNo(), student.getName());
		}
	}
}
