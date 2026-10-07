package kr.ac.ync.midterm.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssignmentStatusTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 9, 0);
	private static final LocalDateTime END = LocalDateTime.of(2026, 10, 17, 23, 59);

	@Test
	@DisplayName("of - 시작일시 전이면 예정")
	void of_beforeStart() {
		assertThat(AssignmentStatus.of(START, END, START.minusSeconds(1))).isEqualTo(AssignmentStatus.UPCOMING);
		assertThat(AssignmentStatus.of(START, END, START.minusDays(30))).isEqualTo(AssignmentStatus.UPCOMING);
	}

	@Test
	@DisplayName("of - 시작일시 정각은 진행중 (경계값)")
	void of_exactlyStart() {
		assertThat(AssignmentStatus.of(START, END, START)).isEqualTo(AssignmentStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("of - 시작과 종료 사이면 진행중")
	void of_between() {
		assertThat(AssignmentStatus.of(START, END, START.plusDays(3))).isEqualTo(AssignmentStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("of - 종료일시 정각은 진행중 (경계값)")
	void of_exactlyEnd() {
		assertThat(AssignmentStatus.of(START, END, END)).isEqualTo(AssignmentStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("of - 종료일시가 지나면 마감 (1초 뒤)")
	void of_afterEnd() {
		assertThat(AssignmentStatus.of(START, END, END.plusSeconds(1))).isEqualTo(AssignmentStatus.CLOSED);
		assertThat(AssignmentStatus.of(START, END, END.plusDays(30))).isEqualTo(AssignmentStatus.CLOSED);
	}

	@Test
	@DisplayName("상태 표시 - 이름, 배지 색, 정렬 순서가 정해져 있음")
	void display() {
		assertThat(AssignmentStatus.IN_PROGRESS.getLabel()).isEqualTo("진행중");
		assertThat(AssignmentStatus.UPCOMING.getLabel()).isEqualTo("예정");
		assertThat(AssignmentStatus.CLOSED.getLabel()).isEqualTo("마감");
		assertThat(AssignmentStatus.IN_PROGRESS.getBadgeClass()).isEqualTo("badge-success");
		assertThat(AssignmentStatus.UPCOMING.getBadgeClass()).isEqualTo("badge-secondary");
		assertThat(AssignmentStatus.CLOSED.getBadgeClass()).isEqualTo("badge-danger");
		assertThat(AssignmentStatus.IN_PROGRESS.getSortOrder())
				.isLessThan(AssignmentStatus.UPCOMING.getSortOrder());
		assertThat(AssignmentStatus.UPCOMING.getSortOrder())
				.isLessThan(AssignmentStatus.CLOSED.getSortOrder());
	}
}
