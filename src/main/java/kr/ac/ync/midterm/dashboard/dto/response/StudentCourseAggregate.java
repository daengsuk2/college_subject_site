package kr.ac.ync.midterm.dashboard.dto.response;

/**
 * 학생의 강좌별 제출 집계 (집계 쿼리 결과, X35). 점수 합과 배점 합은 채점 완료된 제출만 더한 값이며, 없으면 null이다.
 */
public record StudentCourseAggregate(Long courseId, Long submittedCount, Long gradedCount, Long scoreSum,
		Long maxScoreSum) {
}
