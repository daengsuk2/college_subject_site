package kr.ac.ync.midterm.submission.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.dto.response.ObjectionAlerts;
import kr.ac.ync.midterm.submission.service.ObjectionService;
import kr.ac.ync.midterm.user.domain.Role;
import lombok.RequiredArgsConstructor;

/**
 * 모든 화면의 상단바 알림용 데이터 (X29). 로그인한 강사에게만 답변을 기다리는 이의 신청을 전달하고,
 * 학생 · 비로그인에게는 아무것도 조회하지 않는다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ObjectionAlertAdvice {

	private final ObjectionService objectionService;

	@ModelAttribute("objectionAlerts")
	public ObjectionAlerts objectionAlerts(@AuthenticationPrincipal CustomUserDetails user) {
		if (user == null || user.getRole() != Role.INSTRUCTOR) {
			return null;
		}
		return objectionService.findPendingAlerts(user.getId());
	}
}
