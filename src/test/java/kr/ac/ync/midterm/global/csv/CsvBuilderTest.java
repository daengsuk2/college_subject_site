package kr.ac.ync.midterm.global.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * CSV 만들기 (X8 · X32): BOM, 줄바꿈, 이스케이프, 엑셀 수식 주입 방지, 숫자 · 날짜 형식, 파일명 처리.
 */
public class CsvBuilderTest {

	/** 테스트용 간단한 CSV 읽기 (큰따옴표 · 줄바꿈 처리). 만든 CSV를 다시 읽어 값이 같은지 확인하는 데 쓴다. */
	public static List<List<String>> parse(byte[] bytes) {
		String text = new String(bytes, StandardCharsets.UTF_8);
		assertThat(text).startsWith("\uFEFF");
		text = text.substring(1);
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cell.append(c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
				row.add(cell.toString());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
				i++;
			} else {
				cell.append(c);
			}
		}
		return rows;
	}

	@Test
	@DisplayName("UTF-8 BOM으로 시작하고 줄바꿈은 CRLF이며 머리글이 첫 줄에 있음")
	void bomHeaderAndNewlines() {
		byte[] bytes = CsvBuilder.withHeader("학번", "이름").row("20260001", "홍길동").toBytes();

		assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
		assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
		assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
		assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("\uFEFF학번,이름\r\n20260001,홍길동\r\n");
	}

	@Test
	@DisplayName("머리글만 있는 CSV도 만들어짐 (데이터가 없는 경우)")
	void headerOnly() {
		assertThat(parse(CsvBuilder.withHeader("학번", "이름").toBytes())).containsExactly(List.of("학번", "이름"));
	}

	@Test
	@DisplayName("=, +, -, @, 탭, 캐리지리턴으로 시작하는 문자열은 앞에 작은따옴표가 붙어 수식으로 실행되지 않음")
	void formulaInjectionNeutralized() {
		String[] dangerous = { "=1+1", "+cmd", "-2", "@SUM(A1)", "\tTAB", "\rCR", "=HYPERLINK(\"http://evil\",\"x\")" };
		CsvBuilder csv = CsvBuilder.withHeader("값");
		for (String value : dangerous) {
			csv.row(value);
		}

		List<List<String>> rows = parse(csv.toBytes());

		for (int i = 0; i < dangerous.length; i++) {
			assertThat(rows.get(i + 1).getFirst()).as(dangerous[i]).isEqualTo("'" + dangerous[i]);
		}
	}

	@Test
	@DisplayName("일반 값 · 한글 · 숫자처럼 보이는 날짜 문자열 · 가운데에 =, + 가 있는 값은 바뀌지 않음")
	void normalValuesUnchanged() {
		CsvBuilder csv = CsvBuilder.withHeader("값");
		for (String value : new String[] { "홍길동", "2026-10-14 11:35", "a=b", "1+1", "x-y", "user@test.com", "20260001" }) {
			csv.row(value);
		}

		List<List<String>> rows = parse(csv.toBytes());

		assertThat(rows.stream().skip(1).map(r -> r.getFirst()))
				.containsExactly("홍길동", "2026-10-14 11:35", "a=b", "1+1", "x-y", "user@test.com", "20260001");
	}

	@Test
	@DisplayName("쉼표 · 큰따옴표 · 줄바꿈이 들어 있는 값은 감싸지고 이스케이프되어, 다시 읽으면 원래 값과 같음")
	void escapeRoundTrip() {
		String[] values = { "자바,스프링", "그는 \"안녕\"이라고 했다", "첫 줄\r\n둘째 줄", "줄바꿈\n만", "\"", ",", "끝에 따옴표\"" };
		CsvBuilder csv = CsvBuilder.withHeader("값");
		for (String value : values) {
			csv.row(value);
		}

		List<List<String>> rows = parse(csv.toBytes());

		assertThat(rows).hasSize(values.length + 1);
		for (int i = 0; i < values.length; i++) {
			assertThat(rows.get(i + 1)).as(values[i]).containsExactly(values[i]);
		}
	}

	@Test
	@DisplayName("숫자는 그대로, 소수는 소수 첫째 자리, 날짜는 yyyy-MM-dd HH:mm, null은 빈 칸으로 쓰이고 수식 방지가 적용되지 않음")
	void numbersDatesAndNulls() {
		byte[] bytes = CsvBuilder.withHeader("a", "b", "c", "d", "e", "f", "g")
				.row(100L, 7, 85.0, 66.6667, 0.05, LocalDateTime.of(2026, 10, 14, 11, 5), null)
				.row(-3, -1.5, 100.0 / 3, null, null, null, null)
				.toBytes();

		List<List<String>> rows = parse(bytes);

		assertThat(rows.get(1)).containsExactly("100", "7", "85.0", "66.7", "0.1", "2026-10-14 11:05", "");
		assertThat(rows.get(2)).containsExactly("-3", "-1.5", "33.3", "", "", "", "");
	}

	@Test
	@DisplayName("내려받기 응답 - text/csv UTF-8, 항상 attachment, 한글 파일명은 filename*=UTF-8''로 인코딩됨")
	void downloadHeaders() {
		ResponseEntity<byte[]> response = CsvResponses.download("수강생_자바스프링", CsvBuilder.withHeader("학번"));

		assertThat(response.getHeaders().getContentType().toString()).isEqualToIgnoringCase("text/csv;charset=UTF-8");
		String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
		assertThat(disposition).startsWith("attachment").contains("filename*=UTF-8''");
		assertThat(disposition).doesNotContain("수강생");
		ContentDisposition parsed = ContentDisposition.parse(disposition);
		assertThat(parsed.getFilename()).isEqualTo("수강생_자바스프링.csv");
		assertThat(response.getBody()[0] & 0xFF).isEqualTo(0xEF);
	}

	@Test
	@DisplayName("파일명에 쓸 수 없는 글자(/ \\ : * ? \" < > |, 줄바꿈)는 _ 로 바뀌고, 비거나 너무 길면 정리됨")
	void fileNameSanitized() {
		assertThat(CsvResponses.safeFileName("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j");
		assertThat(CsvResponses.safeFileName("줄\r\n바꿈\t탭")).isEqualTo("줄__바꿈_탭");
		assertThat(CsvResponses.safeFileName("../../etc/passwd")).isEqualTo(".._.._etc_passwd");
		assertThat(CsvResponses.safeFileName("   ")).isEqualTo("export");
		assertThat(CsvResponses.safeFileName(null)).isEqualTo("export");
		assertThat(CsvResponses.safeFileName("...")).isEqualTo("export");
		assertThat(CsvResponses.safeFileName("가".repeat(300))).hasSize(100);
	}

	@Test
	@DisplayName("위험한 파일명도 응답 헤더에는 경로 구분자 없이 안전하게 들어감")
	void dangerousFileNameInHeader() {
		ResponseEntity<byte[]> response = CsvResponses.download("../../evil\r\nSet-Cookie: x=1", CsvBuilder.withHeader("a"));

		String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
		assertThat(disposition).doesNotContain("\r").doesNotContain("\n");
		String name = ContentDisposition.parse(disposition).getFilename();
		assertThat(name).doesNotContain("/").doesNotContain("\\").doesNotContain("\r").doesNotContain("\n");
	}
}
