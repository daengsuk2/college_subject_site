package kr.ac.ync.midterm.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각을 Clock으로 주입받아 쓰면 테스트에서 시각을 고정해 경계값을 확인할 수 있다.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
