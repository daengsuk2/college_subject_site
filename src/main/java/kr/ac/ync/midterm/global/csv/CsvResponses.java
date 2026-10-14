package kr.ac.ync.midterm.global.csv;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * CSV 내려받기 응답 (X8 · X32): text/csv;charset=UTF-8, 항상 내려받기(attachment),
 * 한글 파일명은 RFC 5987(filename*=UTF-8'')로 인코딩하고 파일명에 쓸 수 없는 글자는 _ 로 바꾼다.
 */
public final class CsvResponses {

	private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);
	private static final int MAX_NAME_LENGTH = 100;

	private CsvResponses() {
	}

	public static ResponseEntity<byte[]> download(String fileName, CsvBuilder csv) {
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(safeFileName(fileName) + ".csv", StandardCharsets.UTF_8)
				.build();

		return ResponseEntity.ok()
				.contentType(TEXT_CSV_UTF8)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(csv.toBytes());
	}

	/** 경로 구분자 · 윈도우에서 쓸 수 없는 글자 · 제어 문자를 _ 로 바꾸고, 앞뒤 공백을 없애고, 너무 길면 자른다. */
	static String safeFileName(String name) {
		String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
		if (cleaned.isEmpty() || cleaned.chars().allMatch(c -> c == '.' || c == '_')) {
			cleaned = "export";
		}
		return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
	}
}
