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
import kr.ac.ync.midterm.submission.dto.request.ObjectionResponseRequest;
import kr.ac.ync.midterm.submission.exception.InvalidScoreException;
import kr.ac.ync.midterm.submission.exception.ObjectionException;
import kr.ac.ync.midterm.submission.service.ObjectionService;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 이의 답변 (X29). /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록 막혀 있고,
 * 본인 강좌의 제출인지(B5)와 점수 범위(B6)는 서비스에서 검사한다. POST로만 동작한다.
 */
@Controller
@RequestMapping("/instructor/submissions/{submissionId}/objection")
@RequiredArgsConstructor
public class InstructorObjectionController {

	private final ObjectionService objectionService;

	@PostMapping
	public String respond(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long submissionId,
			@Valid @ModelAttribute ObjectionResponseRequest objectionResponseRequest, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", bindingResult.getFieldError().getDefaultMessage());
		} else {
			try {
				objectionService.respond(user.getId(), submissionId, objectionResponseRequest);
				redirectAttributes.addFlashAttribute("message", "이의에 답변했습니다.");
			} catch (ObjectionException | InvalidScoreException e) {
				redirectAttributes.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/instructor/submissions/" + submissionId + "/grade";
	}
}
