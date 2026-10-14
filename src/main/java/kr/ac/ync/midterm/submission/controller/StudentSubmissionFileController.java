package kr.ac.ync.midterm.submission.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.SubmissionFileNotFoundException;
import kr.ac.ync.midterm.submission.exception.SubmissionNotFoundException;
import kr.ac.ync.midterm.submission.exception.SubmissionPeriodException;
import kr.ac.ync.midterm.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;

/**
 * 학생의 제출 파일 삭제 (X23). /student/** 는 SecurityConfig에서 STUDENT만 접근하도록 막혀 있고,
 * 수강(B2) · 기간(B1) · 채점 완료(B4)는 서비스가 검사한다. POST로만 동작한다.
 */
@Controller
@RequestMapping("/student/assignments/{assignmentId}/submission/file")
@RequiredArgsConstructor
public class StudentSubmissionFileController {

	private final SubmissionService submissionService;

	@PostMapping("/delete")
	public String delete(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long assignmentId,
			RedirectAttributes redirectAttributes) {
		try {
			submissionService.removeFile(user.getId(), assignmentId);
			redirectAttributes.addFlashAttribute("message", "첨부를 삭제했습니다.");
			return "redirect:/student/assignments/" + assignmentId + "/submit";
		} catch (SubmissionPeriodException | AlreadyGradedException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		} catch (SubmissionNotFoundException | SubmissionFileNotFoundException e) {
			redirectAttributes.addFlashAttribute("error", "삭제할 첨부가 없습니다.");
		}
		return "redirect:/student/assignments/" + assignmentId;
	}
}
