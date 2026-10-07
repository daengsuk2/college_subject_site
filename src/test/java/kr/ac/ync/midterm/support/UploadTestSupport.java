package kr.ac.ync.midterm.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.util.FileSystemUtils;

/**
 * 첨부파일 테스트용 도우미. 테스트 업로드 폴더(application-test.yml의 app.upload.dir)를 비우고 파일 수를 센다.
 */
public final class UploadTestSupport {

	/** application-test.yml 의 app.upload.dir 과 같은 값 */
	public static final Path TEST_UPLOAD_DIR = Path.of("build/test-uploads");

	private UploadTestSupport() {
	}

	public static void clean() {
		FileSystemUtils.deleteRecursively(TEST_UPLOAD_DIR.toFile());
	}

	public static long fileCount() {
		Path dir = TEST_UPLOAD_DIR.toAbsolutePath();
		if (!Files.exists(dir)) {
			return 0;
		}
		try (Stream<Path> stream = Files.walk(dir)) {
			return stream.filter(Files::isRegularFile).count();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
