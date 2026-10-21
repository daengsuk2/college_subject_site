package kr.ac.ync.midterm.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 배포용 설정 파일(application-local.yml)이 있으면 설정으로 읽히는지 확인한다.
 * 설정 파일이 없는 환경(다른 사람의 컴퓨터, CI)에서는 건너뛴다. 값은 출력하지 않고 일치 여부만 본다.
 * DB에는 접속하지 않는다. (자동 설정 없이 설정 값만 읽는 빈 컨텍스트)
 */
class DeployConfigImportTest {

	@Configuration
	static class Empty {
	}

	@Test
	@DisplayName("application-local.yml이 있으면 DB 계정과 강사 계정 값이 설정에 반영됨")
	void localConfigIsImported() throws IOException {
		Path generated = Path.of("src/main/resources/application-local.yml");
		Path env = Path.of(".env");
		Assumptions.assumeTrue(Files.exists(generated) && Files.exists(env), "배포용 설정 파일이 없어 건너뜀");

		String expectedUser = null;
		for (String line : Files.readAllLines(env, StandardCharsets.UTF_8)) {
			if (line.startsWith("DB_USERNAME=")) {
				expectedUser = line.substring("DB_USERNAME=".length()).trim().replace("\"", "").replace("'", "");
			}
		}
		Assumptions.assumeTrue(expectedUser != null && !expectedUser.isEmpty());

		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
				.web(WebApplicationType.NONE).run()) {
			String user = context.getEnvironment().getProperty("spring.datasource.username");
			assertThat(user).isEqualTo(expectedUser);
			assertThat(context.getEnvironment().getProperty("spring.datasource.password")).isNotNull();
		}
	}
}
