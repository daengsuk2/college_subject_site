package kr.ac.ync.midterm.submission.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.SubmissionPeriodException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

	private final SubmissionRepository submissionRepository;
	private final AssignmentRepository assignmentRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	@Override
	public Optional<SubmissionResponse> findMySubmission(Long studentId, Long assignmentId) {
		Assignment assignment = findEnrolledAssignment(studentId, assignmentId);
		return submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), studentId)
				.map(SubmissionResponse::of);
	}

	/**
	 * 제출 · 재제출 (S3). 순서: 과제 존재(404) → 수강 등록(B2, 403) → 제출 기간(B1) → 채점 완료(B4).
	 * 이미 제출이 있으면 새로 만들지 않고 덮어쓴다 (B3).
	 */
	@Override
	@Transactional
	public void submit(Long studentId, Long assignmentId, SubmissionRequest request) {
		Assignment assignment = findEnrolledAssignment(studentId, assignmentId);
		LocalDateTime now = LocalDateTime.now(clock);

		if (AssignmentStatus.of(assignment.getStartAt(), assignment.getEndAt(), now) != AssignmentStatus.IN_PROGRESS) {
			throw new SubmissionPeriodException();
		}

		Optional<Submission> existing = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
		if (existing.isPresent()) {
			Submission submission = existing.get();
			if (submission.isGraded()) {
				throw new AlreadyGradedException();
			}
			submission.resubmit(request.getContent(), now);
			return;
		}

		submissionRepository.save(Submission.builder()
				.assignment(assignment)
				.student(userRepository.getReferenceById(studentId))
				.content(request.getContent())
				.submittedAt(now)
				.build());
	}

	private Assignment findEnrolledAssignment(Long studentId, Long assignmentId) {
		Assignment assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(AssignmentNotFoundException::new);

		if (!enrollmentRepository.existsByCourseIdAndStudentId(assignment.getCourse().getId(), studentId)) {
			throw new ForbiddenException();
		}
		return assignment;
	}
}
