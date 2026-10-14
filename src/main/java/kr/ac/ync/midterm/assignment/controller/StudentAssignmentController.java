package kr.ac.ync.midterm.assignment.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import kr.ac.ync.midterm.assignment.service.StudentAssignmentService;
import kr.ac.ync.midterm.attachment.service.AttachmentService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;

/**
 * 학생의 과제 화면 (S2). /student/** 는 SecurityConfig에서 STUDENT만 접근하도록 막혀 있고,
 * 수강 등록한 강좌의 과제인지(B2)는 서비스에서 검사한다.
 */
@Controller
@RequestMapping("/student/assignments")
@RequiredArgsConstructor
public class StudentAssignmentController {

	private final StudentAssignmentService studentAssignmentService;
	private final SubmissionService submissionService;
	private final AttachmentService attachmentService;

	@GetMapping
	public String list(@AuthenticationPrincipal CustomUserDetails user, Model model) {
		model.addAttribute("assignments", studentAssignmentService.findMyAssignments(user.getId()));
		return "student/assignments";
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id, Model model) {
		model.addAttribute("assignment", studentAssignmentService.findMyAssignment(user.getId(), id));
		SubmissionResponse submission = submissionService.findMySubmission(user.getId(), id).orElse(null);
		model.addAttribute("submission", submission);
		model.addAttribute("assignmentAttachments", attachmentService.findByAssignment(id));
		model.addAttribute("submissionAttachments",
				submission == null ? List.of() : attachmentService.findBySubmission(submission.id()));
		return "student/assignment-detail";
	}
}
