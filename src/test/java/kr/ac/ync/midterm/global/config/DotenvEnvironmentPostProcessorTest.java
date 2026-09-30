package kr.ac.ync.midterm.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DotenvEnvironmentPostProcessorTest {

	@TempDir
	Path tempDir;

	private Path envFile(String content) throws Exception {
		Path file = tempDir.resolve(".env");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	@Test
	@DisplayName("read - UTF-8 한글 값이 깨지지 않고 그대로 읽힘")
	void read_koreanValue() throws Exception {
		// given
		Path file = envFile("INSTRUCTOR_NAME=강사\nINSTRUCTOR_EMAIL=teacher@test.com\n");
		// when
		Map<String, String> values = DotenvEnvironmentPostProcessor.read(file);
		// then
		assertThat(values.get("INSTRUCTOR_NAME")).isEqualTo("강사");
		assertThat(values.get("INSTRUCTOR_EMAIL")).isEqualTo("teacher@test.com");
	}

	@Test
	@DisplayName("read - 주석, 빈 줄, '=' 없는 줄은 무시하고 값 안의 '='는 유지")
	void read_ignoresCommentsAndBlank() throws Exception {
		// given
		Path file = envFile("""
				# 주석

				DB_USERNAME=postgres
				이상한줄
				DB_PASSWORD=pass=word
				""");
		// when
		Map<String, String> values = DotenvEnvironmentPostProcessor.read(file);
		// then
		assertThat(values).containsOnlyKeys("DB_USERNAME", "DB_PASSWORD");
		assertThat(values.get("DB_PASSWORD")).isEqualTo("pass=word");
	}

	@Test
	@DisplayName("read - 값 양쪽 공백과 감싸는 따옴표를 제거하고 빈 값은 빈 문자열")
	void read_trimAndQuotes() throws Exception {
		// given
		Path file = envFile("A = \"이 복동\" \nB='x y'\nC=\n");
		// when
		Map<String, String> values = DotenvEnvironmentPostProcessor.read(file);
		// then
		assertThat(values.get("A")).isEqualTo("이 복동");
		assertThat(values.get("B")).isEqualTo("x y");
		assertThat(values.get("C")).isEmpty();
	}

	@Test
	@DisplayName("read - UTF-8 BOM이 있어도 첫 번째 키가 정상으로 읽힘")
	void read_bom() throws Exception {
		// given
		Path file = envFile("﻿FIRST=1\nSECOND=2\n");
		// when
		Map<String, String> values = DotenvEnvironmentPostProcessor.read(file);
		// then
		assertThat(values).containsKey("FIRST").doesNotContainKey("﻿FIRST");
	}

	@Test
	@DisplayName("apply - 환경 값이 .env보다 우선하고, 없는 키는 .env에서 채워짐")
	void apply_environmentWins() throws Exception {
		// given
		Path file = envFile("DB_PASSWORD=fromDotenv\nINSTRUCTOR_NAME=강사\n");
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("DB_PASSWORD", "fromEnv")));
		// when
		DotenvEnvironmentPostProcessor.apply(environment, file);
		// then
		assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("fromEnv");
		assertThat(environment.getProperty("INSTRUCTOR_NAME")).isEqualTo("강사");
	}

	@Test
	@DisplayName("apply - .env 파일이 없으면 아무 일도 하지 않음")
	void apply_noFile() {
		// given
		StandardEnvironment environment = new StandardEnvironment();
		// when
		DotenvEnvironmentPostProcessor.apply(environment, tempDir.resolve("없는파일.env"));
		// then
		assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
				.isFalse();
	}
}
