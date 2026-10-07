package kr.ac.ync.midterm.course.controller;

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
import kr.ac.ync.midterm.assignment.service.AssignmentService;
import kr.ac.ync.midterm.course.dto.request.CourseCreateRequest;
import kr.ac.ync.midterm.course.dto.response.CourseResponse;
import kr.ac.ync.midterm.course.service.CourseService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 강좌 관리 화면. /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록 막혀 있다.
 */
@Controller
@RequestMapping("/instructor/courses")
@RequiredArgsConstructor
public class InstructorCourseController {

	private final CourseService courseService;
	private final AssignmentService assignmentService;

	@GetMapping
	public String list(@AuthenticationPrincipal CustomUserDetails user, Model model) {
		model.addAttribute("courses", courseService.findMyCourses(user.getId()));
		model.addAttribute("courseCreateRequest", new CourseCreateRequest());
		return "instructor/courses";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CustomUserDetails user,
			@Valid @ModelAttribute CourseCreateRequest courseCreateRequest,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("courses", courseService.findMyCourses(user.getId()));
			return "instructor/courses";
		}

		CourseResponse created = courseService.create(user.getId(), courseCreateRequest);
		redirectAttributes.addFlashAttribute("message",
				"강좌가 개설되었습니다. 참여코드: " + created.joinCode());
		return "redirect:/instructor/courses";
	}

	/**
	 * 강좌 상세 + 수강생 목록 (I2) + 과제 목록 (I3). 없는 강좌 404, 다른 강사의 강좌 403은 서비스에서 처리한다.
	 */
	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id, Model model) {
		model.addAttribute("course", courseService.findMyCourseDetail(user.getId(), id));
		model.addAttribute("assignments", assignmentService.findMyAssignments(user.getId(), id));
		return "instructor/course-detail";
	}
}
