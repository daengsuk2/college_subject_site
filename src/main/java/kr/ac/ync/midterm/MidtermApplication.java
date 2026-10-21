package kr.ac.ync.midterm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import kr.ac.ync.midterm.global.config.ClockConfig;

@SpringBootApplication
public class MidtermApplication {

	public static void main(String[] args) {
		useSeoulTimeZone();
		SpringApplication.run(MidtermApplication.class, args);
	}

	/** 서버의 시간대가 UTC 등이어도 엔티티의 생성 시각(LocalDateTime.now())과 로그가 한국 시간으로 기록되게 한다. */
	public static void useSeoulTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.ZONE));
	}
}
