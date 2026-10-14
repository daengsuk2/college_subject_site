package kr.ac.ync.midterm.attachment.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kr.ac.ync.midterm.attachment.dto.AttachmentFileResponse;
import kr.ac.ync.midterm.attachment.service.AttachmentService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.SubmissionPeriodException;
import lombok.RequiredArgsConstructor;

/**
 * 추가 첨부의 다운로드와 삭제 (X17). 다운로드는 강사와 학생이 같은 주소를 쓰며 권한은 서비스에서 역할별로 검사한다.
 * /instructor/** , /student/** 는 SecurityConfig에서 역할별로 막혀 있다. 삭제는 POST로만 동작한다.
 */
@Controller
@RequiredArgsConstructor
public class AttachmentController {

	private final AttachmentService attachmentService;

	/** 브라우저가 파일을 화면에 직접 실행하지 않도록 항상 내려받기(attachment, octet-stream)로 응답한다. */
	@GetMapping("/attachments/{id}/file")
	public ResponseEntity<Resource> download(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
		AttachmentFileResponse file = attachmentService.getFile(user.getId(), user.getRole(), id);

		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(file.originalFilename(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(file.resource());
	}

	@PostMapping("/instructor/attachments/{id}/delete")
	public String removeFromAssignment(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		Long assignmentId = attachmentService.removeFromAssignment(user.getId(), id);
		redirectAttributes.addFlashAttribute("message", "첨부를 삭제했습니다.");
		return "redirect:/instructor/assignments/" + assignmentId + "/edit";
	}

	@PostMapping("/student/assignments/{assignmentId}/attachments/{id}/delete")
	public String removeFromSubmission(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long assignmentId, @PathVariable Long id, RedirectAttributes redirectAttributes) {
		try {
			attachmentService.removeFromSubmission(user.getId(), assignmentId, id);
			redirectAttributes.addFlashAttribute("message", "첨부를 삭제했습니다.");
			return "redirect:/student/assignments/" + assignmentId + "/submit";
		} catch (SubmissionPeriodException | AlreadyGradedException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/student/assignments/" + assignmentId;
		}
	}
}
