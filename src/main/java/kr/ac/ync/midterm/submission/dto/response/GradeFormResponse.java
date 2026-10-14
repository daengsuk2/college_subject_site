package kr.ac.ync.midterm.submission.dto.response;

import java.time.LocalDateTime;

import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 채점 화면에 보여 줄 제출 정보 (학생, 과제, 제출 내용, 기존 채점 결과).
 */
public record GradeFormResponse(Long submissionId, Long assignmentId, String courseName, String assignmentTitle,
		int maxScore, String studentNo, String studentName, String content, LocalDateTime submittedAt,
		String originalFilename, SubmissionStatus status, Integer score, String feedback,
		String objectionContent, LocalDateTime objectionAt, String objectionResponse,
		LocalDateTime objectionRespondedAt) {

	public static GradeFormResponse of(Submission submission) {
		User student = submission.getStudent();
		return new GradeFormResponse(submission.getId(), submission.getAssignment().getId(),
				submission.getAssignment().getCourse().getName(), submission.getAssignment().getTitle(),
				submission.getAssignment().getMaxScore(), student.getStudentNo(), student.getName(),
				submission.getContent(), submission.getSubmittedAt(),
				submission.hasFile() ? submission.getOriginalFilename() : null,
				submission.getStatus(), submission.getScore(), submission.getFeedback(),
				submission.getObjectionContent(), submission.getObjectionAt(), submission.getObjectionResponse(),
				submission.getObjectionRespondedAt());
	}

	public boolean hasObjection() {
		return objectionContent != null;
	}

	public boolean hasObjectionResponse() {
		return objectionResponse != null;
	}

	public boolean isGraded() {
		return status == SubmissionStatus.GRADED;
	}

	public boolean hasFile() {
		return originalFilename != null;
	}
}
