package kr.ac.ync.midterm.stats.service;

import kr.ac.ync.midterm.stats.dto.response.StatsPageResponse;

public interface StatsService {

	/**
	 * 강사의 통계 화면. courseId가 없으면 가장 최근 개설한 강좌를 보여 준다.
	 * 없는 강좌는 404, 다른 강사의 강좌는 403 (B5).
	 */
	StatsPageResponse findStats(Long instructorId, Long courseId);
}
