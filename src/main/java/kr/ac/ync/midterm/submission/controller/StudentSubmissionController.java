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
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;
import kr.ac.ync.midterm.assignment.dto.response.StudentAssignmentResponse;
import kr.ac.ync.midterm.assignment.service.StudentAssignmentService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.SubmissionPeriodException;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;

/**
 * 학생의 과제 제출 화면 (S3). /student/** 는 SecurityConfig에서 STUDENT만 접근하도록 막혀 있고,
 * 수강(B2) · 기간(B1) · 채점 완료(B4)는 서비스가 검사한다. 화면에서 버튼을 숨겨도 서버가 다시 막는다.
 */
@Controller
@RequestMapping("/student/assignments/{assignmentId}/submit")
@RequiredArgsConstructor
public class StudentSubmissionController {

	private static final String FORM_VIEW = "student/submission-form";

	private final SubmissionService submissionService;
	private final StudentAssignmentService studentAssignmentService;

	@GetMapping
	public String form(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long assignmentId, Model model, RedirectAttributes redirectAttributes) {
		StudentAssignmentResponse assignment = studentAssignmentService.findMyAssignment(user.getId(), assignmentId);
		SubmissionResponse submission = submissionService.findMySubmission(user.getId(), assignmentId).orElse(null);

		if (assignment.status() != AssignmentStatus.IN_PROGRESS) {
			redirectAttributes.addFlashAttribute("error", new SubmissionPeriodException().getMessage());
			return "redirect:/student/assignments/" + assignmentId;
		}
		if (submission != null && submission.isGraded()) {
			redirectAttributes.addFlashAttribute("error", new AlreadyGradedException().getMessage());
			return "redirect:/student/assignments/" + assignmentId;
		}

		model.addAttribute("assignment", assignment);
		model.addAttribute("resubmit", submission != null);
		model.addAttribute("submissionRequest",
				submission != null ? SubmissionRequest.from(submission) : new SubmissionRequest());
		return FORM_VIEW;
	}

	@PostMapping
	public String submit(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long assignmentId,
			@Valid @ModelAttribute SubmissionRequest submissionRequest,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		StudentAssignmentResponse assignment = studentAssignmentService.findMyAssignment(user.getId(), assignmentId);
		boolean resubmit = submissionService.findMySubmission(user.getId(), assignmentId).isPresent();

		if (!bindingResult.hasErrors()) {
			try {
				submissionService.submit(user.getId(), assignmentId, submissionRequest);
				redirectAttributes.addFlashAttribute("message", resubmit ? "다시 제출했습니다." : "제출했습니다.");
			} catch (SubmissionPeriodException | AlreadyGradedException e) {
				redirectAttributes.addFlashAttribute("error", e.getMessage());
			}
			return "redirect:/student/assignments/" + assignmentId;
		}

		model.addAttribute("assignment", assignment);
		model.addAttribute("resubmit", resubmit);
		return FORM_VIEW;
	}
}
