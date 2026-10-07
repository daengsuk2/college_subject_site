package kr.ac.ync.midterm.assignment.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import kr.ac.ync.midterm.assignment.dto.response.AssignmentFileResponse;
import kr.ac.ync.midterm.assignment.service.AssignmentFileService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;

/**
 * 과제 첨부파일 다운로드 (I3-b). 강사와 학생이 같은 주소를 쓰며, 권한은 서비스에서 역할별로 검사한다.
 * 브라우저가 파일을 화면에 직접 실행하지 않도록 항상 내려받기(attachment, octet-stream)로 응답한다.
 */
@Controller
@RequiredArgsConstructor
public class AssignmentFileController {

	private final AssignmentFileService assignmentFileService;

	@GetMapping("/assignments/{id}/file")
	public ResponseEntity<Resource> download(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id) {
		AssignmentFileResponse file = assignmentFileService.getFile(user.getId(), user.getRole(), id);

		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(file.originalFilename(), StandardCharsets.UTF_8)
				.build();

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(file.resource());
	}
}
