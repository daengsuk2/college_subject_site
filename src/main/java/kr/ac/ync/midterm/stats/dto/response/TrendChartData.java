package kr.ac.ync.midterm.stats.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.AssignmentStatRow;

/**
 * 과제별 추이 선 차트의 데이터 (X31). 같은 순서의 목록들이며, 시작된 과제를 시작일시 순서대로 담는다.
 * 시간 간격이 아니라 과제 순서에 따른 변화이다. 비율 계산은 여기서 하고 화면(Chart.js)은 그리기만 한다.
 * - submissionRates: 제출률(%) = 제출 수 / 수강생 수 (수강생이 없으면 null)
 * - scoreRates: 평균 점수 비율(%) = 평균 점수 / 배점 x 100 (채점된 제출이 없으면 null이라 선이 끊긴다. 0으로 그리지 않는다)
 * 값은 소수 첫째 자리로 반올림한다. 툴팁에 쓸 제출 수 · 수강생 수 · 평균 점수 · 배점도 함께 담는다.
 */
public record TrendChartData(List<String> labels, List<Double> submissionRates, List<Double> scoreRates,
		List<Long> submittedCounts, List<Long> enrolledCounts, List<Double> averageScores, List<Integer> maxScores) {

	public static TrendChartData from(CourseStatsResponse stats) {
		List<String> labels = new ArrayList<>();
		List<Double> submissionRates = new ArrayList<>();
		List<Double> scoreRates = new ArrayList<>();
		List<Long> submittedCounts = new ArrayList<>();
		List<Long> enrolledCounts = new ArrayList<>();
		List<Double> averageScores = new ArrayList<>();
		List<Integer> maxScores = new ArrayList<>();

		for (AssignmentStatRow row : stats.assignments()) {
			labels.add(row.title());
			submissionRates.add(round(row.submissionRate()));
			scoreRates.add(scoreRate(row));
			submittedCounts.add(row.submittedCount());
			enrolledCounts.add(row.enrolledCount());
			averageScores.add(round(row.averageScore()));
			maxScores.add(row.maxScore());
		}
		return new TrendChartData(labels, submissionRates, scoreRates, submittedCounts, enrolledCounts, averageScores,
				maxScores);
	}

	private static Double scoreRate(AssignmentStatRow row) {
		if (row.averageScore() == null || row.maxScore() <= 0) {
			return null;
		}
		return round(row.averageScore() * 100.0 / row.maxScore());
	}

	private static Double round(Double value) {
		if (value == null) {
			return null;
		}
		return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}
}
