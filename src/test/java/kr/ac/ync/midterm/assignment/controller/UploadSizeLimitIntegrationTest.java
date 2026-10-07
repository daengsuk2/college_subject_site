package kr.ac.ync.midterm.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.support.UploadTestSupport;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 실제 서버(Tomcat)에 큰 파일을 올려 본다. MockMvc는 서블릿의 업로드 제한과 연결 처리를 거치지 않아서,
 * 한도(10MB)를 넘는 파일을 올렸을 때 브라우저가 413 안내 화면 대신 응답을 못 받고 멈추는 문제를 잡지 못한다.
 * 이 테스트는 데이터를 실제로 저장(커밋)하므로 끝난 뒤 직접 정리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadSizeLimitIntegrationTest {

	private static final String EMAIL = "upload-limit@test.com";
	private static final String PASSWORD = "password123";
	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private HttpClient client;
	private Course course;

	@BeforeEach
	void setUp() {
		cleanData();
		UploadTestSupport.clean();
		User instructor = userRepository.save(User.builder()
				.email(EMAIL).password(passwordEncoder.encode(PASSWORD)).name("업로드확인강사")
				.role(Role.INSTRUCTOR).build());
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("업로드 한도").joinCode("UPLM22").build());

		client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager())
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	@AfterEach
	void tearDown() {
		cleanData();
		UploadTestSupport.clean();
	}

	// 이 테스트가 커밋한 데이터를 지운다 (다른 테스트와 섞이지 않게)
	private void cleanData() {
		userRepository.findByEmail(EMAIL).ifPresent(user -> {
			courseRepository.findByInstructorIdOrderByCreatedAtDesc(user.getId()).forEach(c -> {
				assignmentRepository.deleteAll(assignmentRepository.findByCourseIdOrderByStartAtDesc(c.getId()));
				courseRepository.delete(c);
			});
			userRepository.delete(user);
		});
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private String csrfFrom(String html) {
		Matcher m = CSRF.matcher(html);
		assertThat(m.find()).as("CSRF 토큰이 있는 폼").isTrue();
		return m.group(1) != null ? m.group(1) : m.group(2);
	}

	private String get(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
				HttpResponse.BodyHandlers.ofString()).body();
	}

	private String loginAndGetFormToken() throws Exception {
		String loginToken = csrfFrom(get("/login"));
		String form = "email=" + URLEncoder.encode(EMAIL, StandardCharsets.UTF_8)
				+ "&password=" + PASSWORD + "&_csrf=" + loginToken;
		HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create(url("/login")))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(login.statusCode()).isEqualTo(302);
		return csrfFrom(get("/instructor/assignments/new?courseId=" + course.getId()));
	}

	private HttpResponse<String> uploadZip(String csrf, int sizeBytes) throws Exception {
		String boundary = "----upload" + System.nanoTime();
		ByteArrayOutputStream body = new ByteArrayOutputStream(sizeBytes + 2048);
		List<String[]> fields = List.of(
				new String[] { "_csrf", csrf }, new String[] { "title", "큰 파일 과제" },
				new String[] { "content", "내용" }, new String[] { "startAt", "2026-10-10T09:00" },
				new String[] { "endAt", "2026-10-17T23:59" }, new String[] { "maxScore", "100" });
		for (String[] f : fields) {
			body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f[0] + "\"\r\n\r\n"
					+ f[1] + "\r\n").getBytes(StandardCharsets.UTF_8));
		}
		body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.zip\"\r\n"
				+ "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.writeBytes(new byte[sizeBytes]);
		body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		return client.send(HttpRequest.newBuilder(URI.create(url("/instructor/assignments/new?courseId=" + course.getId())))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.timeout(Duration.ofSeconds(60))
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	@DisplayName("10MB 이하 파일은 실제 서버에서도 정상 등록됨")
	void upload_underLimit() throws Exception {
		String csrf = loginAndGetFormToken();

		HttpResponse<String> response = uploadZip(csrf, 9 * 1024 * 1024);

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId())).hasSize(1);
		Assignment saved = assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId()).getFirst();
		assertThat(saved.getOriginalFilename()).isEqualTo("big.zip");
	}

	@Test
	@DisplayName("10MB를 넘는 파일(11MB, 50MB)은 연결이 끊기지 않고 413 안내 화면을 받으며 저장되지 않음")
	void upload_overLimit() throws Exception {
		String csrf = loginAndGetFormToken();

		for (int mb : new int[] { 11, 50 }) {
			HttpResponse<String> response = uploadZip(csrf, mb * 1024 * 1024);

			assertThat(response.statusCode()).as(mb + "MB 업로드 응답").isEqualTo(413);
			assertThat(response.body()).contains("10MB 이하");
		}
		assertThat(assignmentRepository.findByCourseIdOrderByStartAtDesc(course.getId())).isEmpty();
		assertThat(UploadTestSupport.fileCount()).isZero();
	}
}
