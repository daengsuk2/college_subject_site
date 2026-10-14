package kr.ac.ync.midterm.submission.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.dto.request.ObjectionRequest;
import kr.ac.ync.midterm.submission.exception.ObjectionException;
import kr.ac.ync.midterm.submission.service.ObjectionService;
import lombok.RequiredArgsConstructor;

/**
 * 학생의 이의 신청 (X29). /student/** 는 SecurityConfig에서 STUDENT만 접근하도록 막혀 있고,
 * 수강(B2) · 채점 완료 · 1회 제한은 서비스가 검사한다. POST로만 동작한다.
 */
@Controller
@RequestMapping("/student/assignments/{assignmentId}/submission/objection")
@RequiredArgsConstructor
public class StudentObjectionController {

	private final ObjectionService objectionService;

	@PostMapping
	public String raise(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long assignmentId,
			@Valid @ModelAttribute ObjectionRequest objectionRequest, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", bindingResult.getFieldError().getDefaultMessage());
		} else {
			try {
				objectionService.raise(user.getId(), assignmentId, objectionRequest);
				redirectAttributes.addFlashAttribute("message", "이의를 신청했습니다.");
			} catch (ObjectionException e) {
				redirectAttributes.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/student/assignments/" + assignmentId;
	}
}
