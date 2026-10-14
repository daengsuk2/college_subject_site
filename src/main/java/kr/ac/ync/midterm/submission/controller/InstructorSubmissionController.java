package kr.ac.ync.midterm.submission.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.service.SubmissionStatusService;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 제출 현황 화면 (I4). /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록 막혀 있고,
 * 본인 강좌의 과제인지(B5)는 서비스에서 검사한다.
 */
@Controller
@RequestMapping("/instructor/assignments/{assignmentId}/submissions")
@RequiredArgsConstructor
public class InstructorSubmissionController {

	private final SubmissionStatusService submissionStatusService;

	@GetMapping
	public String status(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long assignmentId, Model model) {
		model.addAttribute("status", submissionStatusService.findStatus(user.getId(), assignmentId));
		return "instructor/submissions";
	}
}
