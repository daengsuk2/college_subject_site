package kr.ac.ync.midterm.dashboard.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.dashboard.dto.response.InstructorDashboardResponse;
import kr.ac.ync.midterm.dashboard.dto.response.InstructorDashboardResponse.RecentSubmissionRow;
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse;
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse.DueSoonRow;
import kr.ac.ync.midterm.dashboard.dto.response.StudentSubmissionAggregate;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

	/** 종료까지 이 시간 이내인 미제출 과제를 마감 임박으로 본다 */
	static final Duration DUE_SOON_WINDOW = Duration.ofHours(24);

	/** 강사 대시보드에 보여 줄 최근 제출 수 */
	static final int RECENT_SUBMISSION_LIMIT = 10;

	private final EnrollmentRepository enrollmentRepository;
	private final AssignmentRepository assignmentRepository;
	private final SubmissionRepository submissionRepository;
	private final CourseRepository courseRepository;
	private final Clock clock;

	/**
	 * 학생 대시보드 (S5). 집계는 쿼리로 구한다: 시작된 과제 수, 제출 집계, 마감 임박 목록.
	 */
	@Override
	public StudentDashboardResponse findStudentDashboard(Long studentId) {
		LocalDateTime now = LocalDateTime.now(clock);

		long courseCount = enrollmentRepository.countByStudentId(studentId);
		long started = assignmentRepository.countStartedByEnrolledStudent(studentId, now);
		StudentSubmissionAggregate aggregate = submissionRepository.aggregateForStudent(studentId, now);

		long graded = aggregate.gradedCount();
		Double averageScore = graded == 0 ? null : aggregate.scoreSum() / (double) graded;
		Double averageRate = graded == 0 || aggregate.maxScoreSum() == null || aggregate.maxScoreSum() == 0
				? null
				: aggregate.scoreSum() * 100.0 / aggregate.maxScoreSum();

		List<DueSoonRow> dueSoon = assignmentRepository
				.findDueSoon(studentId, now, now.plus(DUE_SOON_WINDOW)).stream()
				.map(a -> new DueSoonRow(a.getId(), a.getTitle(), a.getCourse().getName(), a.getEndAt(),
						Duration.between(now, a.getEndAt()).toMinutes()))
				.toList();

		return new StudentDashboardResponse(courseCount, started, aggregate.submittedCount(), graded, averageScore,
				averageRate, dueSoon);
	}

	/**
	 * 강사 대시보드. 모든 집계가 본인 강좌로 한정된다 (B5).
	 */
	@Override
	public InstructorDashboardResponse findInstructorDashboard(Long instructorId) {
		LocalDateTime now = LocalDateTime.now(clock);

		List<RecentSubmissionRow> recent = submissionRepository
				.findRecentByInstructor(instructorId, PageRequest.of(0, RECENT_SUBMISSION_LIMIT)).stream()
				.map(RecentSubmissionRow::of)
				.toList();

		return new InstructorDashboardResponse(courseRepository.countByInstructorId(instructorId),
				assignmentRepository.countInProgressByInstructor(instructorId, now),
				submissionRepository.countPendingGrading(instructorId), recent);
	}
}
