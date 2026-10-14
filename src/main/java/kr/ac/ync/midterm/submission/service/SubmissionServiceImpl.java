package kr.ac.ync.midterm.submission.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.global.storage.StoredFile;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.MySubmissionResponse;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.AlreadySubmittedException;
import kr.ac.ync.midterm.submission.exception.SubmissionFileNotFoundException;
import kr.ac.ync.midterm.submission.exception.SubmissionNotFoundException;
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
	private final FileStorage fileStorage;
	private final Clock clock;

	@Override
	public Optional<SubmissionResponse> findMySubmission(Long studentId, Long assignmentId) {
		Assignment assignment = findEnrolledAssignment(studentId, assignmentId);
		return submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), studentId)
				.map(SubmissionResponse::of);
	}

	/**
	 * 내 제출 목록 (S4). 학생 id로 걸러 본인의 제출만 조회한다.
	 */
	@Override
	public List<MySubmissionResponse> findMySubmissions(Long studentId) {
		return submissionRepository.findMySubmissions(studentId).stream()
				.map(MySubmissionResponse::of)
				.toList();
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
		if (existing.isPresent() && existing.get().isGraded()) {
			throw new AlreadyGradedException();
		}

		// 첨부파일(S3-b)은 수강 · 기간 · 채점 완료 검사를 모두 통과한 뒤에만 저장한다
		StoredFile stored = fileStorage.store(request.getFile());
		// 커밋 단계에서 실패해 롤백되더라도 방금 올린 파일이 남지 않게 한다 (X22)
		fileStorage.deleteOnRollback(stored == null ? null : stored.storedName());
		String oldStoredName = existing.map(Submission::getFilePath).orElse(null);

		try {
			if (existing.isPresent()) {
				Submission submission = existing.get();
				submission.resubmit(request.getContent(), now);
				if (stored != null) {
					submission.attachFile(stored.storedName(), stored.originalName());
				}
			} else {
				Submission submission = Submission.builder()
						.assignment(assignment)
						.student(userRepository.getReferenceById(studentId))
						.content(request.getContent())
						.submittedAt(now)
						.build();
				if (stored != null) {
					submission.attachFile(stored.storedName(), stored.originalName());
				}
				submissionRepository.save(submission);
			}
		} catch (DataIntegrityViolationException e) {
			// 같은 학생의 제출이 동시에 들어와 UNIQUE(assignment_id, student_id)에 걸린 경우: 한 건은 이미 저장되어 있다
			if (stored != null) {
				fileStorage.deleteQuietly(stored.storedName());
			}
			throw new AlreadySubmittedException();
		} catch (RuntimeException e) {
			// 저장에 실패하면 방금 올린 파일이 남지 않게 지운다
			if (stored != null) {
				fileStorage.deleteQuietly(stored.storedName());
			}
			throw e;
		}

		// 새 파일로 교체했다면 이전 파일을 DB 변경이 확정된 뒤에 지운다. 커밋이 실패하면 이전 파일이 그대로 남아야 한다 (X22)
		if (stored != null) {
			fileStorage.deleteAfterCommit(oldStoredName);
		}
	}

	/**
	 * 제출 파일 삭제 (X23). 순서: 과제 존재(404) → 수강 등록(B2, 403) → 제출 기간(B1) → 내 제출 → 채점 완료(B4) → 첨부 여부.
	 * 서버의 파일은 DB 변경이 확정된 뒤에 지운다.
	 */
	@Override
	@Transactional
	public void removeFile(Long studentId, Long assignmentId) {
		Assignment assignment = findEnrolledAssignment(studentId, assignmentId);
		LocalDateTime now = LocalDateTime.now(clock);

		if (AssignmentStatus.of(assignment.getStartAt(), assignment.getEndAt(), now) != AssignmentStatus.IN_PROGRESS) {
			throw new SubmissionPeriodException();
		}

		Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
				.orElseThrow(SubmissionNotFoundException::new);
		if (submission.isGraded()) {
			throw new AlreadyGradedException();
		}
		if (!submission.hasFile()) {
			throw new SubmissionFileNotFoundException();
		}

		String storedName = submission.getFilePath();
		submission.detachFile();
		fileStorage.deleteAfterCommit(storedName);
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
