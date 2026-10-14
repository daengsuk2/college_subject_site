package kr.ac.ync.midterm.home.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import kr.ac.ync.midterm.dashboard.service.DashboardService;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.user.domain.Role;
import lombok.RequiredArgsConstructor;

/**
 * 대시보드 (/). 로그인한 역할에 맞는 데이터만 화면에 전달한다: 학생은 내 통계(S5), 강사는 내 강좌 요약.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

	private final DashboardService dashboardService;

	@GetMapping("/")
	public String home(@AuthenticationPrincipal CustomUserDetails user, Model model) {
		if (user.getRole() == Role.STUDENT) {
			model.addAttribute("student", dashboardService.findStudentDashboard(user.getId()));
		} else {
			model.addAttribute("instructor", dashboardService.findInstructorDashboard(user.getId()));
		}
		return "home";
	}
}
