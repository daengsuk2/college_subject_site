package kr.ac.ync.midterm.global.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 만들기 (X8 · X32). 라이브러리 없이 필요한 규칙만 직접 구현한다.
 * - UTF-8 + BOM: 엑셀이 한글을 깨뜨리지 않고 연다.
 * - 줄바꿈은 CRLF, 구분자는 쉼표. 쉼표 · 큰따옴표 · 줄바꿈이 있는 값은 큰따옴표로 감싸고 큰따옴표는 두 번 쓴다.
 * - 엑셀 수식 주입 방지: 문자열 값이 =, +, -, @, 탭, 캐리지리턴으로 시작하면 앞에 작은따옴표를 붙여 문자로 취급되게 한다.
 *   숫자 · 날짜 값은 우리가 직접 만든 형식이라 그대로 쓴다.
 */
public final class CsvBuilder {

	private static final String BOM = "﻿";
	private static final String NEWLINE = "\r\n";
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final StringBuilder out = new StringBuilder(BOM);

	private CsvBuilder() {
	}

	/** 머리글(첫 줄)로 시작하는 CSV를 만든다. */
	public static CsvBuilder withHeader(String... columns) {
		CsvBuilder csv = new CsvBuilder();
		List<String> cells = new ArrayList<>(columns.length);
		for (String column : columns) {
			cells.add(escape(column)); // 머리글은 코드에 적어 둔 고정 글자라 수식 방지는 필요 없다
		}
		csv.append(cells);
		return csv;
	}

	/**
	 * 한 줄을 추가한다. 값의 종류: 문자열(수식 주입 방지 적용), 정수(그대로), 소수(소수 첫째 자리), 날짜 · 시각(yyyy-MM-dd HH:mm), null(빈 칸).
	 */
	public CsvBuilder row(Object... values) {
		List<String> cells = new ArrayList<>(values.length);
		for (Object value : values) {
			cells.add(format(value));
		}
		append(cells);
		return this;
	}

	public byte[] toBytes() {
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	// ---------- 값 하나를 CSV 칸으로 ----------

	static String format(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Double || value instanceof Float) {
			return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(1, RoundingMode.HALF_UP).toPlainString();
		}
		if (value instanceof Number) {
			return value.toString();
		}
		if (value instanceof LocalDateTime time) {
			return time.format(DATE_TIME);
		}
		return text(value.toString());
	}

	/** 문자열 칸: 수식으로 읽힐 수 있는 시작 글자는 막고, CSV 규칙대로 감싼다. */
	static String text(String value) {
		return escape(neutralizeFormula(value));
	}

	static String neutralizeFormula(String value) {
		if (value.isEmpty()) {
			return value;
		}
		char first = value.charAt(0);
		if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
			return "'" + value;
		}
		return value;
	}

	static String escape(String value) {
		boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
				|| value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
		if (!needsQuotes) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private void append(List<String> cells) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(cells.get(i));
		}
		out.append(NEWLINE);
	}
}
