package kr.ac.ync.midterm.stats.dto.response;

import java.util.List;

/**
 * 강사의 강좌별 통계 (I6): 요약, 과제별 행, 미제출자 행.
 * 제출률 = 제출 건수 / (수강생 수 x 시작된 과제 수). 예정 과제는 제출할 수 없으므로 분모에 넣지 않는다.
 */
public record CourseStatsResponse(Long courseId, String courseName, long enrolledCount, int startedAssignmentCount,
		long submittedCount, long possibleCount, List<AssignmentStatRow> assignments,
		List<MissingStudentRow> missingStudents) {

	/** 전체 제출률(%). 분모가 0이면 null */
	public Double submissionRate() {
		return possibleCount == 0 ? null : submittedCount * 100.0 / possibleCount;
	}

	public long notSubmittedCount() {
		return possibleCount - submittedCount;
	}

	/** 과제별 행. 제출률은 수강생이 없으면 null, 평균 점수는 채점된 제출이 없으면 null이다. */
	public record AssignmentStatRow(Long assignmentId, String title, int maxScore, long submittedCount,
			long enrolledCount, long gradedCount, Double averageScore) {

		public Double submissionRate() {
			return enrolledCount == 0 ? null : submittedCount * 100.0 / enrolledCount;
		}

		public long pendingCount() {
			return submittedCount - gradedCount;
		}
	}

	/** 미제출자 행: 시작된 과제 중 제출하지 않은 과제 수 */
	public record MissingStudentRow(String studentNo, String studentName, long missingCount) {
	}
}
