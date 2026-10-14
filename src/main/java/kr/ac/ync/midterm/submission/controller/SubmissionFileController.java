package kr.ac.ync.midterm.submission.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.dto.response.SubmissionFileResponse;
import kr.ac.ync.midterm.submission.service.SubmissionFileService;
import lombok.RequiredArgsConstructor;

/**
 * 제출 파일 다운로드 (S3-b). 학생과 강사가 같은 주소를 쓰며, 권한은 서비스에서 역할별로 검사한다.
 * 브라우저가 파일을 화면에 직접 실행하지 않도록 항상 내려받기(attachment, octet-stream)로 응답한다.
 */
@Controller
@RequiredArgsConstructor
public class SubmissionFileController {

	private final SubmissionFileService submissionFileService;

	@GetMapping("/submissions/{id}/file")
	public ResponseEntity<Resource> download(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id) {
		SubmissionFileResponse file = submissionFileService.getFile(user.getId(), user.getRole(), id);

		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(file.originalFilename(), StandardCharsets.UTF_8)
				.build();

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(file.resource());
	}
}
