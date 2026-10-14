package kr.ac.ync.midterm.stats.dto.response;

/**
 * 수강생별 제출 건수 (집계 쿼리 결과). 시작된 과제 중 제출한 과제 수이다.
 */
public record StudentSubmissionCount(Long studentId, String studentNo, String studentName, Long submittedCount) {
}
