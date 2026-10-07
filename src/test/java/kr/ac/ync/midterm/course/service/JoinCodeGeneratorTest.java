package kr.ac.ync.midterm.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JoinCodeGeneratorTest {

	private final JoinCodeGenerator generator = new JoinCodeGenerator();

	@Test
	@DisplayName("generate - 6자리, 대문자와 숫자만 사용")
	void generate_format() {
		for (int i = 0; i < 1000; i++) {
			assertThat(generator.generate()).matches("[A-HJ-NP-Z2-9]{6}");
		}
	}

	@Test
	@DisplayName("generate - 헷갈리는 문자(I, O, 0, 1)는 사용하지 않음")
	void generate_excludesConfusingCharacters() {
		for (int i = 0; i < 1000; i++) {
			assertThat(generator.generate()).doesNotContain("I", "O", "0", "1");
		}
	}

	@Test
	@DisplayName("generate - 매번 같은 값이 나오지 않음")
	void generate_varies() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			codes.add(generator.generate());
		}
		assertThat(codes.size()).isGreaterThan(190);
	}
}
