package kr.ac.ync.midterm.stats.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.AssignmentStatRow;
import kr.ac.ync.midterm.stats.dto.response.StatsPageResponse;
import kr.ac.ync.midterm.stats.dto.response.TrendChartData;
import kr.ac.ync.midterm.stats.service.StatsService;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 통계 대시보드 (I6). /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록 막혀 있고,
 * 본인 강좌인지(B5)는 서비스에서 검사한다. 숫자는 서버가 계산하고, 차트 그리기만 화면(Chart.js)이 한다.
 */
@Controller
@RequestMapping("/instructor/stats")
@RequiredArgsConstructor
public class InstructorStatsController {

	private final StatsService statsService;

	@GetMapping
	public String stats(@AuthenticationPrincipal CustomUserDetails user,
			@RequestParam(required = false) Long courseId, Model model) {
		StatsPageResponse page = statsService.findStats(user.getId(), courseId);
		model.addAttribute("courses", page.courses());
		model.addAttribute("stats", page.stats());

		CourseStatsResponse stats = page.stats();
		if (stats != null) {
			// 차트에 넘길 값. 제목은 템플릿의 자바스크립트 인라인 처리에서 이스케이프된다
			List<String> labels = new ArrayList<>();
			List<Double> averages = new ArrayList<>();
			List<Integer> maxScores = new ArrayList<>();
			for (AssignmentStatRow row : stats.assignments()) {
				labels.add(row.title());
				averages.add(row.averageScore());
				maxScores.add(row.maxScore());
			}
			model.addAttribute("chartLabels", labels);
			model.addAttribute("chartAverages", averages);
			model.addAttribute("chartMaxScores", maxScores);
			// 과제별 추이 선 차트 (X31): 과제 순서(시작일시 순)에 따른 제출률 · 평균 점수 비율, 서버가 계산한 값
			model.addAttribute("trend", TrendChartData.from(stats));
			model.addAttribute("chartSubmitted", stats.submittedCount());
			model.addAttribute("chartNotSubmitted", stats.notSubmittedCount());
		}
		return "instructor/stats";
	}
}
