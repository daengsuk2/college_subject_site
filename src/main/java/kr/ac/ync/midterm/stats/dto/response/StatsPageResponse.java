package kr.ac.ync.midterm.stats.dto.response;

import java.util.List;

/**
 * 통계 화면 전체: 강좌 선택 목록과 선택한 강좌의 통계. 강좌가 하나도 없으면 stats는 null이다.
 */
public record StatsPageResponse(List<CourseOption> courses, CourseStatsResponse stats) {

	public record CourseOption(Long id, String name) {
	}
}
