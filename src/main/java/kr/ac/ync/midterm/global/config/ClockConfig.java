package kr.ac.ync.midterm.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각을 Clock으로 주입받아 쓰면 테스트에서 시각을 고정해 경계값을 확인할 수 있다.
 * 서버(배포 환경)의 시간대와 상관없이 항상 한국 시간(Asia/Seoul)을 기준으로 한다.
 * 강사가 입력하는 과제 시작 · 종료일시가 한국 시간이므로, 서버가 UTC여도 제출 기간 판정(B1)이 어긋나지 않게 한다.
 */
@Configuration
public class ClockConfig {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(ZONE);
	}
}
