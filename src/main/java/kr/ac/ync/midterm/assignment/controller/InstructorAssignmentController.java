package kr.ac.ync.midterm.assignment.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentHasSubmissionsException;
import kr.ac.ync.midterm.assignment.exception.InvalidAssignmentPeriodException;
import kr.ac.ync.midterm.assignment.service.AssignmentService;
import kr.ac.ync.midterm.course.dto.response.CourseDetailResponse;
import kr.ac.ync.midterm.course.service.CourseService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.global.storage.InvalidFileException;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 과제 관리 화면 (I3). /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록 막혀 있고,
 * 본인 강좌의 과제인지(B5)는 서비스에서 검사한다.
 */
@Controller
@RequestMapping("/instructor/assignments")
@RequiredArgsConstructor
public class InstructorAssignmentController {

	private static final String FORM_VIEW = "instructor/assignment-form";

	private final AssignmentService assignmentService;
	private final CourseService courseService;

	/** 과제 등록 폼. 사이드바 링크처럼 강좌 없이 들어오면 강좌 목록으로 안내한다. */
	@GetMapping("/new")
	public String newForm(@AuthenticationPrincipal CustomUserDetails user,
			@RequestParam(required = false) Long courseId,
			Model model, RedirectAttributes redirectAttributes) {
		if (courseId == null) {
			redirectAttributes.addFlashAttribute("message", "과제를 등록할 강좌를 먼저 선택해 주세요.");
			return "redirect:/instructor/courses";
		}

		CourseDetailResponse course = courseService.findMyCourseDetail(user.getId(), courseId);
		model.addAttribute("course", course);
		model.addAttribute("assignmentRequest", new AssignmentRequest());
		model.addAttribute("formAction", "/instructor/assignments/new?courseId=" + courseId);
		model.addAttribute("editing", false);
		return FORM_VIEW;
	}

	@PostMapping("/new")
	public String create(@AuthenticationPrincipal CustomUserDetails user,
			@RequestParam Long courseId,
			@Valid @ModelAttribute AssignmentRequest assignmentRequest,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		CourseDetailResponse course = courseService.findMyCourseDetail(user.getId(), courseId);

		if (!bindingResult.hasErrors()) {
			try {
				assignmentService.create(user.getId(), courseId, assignmentRequest);
				redirectAttributes.addFlashAttribute("message", "과제가 등록되었습니다.");
				return "redirect:/instructor/courses/" + courseId;
			} catch (InvalidAssignmentPeriodException e) {
				bindingResult.rejectValue("endAt", "period.invalid", e.getMessage());
			} catch (InvalidFileException e) {
				bindingResult.rejectValue("file", "file.invalid", e.getMessage());
			}
		}

		model.addAttribute("course", course);
		model.addAttribute("formAction", "/instructor/assignments/new?courseId=" + courseId);
		model.addAttribute("editing", false);
		return FORM_VIEW;
	}

	@GetMapping("/{id}/edit")
	public String editForm(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id, Model model) {
		AssignmentResponse assignment = assignmentService.findMyAssignment(user.getId(), id);

		model.addAttribute("course", courseService.findMyCourseDetail(user.getId(), assignment.courseId()));
		model.addAttribute("assignmentRequest", AssignmentRequest.from(assignment));
		model.addAttribute("formAction", "/instructor/assignments/" + id + "/edit");
		model.addAttribute("editing", true);
		model.addAttribute("assignmentId", id);
		model.addAttribute("currentFileName", assignment.originalFilename());
		return FORM_VIEW;
	}

	@PostMapping("/{id}/edit")
	public String update(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id,
			@Valid @ModelAttribute AssignmentRequest assignmentRequest,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		AssignmentResponse assignment = assignmentService.findMyAssignment(user.getId(), id);

		if (!bindingResult.hasErrors()) {
			try {
				assignmentService.update(user.getId(), id, assignmentRequest);
				redirectAttributes.addFlashAttribute("message", "과제가 수정되었습니다.");
				return "redirect:/instructor/courses/" + assignment.courseId();
			} catch (InvalidAssignmentPeriodException e) {
				bindingResult.rejectValue("endAt", "period.invalid", e.getMessage());
			} catch (InvalidFileException e) {
				bindingResult.rejectValue("file", "file.invalid", e.getMessage());
			}
		}

		model.addAttribute("course", courseService.findMyCourseDetail(user.getId(), assignment.courseId()));
		model.addAttribute("formAction", "/instructor/assignments/" + id + "/edit");
		model.addAttribute("editing", true);
		model.addAttribute("assignmentId", id);
		model.addAttribute("currentFileName", assignment.originalFilename());
		return FORM_VIEW;
	}

	@PostMapping("/{id}/delete")
	public String delete(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id, RedirectAttributes redirectAttributes) {
		AssignmentResponse assignment = assignmentService.findMyAssignment(user.getId(), id);

		try {
			assignmentService.delete(user.getId(), id);
			redirectAttributes.addFlashAttribute("message", "과제가 삭제되었습니다.");
		} catch (AssignmentHasSubmissionsException e) {
			redirectAttributes.addFlashAttribute("error", "제출물이 있어 삭제 불가");
		}
		return "redirect:/instructor/courses/" + assignment.courseId();
	}
}
