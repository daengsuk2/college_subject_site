package kr.ac.ync.midterm.stats.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.AssignmentStatRow;
import kr.ac.ync.midterm.stats.dto.response.TrendChartData;

/**
 * 과제별 추이 차트 데이터 (X31): 제출률 · 평균 점수 비율 계산, 순서, null 처리, 반올림.
 */
class TrendChartDataTest {

	private CourseStatsResponse stats(AssignmentStatRow... rows) {
		return new CourseStatsResponse(1L, "강좌", 2, rows.length, 0, 0, List.of(rows), List.of());
	}

	private AssignmentStatRow row(String title, int maxScore, long submitted, long enrolled, Double average) {
		return new AssignmentStatRow(1L, title, maxScore, submitted, enrolled, average == null ? 0 : submitted, average);
	}

	@Test
	@DisplayName("제출률 = 제출 수 / 수강생 수, 평균 점수 비율 = 평균 점수 / 배점 (배점이 달라도 같은 비율이면 같은 값)")
	void ratesWithDifferentMaxScores() {
		TrendChartData data = TrendChartData.from(stats(
				row("A", 100, 2, 2, 80.0),
				row("B", 50, 1, 2, 40.0)));

		assertThat(data.submissionRates()).containsExactly(100.0, 50.0);
		// 배점 100의 평균 80점도, 배점 50의 평균 40점도 80%
		assertThat(data.scoreRates()).containsExactly(80.0, 80.0);
	}

	@Test
	@DisplayName("과제가 나열된 순서를 그대로 유지하고 툴팁용 제출 수 · 수강생 수 · 평균 점수 · 배점도 같은 순서로 담김")
	void orderAndTooltipValues() {
		TrendChartData data = TrendChartData.from(stats(
				row("첫째", 100, 3, 5, 70.5),
				row("둘째", 20, 4, 5, 15.0),
				row("셋째", 10, 5, 5, 9.0)));

		assertThat(data.labels()).containsExactly("첫째", "둘째", "셋째");
		assertThat(data.submittedCounts()).containsExactly(3L, 4L, 5L);
		assertThat(data.enrolledCounts()).containsExactly(5L, 5L, 5L);
		assertThat(data.averageScores()).containsExactly(70.5, 15.0, 9.0);
		assertThat(data.maxScores()).containsExactly(100, 20, 10);
	}

	@Test
	@DisplayName("채점된 제출이 없어 평균이 없는 과제는 평균 점수 비율이 null (0이 아님)이고 제출률은 정상")
	void noAverageIsNullNotZero() {
		TrendChartData data = TrendChartData.from(stats(
				row("채점됨", 100, 2, 2, 90.0),
				row("채점 전", 100, 2, 2, null),
				row("0점 채점", 100, 1, 2, 0.0)));

		assertThat(data.scoreRates()).containsExactly(90.0, null, 0.0);
		assertThat(data.averageScores()).containsExactly(90.0, null, 0.0);
		assertThat(data.submissionRates()).containsExactly(100.0, 100.0, 50.0);
	}

	@Test
	@DisplayName("소수는 소수 첫째 자리로 반올림됨 (66.666 → 66.7, 85.55 → 85.6, 33.333 → 33.3)")
	void rounding() {
		TrendChartData data = TrendChartData.from(stats(
				row("A", 100, 2, 3, 85.55),
				row("B", 3, 1, 3, 1.0)));

		assertThat(data.submissionRates()).containsExactly(66.7, 33.3);
		assertThat(data.scoreRates()).containsExactly(85.6, 33.3);
	}

	@Test
	@DisplayName("수강생이 0명이면 제출률은 null이고(0으로 나누지 않음), 과제가 없으면 모든 목록이 비어 있음")
	void emptyCases() {
		TrendChartData noStudents = TrendChartData.from(stats(row("A", 100, 0, 0, null)));
		assertThat(noStudents.submissionRates()).containsExactly((Double) null);
		assertThat(noStudents.scoreRates()).containsExactly((Double) null);

		TrendChartData none = TrendChartData.from(stats());
		assertThat(none.labels()).isEmpty();
		assertThat(none.submissionRates()).isEmpty();
		assertThat(none.scoreRates()).isEmpty();
	}

	@Test
	@DisplayName("과제가 하나뿐이어도 값이 한 개씩 만들어짐")
	void singleAssignment() {
		TrendChartData data = TrendChartData.from(stats(row("하나", 100, 1, 4, 55.0)));

		assertThat(data.labels()).containsExactly("하나");
		assertThat(data.submissionRates()).containsExactly(25.0);
		assertThat(data.scoreRates()).containsExactly(55.0);
	}
}
