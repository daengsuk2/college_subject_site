package kr.ac.ync.midterm.submission.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.dto.request.GradeRequest;
import kr.ac.ync.midterm.submission.dto.response.GradeFormResponse;
import kr.ac.ync.midterm.submission.exception.InvalidScoreException;
import kr.ac.ync.midterm.submission.service.GradingService;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 채점 화면 (I5). /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록 막혀 있고,
 * 본인 강좌의 제출인지(B5)와 점수 범위(B6)는 서비스에서 검사한다.
 */
@Controller
@RequestMapping("/instructor/submissions/{submissionId}/grade")
@RequiredArgsConstructor
public class InstructorGradingController {

	private static final String FORM_VIEW = "instructor/grade";

	private final GradingService gradingService;

	@GetMapping
	public String form(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long submissionId, Model model) {
		GradeFormResponse submission = gradingService.findForGrading(user.getId(), submissionId);

		model.addAttribute("submission", submission);
		model.addAttribute("gradeRequest", GradeRequest.from(submission));
		return FORM_VIEW;
	}

	@PostMapping
	public String grade(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long submissionId,
			@Valid @ModelAttribute GradeRequest gradeRequest,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		GradeFormResponse submission = gradingService.findForGrading(user.getId(), submissionId);

		if (!bindingResult.hasErrors()) {
			try {
				gradingService.grade(user.getId(), submissionId, gradeRequest);
				redirectAttributes.addFlashAttribute("message", "채점을 저장했습니다.");
				return "redirect:/instructor/assignments/" + submission.assignmentId() + "/submissions";
			} catch (InvalidScoreException e) {
				bindingResult.rejectValue("score", "score.range", e.getMessage());
			}
		}

		model.addAttribute("submission", submission);
		return FORM_VIEW;
	}
}
