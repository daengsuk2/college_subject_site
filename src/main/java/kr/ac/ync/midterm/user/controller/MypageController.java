package kr.ac.ync.midterm.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.ac.ync.midterm.global.security.AuthenticationRefresher;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.user.dto.request.NameChangeRequest;
import kr.ac.ync.midterm.user.dto.request.PasswordChangeRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;
import kr.ac.ync.midterm.user.exception.PasswordMismatchException;
import kr.ac.ync.midterm.user.exception.SamePasswordException;
import kr.ac.ync.midterm.user.exception.WrongCurrentPasswordException;
import kr.ac.ync.midterm.user.service.MypageService;
import lombok.RequiredArgsConstructor;

/**
 * 마이페이지 (X1). 강사와 학생이 함께 쓰며, 항상 로그인한 본인의 정보만 보고 바꾼다.
 * (SecurityConfig의 anyRequest().authenticated()로 비로그인은 로그인 화면으로 이동한다.)
 */
@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MypageController {

	private static final String VIEW = "mypage";

	private final MypageService mypageService;
	private final AuthenticationRefresher authenticationRefresher;

	@GetMapping
	public String page(@AuthenticationPrincipal CustomUserDetails user, Model model) {
		UserResponse me = mypageService.findMe(user.getId());
		model.addAttribute("me", me);
		NameChangeRequest nameChangeRequest = new NameChangeRequest();
		nameChangeRequest.setName(me.name());
		model.addAttribute("nameChangeRequest", nameChangeRequest);
		model.addAttribute("passwordChangeRequest", new PasswordChangeRequest());
		return VIEW;
	}

	@PostMapping("/name")
	public String changeName(@AuthenticationPrincipal CustomUserDetails user,
			@Valid @ModelAttribute NameChangeRequest nameChangeRequest, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes,
			HttpServletRequest request, HttpServletResponse response) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("me", mypageService.findMe(user.getId()));
			model.addAttribute("passwordChangeRequest", new PasswordChangeRequest());
			return VIEW;
		}

		mypageService.changeName(user.getId(), nameChangeRequest.getName());
		authenticationRefresher.refresh(user.getId(), request, response);
		redirectAttributes.addFlashAttribute("message", "이름을 변경했습니다.");
		return "redirect:/mypage";
	}

	@PostMapping("/password")
	public String changePassword(@AuthenticationPrincipal CustomUserDetails user,
			@Valid @ModelAttribute PasswordChangeRequest passwordChangeRequest, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes,
			HttpServletRequest request, HttpServletResponse response) {
		if (!bindingResult.hasErrors()) {
			try {
				mypageService.changePassword(user.getId(), passwordChangeRequest);
				authenticationRefresher.refresh(user.getId(), request, response);
				redirectAttributes.addFlashAttribute("message", "비밀번호를 변경했습니다.");
				return "redirect:/mypage";
			} catch (WrongCurrentPasswordException e) {
				bindingResult.rejectValue("currentPassword", "password.wrong", e.getMessage());
			} catch (PasswordMismatchException e) {
				bindingResult.rejectValue("newPasswordConfirm", "password.mismatch", e.getMessage());
			} catch (SamePasswordException e) {
				bindingResult.rejectValue("newPassword", "password.same", e.getMessage());
			}
		}

		UserResponse me = mypageService.findMe(user.getId());
		model.addAttribute("me", me);
		NameChangeRequest nameChangeRequest = new NameChangeRequest();
		nameChangeRequest.setName(me.name());
		model.addAttribute("nameChangeRequest", nameChangeRequest);
		return VIEW;
	}
}
