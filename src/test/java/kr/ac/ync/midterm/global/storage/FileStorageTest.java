package kr.ac.ync.midterm.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageTest {

	@TempDir
	Path tempDir;

	private Path root;
	private FileStorage storage;

	@BeforeEach
	void setUp() {
		root = tempDir.resolve("uploads");
		storage = new FileStorage(root.toString());
	}

	private MockMultipartFile file(String originalName, String content) {
		return new MockMultipartFile("file", originalName, "application/octet-stream",
				content.getBytes(StandardCharsets.UTF_8));
	}

	private long countFilesUnder(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return 0;
		}
		try (Stream<Path> stream = Files.walk(dir)) {
			return stream.filter(Files::isRegularFile).count();
		}
	}

	// ---------- 저장 ----------

	@Test
	@DisplayName("store - UUID 이름으로 저장되고 원본 파일명은 따로 반환됨")
	void store_success() throws IOException {
		// when
		StoredFile stored = storage.store(file("1주차 과제.pdf", "내용"));

		// then
		assertThat(stored.originalName()).isEqualTo("1주차 과제.pdf");
		assertThat(stored.storedName()).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(stored.storedName()).doesNotContain("1주차", "/", "\\");
		assertThat(Files.readString(root.resolve(stored.storedName()))).isEqualTo("내용");
		assertThat(storage.exists(stored.storedName())).isTrue();
	}

	@Test
	@DisplayName("store - 같은 이름의 파일을 두 번 올려도 서로 덮어쓰지 않음")
	void store_sameNameTwice() {
		// when
		StoredFile first = storage.store(file("report.pdf", "첫번째"));
		StoredFile second = storage.store(file("report.pdf", "두번째"));

		// then
		assertThat(first.storedName()).isNotEqualTo(second.storedName());
		assertThat(storage.exists(first.storedName())).isTrue();
		assertThat(storage.exists(second.storedName())).isTrue();
	}

	@Test
	@DisplayName("store - 파일을 선택하지 않았으면(비어 있으면) null이고 아무것도 저장하지 않음")
	void store_empty() throws IOException {
		assertThat(storage.store(null)).isNull();
		assertThat(storage.store(file("empty.pdf", ""))).isNull();
		assertThat(countFilesUnder(root)).isZero();
	}

	@Test
	@DisplayName("store - 확장자는 대소문자를 구분하지 않음")
	void store_upperCaseExtension() {
		StoredFile stored = storage.store(file("REPORT.PDF", "내용"));

		assertThat(stored.storedName()).endsWith(".pdf");
		assertThat(stored.originalName()).isEqualTo("REPORT.PDF");
	}

	// ---------- 허용 확장자 ----------

	@ParameterizedTest(name = "허용 확장자 [{0}]")
	@ValueSource(strings = { "a.pdf", "a.doc", "a.docx", "a.hwp", "a.hwpx", "a.ppt", "a.pptx",
			"a.xls", "a.xlsx", "a.txt", "a.zip", "a.png", "a.jpg", "a.jpeg" })
	@DisplayName("store - 허용한 확장자는 저장됨")
	void store_allowedExtensions(String name) {
		assertThat(storage.store(file(name, "x"))).isNotNull();
	}

	@ParameterizedTest(name = "거부 파일명 [{0}]")
	@ValueSource(strings = {
			"virus.exe", "script.sh", "page.jsp", "page.html", "page.htm", "shell.php", "run.bat",
			"app.jar", "evil.js", "image.svg",
			"noextension", "endswithdot.", ".hidden",
			"double.pdf.exe", "double.exe.pdf.sh"
	})
	@DisplayName("store - 허용하지 않는 확장자 · 확장자 없는 파일은 거부되고 저장되지 않음")
	void store_rejectedExtensions(String name) throws IOException {
		assertThatThrownBy(() -> storage.store(file(name, "x")))
				.isInstanceOf(InvalidFileException.class);
		assertThat(countFilesUnder(root)).isZero();
	}

	// ---------- 크기 ----------

	@Test
	@DisplayName("store - 10MB 정확히는 허용, 1바이트라도 넘으면 거부")
	void store_sizeLimit() throws IOException {
		byte[] exactly = new byte[(int) FileStorage.MAX_SIZE];
		byte[] tooBig = new byte[(int) FileStorage.MAX_SIZE + 1];

		assertThat(storage.store(new MockMultipartFile("file", "ok.zip", "application/zip", exactly))).isNotNull();

		assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "big.zip", "application/zip", tooBig)))
				.isInstanceOf(InvalidFileException.class)
				.hasMessageContaining("10MB");
		assertThat(countFilesUnder(root)).isEqualTo(1);
	}

	// ---------- 경로 조작 ----------

	@ParameterizedTest(name = "경로 조작 이름 [{0}]")
	@ValueSource(strings = {
			"../../evil.pdf",
			"..\\..\\evil.pdf",
			"/etc/passwd.pdf",
			"C:\\Windows\\evil.pdf",
			"folder/../../evil.pdf",
			"....//....//evil.pdf"
	})
	@DisplayName("store - 파일명에 경로 조작이 있어도 지정한 폴더 안에 UUID 이름으로만 저장됨")
	void store_pathTraversalInName(String name) throws IOException {
		// when
		StoredFile stored = storage.store(file(name, "내용"));

		// then: 저장 이름에는 경로가 없고, 폴더 밖에는 아무 파일도 생기지 않는다
		assertThat(stored.storedName()).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(stored.originalName()).doesNotContain("/", "\\");
		assertThat(countFilesUnder(root)).isEqualTo(1);
		assertThat(Files.exists(tempDir.resolve("evil.pdf"))).isFalse();
		assertThat(countFilesUnder(tempDir)).isEqualTo(1);
		assertThat(Files.exists(root.resolve(stored.storedName()))).isTrue();
	}

	@ParameterizedTest(name = "제어 문자 이름 [{0}]")
	@ValueSource(strings = { "evil.pdf\u0000.exe", "evil\r\nSet-Cookie: x=1.pdf", "line\nbreak.pdf" })
	@DisplayName("store - 제어 문자(널, 줄바꿈)가 있는 파일명은 거부됨")
	void store_controlCharacters(String name) throws IOException {
		assertThatThrownBy(() -> storage.store(file(name, "x")))
				.isInstanceOf(InvalidFileException.class);
		assertThat(countFilesUnder(root)).isZero();
	}

	@Test
	@DisplayName("store - 255자를 넘는 파일명은 거부됨")
	void store_tooLongName() {
		String longName = "가".repeat(256) + ".pdf";

		assertThatThrownBy(() -> storage.store(file(longName, "x")))
				.isInstanceOf(InvalidFileException.class);
	}

	// ---------- 읽기 · 삭제 ----------

	@ParameterizedTest(name = "읽기 시도 [{0}]")
	@ValueSource(strings = { "../secret.txt", "../../etc/passwd", "sub/file.pdf", "/etc/passwd", "..", "." })
	@DisplayName("load · exists · deleteQuietly - 폴더 밖을 가리키는 이름은 읽을 수도 지울 수도 없음")
	void access_outsideRoot(String storedName) throws IOException {
		// given: 폴더 밖에 파일을 만들어 둔다
		Files.writeString(tempDir.resolve("secret.txt"), "비밀");

		// when & then
		assertThatThrownBy(() -> storage.load(storedName)).isInstanceOf(InvalidFileException.class);
		assertThatThrownBy(() -> storage.exists(storedName)).isInstanceOf(InvalidFileException.class);
		storage.deleteQuietly(storedName); // 예외 없이 무시되고, 폴더 밖 파일은 그대로
		assertThat(Files.readString(tempDir.resolve("secret.txt"))).isEqualTo("비밀");
	}

	@Test
	@DisplayName("load - 저장한 파일은 읽을 수 있고 없는 파일은 null")
	void load() throws IOException {
		StoredFile stored = storage.store(file("a.txt", "안녕"));

		Resource resource = storage.load(stored.storedName());
		assertThat(resource).isNotNull();
		// 스트림을 닫지 않으면 Windows에서 임시 폴더를 지우지 못한다
		try (var in = resource.getInputStream()) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("안녕");
		}

		assertThat(storage.load("00000000-0000-0000-0000-000000000000.pdf")).isNull();
	}

	@Test
	@DisplayName("deleteQuietly - 저장한 파일은 삭제되고 없는 파일 · null은 조용히 넘어감")
	void deleteQuietly() {
		StoredFile stored = storage.store(file("a.txt", "x"));

		storage.deleteQuietly(stored.storedName());
		storage.deleteQuietly("00000000-0000-0000-0000-000000000000.pdf");
		storage.deleteQuietly(null);

		assertThat(storage.exists(stored.storedName())).isFalse();
	}

	@Test
	@DisplayName("허용 확장자 목록 - 실행 가능한 형식(exe, sh, jsp, html 등)은 포함되지 않음")
	void allowedList_hasNoExecutables() {
		List<String> dangerous = List.of("exe", "sh", "bat", "jsp", "html", "htm", "php", "js", "jar", "svg");
		for (String ext : dangerous) {
			assertThatThrownBy(() -> storage.store(file("file." + ext, "x")))
					.isInstanceOf(InvalidFileException.class);
		}
	}
}
