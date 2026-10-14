package kr.ac.ync.midterm.dashboard.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.domain.SubmissionStatus;

/**
 * 강사 대시보드: 내 강좌 수, 진행중 과제 수, 채점 대기 수, 최근 제출. 본인 강좌의 데이터만 담는다 (B5).
 */
public record InstructorDashboardResponse(long courseCount, long inProgressAssignmentCount, long pendingGradingCount,
		List<RecentSubmissionRow> recentSubmissions) {

	/** 최근 제출 한 줄 */
	public record RecentSubmissionRow(Long submissionId, String studentNo, String studentName,
			String assignmentTitle, String courseName, LocalDateTime submittedAt, SubmissionStatus status) {

		public static RecentSubmissionRow of(Submission submission) {
			return new RecentSubmissionRow(submission.getId(), submission.getStudent().getStudentNo(),
					submission.getStudent().getName(), submission.getAssignment().getTitle(),
					submission.getAssignment().getCourse().getName(), submission.getSubmittedAt(),
					submission.getStatus());
		}

		public boolean isGraded() {
			return status == SubmissionStatus.GRADED;
		}
	}
}
