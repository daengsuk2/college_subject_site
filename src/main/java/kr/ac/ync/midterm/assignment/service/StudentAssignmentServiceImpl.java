package kr.ac.ync.midterm.assignment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;
import kr.ac.ync.midterm.assignment.dto.response.StudentAssignmentResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAssignmentServiceImpl implements StudentAssignmentService {

	// 진행중 → 예정 → 마감 순, 같은 상태끼리는 종료일시가 이른 순
	private static final Comparator<StudentAssignmentResponse> LIST_ORDER = Comparator
			.comparingInt((StudentAssignmentResponse r) -> r.status().getSortOrder())
			.thenComparing(StudentAssignmentResponse::endAt);

	private final AssignmentRepository assignmentRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final Clock clock;

	/**
	 * 내 과제 목록 (S2). 수강 등록한 강좌의 과제만 조회한다 (B2).
	 */
	@Override
	public List<StudentAssignmentResponse> findMyAssignments(Long studentId) {
		LocalDateTime now = LocalDateTime.now(clock);

		return assignmentRepository.findAllByEnrolledStudent(studentId).stream()
				.map(assignment -> toResponse(assignment, now))
				.sorted(LIST_ORDER)
				.toList();
	}

	/**
	 * 과제 상세 (S2). 없는 과제는 404, 수강 등록하지 않은 강좌의 과제는 403 (B2).
	 */
	@Override
	public StudentAssignmentResponse findMyAssignment(Long studentId, Long assignmentId) {
		Assignment assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(AssignmentNotFoundException::new);

		if (!enrollmentRepository.existsByCourseIdAndStudentId(assignment.getCourse().getId(), studentId)) {
			throw new ForbiddenException();
		}

		return toResponse(assignment, LocalDateTime.now(clock));
	}

	private StudentAssignmentResponse toResponse(Assignment assignment, LocalDateTime now) {
		AssignmentStatus status = AssignmentStatus.of(assignment.getStartAt(), assignment.getEndAt(), now);
		return StudentAssignmentResponse.of(assignment, status);
	}
}
