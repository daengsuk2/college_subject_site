package kr.ac.ync.midterm.export.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import kr.ac.ync.midterm.course.dto.response.CourseDetailResponse;
import kr.ac.ync.midterm.course.dto.response.EnrolledStudentResponse;
import kr.ac.ync.midterm.course.service.CourseService;
import kr.ac.ync.midterm.global.csv.CsvBuilder;
import kr.ac.ync.midterm.global.csv.CsvResponses;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.AssignmentStatRow;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.MissingStudentRow;
import kr.ac.ync.midterm.stats.service.StatsService;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse.NotSubmittedRow;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse.SubmittedRow;
import kr.ac.ync.midterm.submission.service.SubmissionStatusService;
import lombok.RequiredArgsConstructor;

/**
 * 강사의 표 내려받기 (X8 수강생 목록, X32 제출 현황 · 통계). /instructor/** 는 SecurityConfig에서 INSTRUCTOR만 접근하도록
 * 막혀 있고, 본인 강좌인지(B5)는 화면과 같은 서비스에서 검사한다. 화면에 보이는 숫자와 같은 조회 결과를 쓰므로
 * 화면과 파일의 값이 같다. GET이며 데이터를 바꾸지 않는다. 정렬 · 검색 상태와 무관하게 서버 기본 순서의 전체 데이터를 내려 준다.
 */
@Controller
@RequiredArgsConstructor
public class InstructorExportController {

	private final CourseService courseService;
	private final SubmissionStatusService submissionStatusService;
	private final StatsService statsService;

	/** 수강생 목록 (등록 순서) */
	@GetMapping("/instructor/courses/{id}/students.csv")
	public ResponseEntity<byte[]> students(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
		CourseDetailResponse course = courseService.findMyCourseDetail(user.getId(), id);

		CsvBuilder csv = CsvBuilder.withHeader("학번", "이름", "이메일", "수강 등록일");
		for (EnrolledStudentResponse s : course.students()) {
			csv.row(s.studentNo(), s.name(), s.email(), s.joinedAt());
		}
		return CsvResponses.download("수강생_" + course.name(), csv);
	}

	/** 과제별 제출 현황: 제출한 학생과 제출하지 않은 학생을 한 파일에 담는다 (엑셀에서 제출 여부로 거르기 쉽게) */
	@GetMapping("/instructor/assignments/{id}/submissions.csv")
	public ResponseEntity<byte[]> submissions(@AuthenticationPrincipal CustomUserDetails user,
			@PathVariable Long id) {
		SubmissionStatusResponse status = submissionStatusService.findStatus(user.getId(), id);

		CsvBuilder csv = CsvBuilder.withHeader("학번", "이름", "제출 여부", "제출 시각", "상태", "점수", "첨부 파일명");
		for (SubmittedRow row : status.submitted()) {
			csv.row(row.studentNo(), row.studentName(), "제출", row.submittedAt(),
					row.isGraded() ? "채점 완료" : "제출함", row.isGraded() ? row.score() : null,
					row.originalFilename());
		}
		for (NotSubmittedRow row : status.notSubmitted()) {
			csv.row(row.studentNo(), row.studentName(), "미제출", null, null, null, null);
		}
		return CsvResponses.download("제출현황_" + status.courseName() + "_" + status.assignmentTitle(), csv);
	}

	/** 통계: 과제별 현황 */
	@GetMapping("/instructor/stats/assignments.csv")
	public ResponseEntity<byte[]> statsAssignments(@AuthenticationPrincipal CustomUserDetails user,
			@RequestParam Long courseId) {
		CourseStatsResponse stats = statsService.findStats(user.getId(), courseId).stats();

		CsvBuilder csv = CsvBuilder.withHeader("과제", "제출 수", "수강생 수", "제출률(%)", "채점 대기", "평균 점수", "배점");
		for (AssignmentStatRow row : stats.assignments()) {
			csv.row(row.title(), row.submittedCount(), row.enrolledCount(), row.submissionRate(), row.pendingCount(),
					row.averageScore(), row.maxScore());
		}
		return CsvResponses.download("통계_과제별_" + stats.courseName(), csv);
	}

	/** 통계: 미제출이 있는 학생 */
	@GetMapping("/instructor/stats/missing.csv")
	public ResponseEntity<byte[]> statsMissing(@AuthenticationPrincipal CustomUserDetails user,
			@RequestParam Long courseId) {
		CourseStatsResponse stats = statsService.findStats(user.getId(), courseId).stats();

		CsvBuilder csv = CsvBuilder.withHeader("학번", "이름", "미제출 과제 수");
		for (MissingStudentRow row : stats.missingStudents()) {
			csv.row(row.studentNo(), row.studentName(), row.missingCount());
		}
		return CsvResponses.download("통계_미제출_" + stats.courseName(), csv);
	}
}
