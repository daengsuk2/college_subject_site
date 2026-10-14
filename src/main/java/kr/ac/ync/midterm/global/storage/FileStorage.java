package kr.ac.ync.midterm.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 저장소.
 * - 서버에는 UUID 이름으로만 저장하고 사용자가 올린 파일명은 경로에 쓰지 않는다. (경로 조작 방지)
 * - 허용한 확장자와 최대 크기만 받는다.
 * - 저장 · 읽기 · 삭제 모두 지정한 폴더 밖으로 나갈 수 없다.
 */
@Component
public class FileStorage {

	public static final long MAX_SIZE = 10L * 1024 * 1024;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"pdf", "doc", "docx", "hwp", "hwpx", "ppt", "pptx", "xls", "xlsx", "txt", "zip",
			"png", "jpg", "jpeg");

	private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

	private final Path root;

	public FileStorage(@Value("${app.upload.dir:uploads}") String uploadDir) {
		this.root = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	/**
	 * 파일을 저장한다. 파일을 선택하지 않았다면(비어 있으면) null을 반환한다.
	 */
	public StoredFile store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}
		if (file.getSize() > MAX_SIZE) {
			throw new InvalidFileException("파일 크기는 10MB 이하여야 합니다.");
		}

		String originalName = sanitizeOriginalName(file.getOriginalFilename());
		String extension = extensionOf(originalName);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new InvalidFileException("허용하지 않는 파일 형식입니다. (허용: " + String.join(", ", sortedExtensions()) + ")");
		}

		String storedName = UUID.randomUUID() + "." + extension;
		Path target = resolveInsideRoot(storedName);
		try {
			Files.createDirectories(root);
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 저장하지 못했습니다.", e);
		}
		return new StoredFile(storedName, originalName);
	}

	/**
	 * 저장된 파일을 읽을 수 있는 Resource로 반환한다. 파일이 없으면 null.
	 */
	public Resource load(String storedName) {
		Path path = resolveInsideRoot(storedName);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			return new UrlResource(path.toUri());
		} catch (MalformedURLException e) {
			throw new UncheckedIOException(e);
		}
	}

	public boolean exists(String storedName) {
		return storedName != null && Files.isRegularFile(resolveInsideRoot(storedName));
	}

	/**
	 * 저장된 파일을 삭제한다. 없거나 이름이 올바르지 않으면 조용히 넘어간다.
	 */
	public void deleteQuietly(String storedName) {
		if (storedName == null) {
			return;
		}
		try {
			Files.deleteIfExists(resolveInsideRoot(storedName));
		} catch (IOException | InvalidFileException e) {
			// 파일 정리 실패가 업무 처리를 막지 않도록 무시한다
		}
	}

	/**
	 * DB 변경이 확정(커밋)된 뒤에 파일을 삭제한다. DB 처리가 실패해 롤백되면 파일은 지워지지 않는다.
	 * (파일을 먼저 지웠는데 DB 저장이 실패하면 되돌릴 수 없기 때문이다.) 트랜잭션 밖에서 부르면 바로 삭제한다.
	 */
	public void deleteAfterCommit(String storedName) {
		if (storedName == null) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deleteQuietly(storedName);
				}
			});
		} else {
			deleteQuietly(storedName);
		}
	}

	/**
	 * 방금 저장한 새 파일이 DB 변경과 함께 확정되지 못하면(트랜잭션이 롤백되면) 삭제한다. 커밋되면 그대로 둔다.
	 * 메서드 안에서 난 예외뿐 아니라 커밋 단계에서 실패하는 경우에도 새 파일이 서버에 남지 않게 한다.
	 * 트랜잭션 밖에서는 아무것도 하지 않는다. (삭제는 deleteQuietly로 직접 한다)
	 */
	public void deleteOnRollback(String storedName) {
		if (storedName == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					deleteQuietly(storedName);
				}
			}
		});
	}

	// 저장 이름이 폴더 밖을 가리키면(../ 등) 거부한다
	private Path resolveInsideRoot(String storedName) {
		if (!StringUtils.hasText(storedName)) {
			throw new InvalidFileException("파일 이름이 올바르지 않습니다.");
		}
		Path resolved = root.resolve(storedName).normalize();
		if (!resolved.startsWith(root) || resolved.equals(root) || !resolved.getParent().equals(root)) {
			throw new InvalidFileException("파일 이름이 올바르지 않습니다.");
		}
		return resolved;
	}

	// 사용자가 올린 이름에서 경로를 떼어 내고, 제어 문자가 있으면 거부한다 (표시용으로만 사용)
	private String sanitizeOriginalName(String originalFilename) {
		if (!StringUtils.hasText(originalFilename)) {
			throw new InvalidFileException("파일 이름이 올바르지 않습니다.");
		}
		for (char c : originalFilename.toCharArray()) {
			if (Character.isISOControl(c)) {
				throw new InvalidFileException("파일 이름에 사용할 수 없는 문자가 있습니다.");
			}
		}
		String name = StringUtils.getFilename(StringUtils.cleanPath(originalFilename.replace('\\', '/')));
		if (!StringUtils.hasText(name) || name.equals(".") || name.equals("..")) {
			throw new InvalidFileException("파일 이름이 올바르지 않습니다.");
		}
		if (name.length() > MAX_ORIGINAL_NAME_LENGTH) {
			throw new InvalidFileException("파일 이름이 너무 깁니다.");
		}
		return name;
	}

	private String extensionOf(String name) {
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return "";
		}
		return name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private java.util.List<String> sortedExtensions() {
		return ALLOWED_EXTENSIONS.stream().sorted().toList();
	}
}
