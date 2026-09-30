package kr.ac.ync.midterm.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;
import kr.ac.ync.midterm.user.dto.request.SignupRequest;
import kr.ac.ync.midterm.user.exception.DuplicateEmailException;
import kr.ac.ync.midterm.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AuthController {

	private final UserService userService;

	@GetMapping("/login")
	public String login() {
		return "auth/login";
	}

	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("signupRequest", new SignupRequest());
		return "auth/signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute SignupRequest signupRequest, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return "auth/signup";
		}

		try {
			userService.signup(signupRequest);
		} catch (DuplicateEmailException e) {
			bindingResult.rejectValue("email", "duplicate", "이미 가입된 이메일입니다.");
			return "auth/signup";
		}
		return "redirect:/login?signup";
	}
}
