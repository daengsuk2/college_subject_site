package kr.ac.ync.midterm.global.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 프로젝트 루트의 .env 파일을 UTF-8로 읽어 설정 값으로 등록한다.
 *
 * spring.config.import 로 .env[.properties]를 읽으면 ISO-8859-1로 해석되어 한글 값이 깨지므로
 * (이슈 #6) 직접 UTF-8로 읽는다. 환경변수와 application.yml 보다 우선순위가 낮아서,
 * 같은 이름의 환경변수가 있으면 환경변수가 이긴다. .env가 없으면 아무 일도 하지 않는다.
 *
 * 등록: META-INF/spring.factories
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final String PROPERTY_SOURCE_NAME = "dotenv";
	private static final Path DEFAULT_FILE = Path.of(".env");

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		apply(environment, DEFAULT_FILE);
	}

	static void apply(ConfigurableEnvironment environment, Path file) {
		if (!Files.isRegularFile(file)) {
			return;
		}
		Map<String, Object> values = new LinkedHashMap<>(read(file));
		if (!values.isEmpty()) {
			environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
		}
	}

	static Map<String, String> read(Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(".env 파일을 읽을 수 없습니다: " + file, e);
		}

		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (i == 0 && line.startsWith("﻿")) {
				line = line.substring(1); // UTF-8 BOM 제거
			}
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = line.substring(0, eq).strip();
			String value = unquote(line.substring(eq + 1).strip());
			if (!key.isEmpty()) {
				values.put(key, value);
			}
		}
		return values;
	}

	private static String unquote(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '"' || first == '\'') && first == last) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}
}
