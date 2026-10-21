package kr.ac.ync.midterm.submission.dto.response;

import java.util.List;

/**
 * 답변을 기다리는 이의 신청 알림. count는 전체 수이고 items는 최근 몇 건만 담는다.
 */
public record ObjectionAlerts(long count, List<ObjectionAlertResponse> items) {

	public boolean hasMore() {
		return count > items.size();
	}
}
