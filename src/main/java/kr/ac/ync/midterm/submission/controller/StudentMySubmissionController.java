package kr.ac.ync.midterm.submission.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;

/**
 * 학생의 "내 제출 · 점수" 목록 (S4). /student/** 는 SecurityConfig에서 STUDENT만 접근하도록 막혀 있고,
 * 서비스가 로그인한 학생 본인의 제출만 조회한다. 점수와 피드백은 과제 상세에서 확인한다.
 */
@Controller
@RequestMapping("/student/submissions")
@RequiredArgsConstructor
public class StudentMySubmissionController {

	private final SubmissionService submissionService;

	@GetMapping
	public String list(@AuthenticationPrincipal CustomUserDetails user, Model model) {
		model.addAttribute("submissions", submissionService.findMySubmissions(user.getId()));
		return "student/submissions";
	}
}
