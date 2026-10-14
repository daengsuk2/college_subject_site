package kr.ac.ync.midterm.stats.dto.response;

/**
 * 과제별 제출 집계 (group by 쿼리 결과). 평균 점수는 채점 완료된 제출만으로 계산하며 없으면 null이다.
 */
public record AssignmentSubmissionAggregate(Long assignmentId, Long submittedCount, Long gradedCount,
		Double averageScore) {
}
