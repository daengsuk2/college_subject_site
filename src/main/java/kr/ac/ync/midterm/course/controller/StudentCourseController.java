package kr.ac.ync.midterm.course.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import kr.ac.ync.midterm.course.dto.request.EnrollRequest;
import kr.ac.ync.midterm.course.dto.response.EnrollmentResponse;
import kr.ac.ync.midterm.course.exception.AlreadyEnrolledException;
import kr.ac.ync.midterm.course.exception.InvalidJoinCodeException;
import kr.ac.ync.midterm.course.service.EnrollmentService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;

/**
 * 학생의 수강 등록 화면. /student/** 는 SecurityConfig에서 STUDENT만 접근하도록 막혀 있다.
 */
@Controller
@RequestMapping("/student/courses")
@RequiredArgsConstructor
public class StudentCourseController {

	private final EnrollmentService enrollmentService;

	@GetMapping("/join")
	public String joinForm(Model model) {
		model.addAttribute("enrollRequest", new EnrollRequest());
		return "student/join";
	}

	@PostMapping("/join")
	public String join(@AuthenticationPrincipal CustomUserDetails user,
			@Valid @ModelAttribute EnrollRequest enrollRequest,
			BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "student/join";
		}

		try {
			EnrollmentResponse enrolled = enrollmentService.enroll(user.getId(), enrollRequest);
			redirectAttributes.addFlashAttribute("message",
					enrolled.courseName() + " 강좌에 수강 등록되었습니다.");
			return "redirect:/student/courses/join";
		} catch (InvalidJoinCodeException | AlreadyEnrolledException e) {
			bindingResult.rejectValue("joinCode", "enroll.failed", e.getMessage());
			return "student/join";
		}
	}
}
