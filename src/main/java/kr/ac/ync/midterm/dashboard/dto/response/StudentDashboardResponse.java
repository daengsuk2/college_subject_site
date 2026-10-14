package kr.ac.ync.midterm.dashboard.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 학생 대시보드 (S5): 수강 강좌 수, 제출률, 평균 점수, 마감 임박 과제.
 * 제출률 = 내가 제출한 과제 수 / 수강 강좌의 시작된 과제 수 (예정 과제는 제출할 수 없으므로 제외).
 */
public record StudentDashboardResponse(long courseCount, long startedAssignmentCount, long submittedCount,
		long gradedCount, Double averageScore, Double averageScoreRate, List<DueSoonRow> dueSoon) {

	/** 제출률(%). 시작된 과제가 없으면 null */
	public Double submissionRate() {
		return startedAssignmentCount == 0 ? null : submittedCount * 100.0 / startedAssignmentCount;
	}

	public int dueSoonCount() {
		return dueSoon.size();
	}

	/** 마감 임박 과제 한 줄 (아직 제출하지 않은 과제) */
	public record DueSoonRow(Long assignmentId, String title, String courseName, LocalDateTime endAt,
			long remainingMinutes) {

		/** 남은 시간을 "N시간 M분" 형태로 보여 준다 */
		public String remainingText() {
			long hours = remainingMinutes / 60;
			long minutes = remainingMinutes % 60;
			if (hours == 0) {
				return minutes + "분";
			}
			return minutes == 0 ? hours + "시간" : hours + "시간 " + minutes + "분";
		}
	}
}
