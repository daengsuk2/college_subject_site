package kr.ac.ync.midterm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

	@GetMapping("/")
	public String home() {
		return "home";
	}

	@GetMapping("/login")
	public String login() {
		return "auth/login";
	}

	@GetMapping("/signup")
	public String signup() {
		return "auth/signup";
	}
}
