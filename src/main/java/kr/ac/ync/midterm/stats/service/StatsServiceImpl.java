package kr.ac.ync.midterm.stats.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.exception.CourseNotFoundException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.stats.dto.response.AssignmentSubmissionAggregate;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.AssignmentStatRow;
import kr.ac.ync.midterm.stats.dto.response.CourseStatsResponse.MissingStudentRow;
import kr.ac.ync.midterm.stats.dto.response.StatsPageResponse;
import kr.ac.ync.midterm.stats.dto.response.StatsPageResponse.CourseOption;
import kr.ac.ync.midterm.stats.dto.response.StudentSubmissionCount;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

	private final CourseRepository courseRepository;
	private final AssignmentRepository assignmentRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final SubmissionRepository submissionRepository;
	private final Clock clock;

	/**
	 * 강좌별 통계 (I6). 본인 강좌만 볼 수 있다 (B5).
	 * 숫자는 집계 쿼리로 구한다: 과제별 제출 · 평균 (group by 1회), 수강생별 제출 수 (1회).
	 */
	@Override
	public StatsPageResponse findStats(Long instructorId, Long courseId) {
		List<Course> myCourses = courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
		List<CourseOption> options = myCourses.stream().map(c -> new CourseOption(c.getId(), c.getName())).toList();

		Course selected;
		if (courseId == null) {
			if (myCourses.isEmpty()) {
				return new StatsPageResponse(options, null);
			}
			selected = myCourses.get(0);
		} else {
			selected = courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
			if (!selected.getInstructor().getId().equals(instructorId)) {
				throw new ForbiddenException();
			}
		}

		return new StatsPageResponse(options, buildStats(selected, LocalDateTime.now(clock)));
	}

	private CourseStatsResponse buildStats(Course course, LocalDateTime now) {
		Long courseId = course.getId();
		long enrolled = enrollmentRepository.countByCourseId(courseId);

		// 시작된 과제만 (예정 과제는 제출할 수 없으므로 제외), 시작이 이른 순
		List<Assignment> started = assignmentRepository.findByCourseIdOrderByStartAtDesc(courseId).stream()
				.filter(a -> !a.getStartAt().isAfter(now))
				.sorted(Comparator.comparing(Assignment::getStartAt).thenComparing(Assignment::getId))
				.toList();

		Map<Long, AssignmentSubmissionAggregate> aggregates = submissionRepository
				.aggregateByAssignment(courseId, now).stream()
				.collect(Collectors.toMap(AssignmentSubmissionAggregate::assignmentId, Function.identity()));

		List<AssignmentStatRow> rows = started.stream().map(a -> {
			AssignmentSubmissionAggregate agg = aggregates.get(a.getId());
			return new AssignmentStatRow(a.getId(), a.getTitle(), a.getMaxScore(),
					agg == null ? 0 : agg.submittedCount(), enrolled,
					agg == null ? 0 : agg.gradedCount(), agg == null ? null : agg.averageScore());
		}).toList();

		long submitted = rows.stream().mapToLong(AssignmentStatRow::submittedCount).sum();
		long possible = enrolled * started.size();

		List<MissingStudentRow> missing = enrollmentRepository.countSubmissionsPerStudent(courseId, now).stream()
				.map(s -> toMissingRow(s, started.size()))
				.filter(r -> r.missingCount() > 0)
				.sorted(Comparator.comparingLong(MissingStudentRow::missingCount).reversed()
						.thenComparing(MissingStudentRow::studentName))
				.toList();

		return new CourseStatsResponse(courseId, course.getName(), enrolled, started.size(), submitted, possible,
				rows, missing);
	}

	private MissingStudentRow toMissingRow(StudentSubmissionCount count, int startedCount) {
		return new MissingStudentRow(count.studentNo(), count.studentName(), startedCount - count.submittedCount());
	}
}
