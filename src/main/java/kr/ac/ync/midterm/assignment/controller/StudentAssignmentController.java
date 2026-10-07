package kr.ac.ync.midterm.assignment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import kr.ac.ync.midterm.assignment.service.StudentAssignmentService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
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

	@GetMapping
	public String list(@AuthenticationPrincipal CustomUserDetails user, Model model) {
		model.addAttribute("assignments", studentAssignmentService.findMyAssignments(user.getId()));
		return "student/assignments";
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id, Model model) {
		model.addAttribute("assignment", studentAssignmentService.findMyAssignment(user.getId(), id));
		return "student/assignment-detail";
	}
}
