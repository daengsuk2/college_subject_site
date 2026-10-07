package kr.ac.ync.midterm.assignment.domain;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * 과제 상태. DB에 저장하지 않고 현재 시각과 시작 · 종료일시로 계산한다.
 * 시작일시 ≤ 현재 ≤ 종료일시는 진행중이다. (제출 가능 기간 규칙 B1과 같은 기준)
 */
@Getter
public enum AssignmentStatus {

	IN_PROGRESS("진행중", "badge-success", 0),
	UPCOMING("예정", "badge-secondary", 1),
	CLOSED("마감", "badge-danger", 2);

	private final String label;
	private final String badgeClass;
	private final int sortOrder;

	AssignmentStatus(String label, String badgeClass, int sortOrder) {
		this.label = label;
		this.badgeClass = badgeClass;
		this.sortOrder = sortOrder;
	}

	public static AssignmentStatus of(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
		if (now.isBefore(startAt)) {
			return UPCOMING;
		}
		if (now.isAfter(endAt)) {
			return CLOSED;
		}
		return IN_PROGRESS;
	}
}
