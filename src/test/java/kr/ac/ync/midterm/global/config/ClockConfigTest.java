package kr.ac.ync.midterm.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.ac.ync.midterm.MidtermApplication;

/**
 * 서버 시간대와 상관없이 한국 시간 기준으로 동작하는지 확인한다. (서버가 UTC일 때 9시간 어긋나던 문제)
 */
class ClockConfigTest {

	private TimeZone original;

	@BeforeEach
	void remember() {
		original = TimeZone.getDefault();
	}

	@AfterEach
	void restore() {
		TimeZone.setDefault(original);
	}

	@Test
	@DisplayName("Clock은 서버 시간대와 상관없이 Asia/Seoul이다")
	void clockIsSeoul() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		Clock clock = new ClockConfig().clock();

		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

	@Test
	@DisplayName("서버가 UTC여도 Clock 기준 현재 시각은 UTC보다 9시간 앞선 한국 시각이다")
	void nineHoursAheadOfUtc() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Clock fixedInSeoul = Clock.fixed(Instant.parse("2026-10-14T05:43:00Z"), ClockConfig.ZONE);

		LocalDateTime now = LocalDateTime.now(fixedInSeoul);

		assertThat(now).isEqualTo(LocalDateTime.of(2026, 10, 14, 14, 43));
		assertThat(Duration.between(LocalDateTime.now(Clock.fixed(fixedInSeoul.instant(), ZoneOffset.UTC)), now))
				.isEqualTo(Duration.ofHours(9));
	}

	@Test
	@DisplayName("앱을 시작하면 JVM 기본 시간대가 Asia/Seoul로 바뀌어 LocalDateTime.now()도 한국 시각이 된다")
	void startupSetsDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		MidtermApplication.useSeoulTimeZone();

		assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
		assertThat(LocalDateTime.now().atZone(ZoneId.systemDefault()).getOffset()).isEqualTo(ZoneOffset.ofHours(9));
	}
}
