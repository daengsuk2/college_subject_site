package kr.ac.ync.midterm.attachment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.domain.AssignmentStatus;
import kr.ac.ync.midterm.assignment.exception.AttachmentNotFoundException;
import kr.ac.ync.midterm.attachment.domain.Attachment;
import kr.ac.ync.midterm.attachment.dto.AttachmentFileResponse;
import kr.ac.ync.midterm.attachment.dto.AttachmentResponse;
import kr.ac.ync.midterm.attachment.exception.TooManyAttachmentsException;
import kr.ac.ync.midterm.attachment.repository.AttachmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.global.storage.StoredFile;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.exception.AlreadyGradedException;
import kr.ac.ync.midterm.submission.exception.SubmissionPeriodException;
import kr.ac.ync.midterm.user.domain.Role;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentServiceImpl implements AttachmentService {

	private final AttachmentRepository attachmentRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final FileStorage fileStorage;
	private final Clock clock;

	@Override
	public List<AttachmentResponse> findByAssignment(Long assignmentId) {
		return attachmentRepository.findByAssignmentIdOrderByIdAsc(assignmentId).stream()
				.map(AttachmentResponse::of).toList();
	}

	@Override
	public List<AttachmentResponse> findBySubmission(Long submissionId) {
		return attachmentRepository.findBySubmissionIdOrderByIdAsc(submissionId).stream()
				.map(AttachmentResponse::of).toList();
	}

	@Override
	@Transactional
	public void addToAssignment(Assignment assignment, List<MultipartFile> files) {
		List<MultipartFile> given = nonEmpty(files);
		if (given.isEmpty()) {
			return;
		}
		long existing = (assignment.hasFile() ? 1 : 0) + attachmentRepository.countByAssignmentId(assignment.getId());
		for (StoredFile stored : checkedStored(existing, given)) {
			attachmentRepository.save(Attachment.ofAssignment(assignment, stored.storedName(), stored.originalName()));
		}
	}

	@Override
	@Transactional
	public void addToSubmission(Submission submission, List<MultipartFile> files) {
		List<MultipartFile> given = nonEmpty(files);
		if (given.isEmpty()) {
			return;
		}
		long existing = (submission.hasFile() ? 1 : 0) + attachmentRepository.countBySubmissionId(submission.getId());
		for (StoredFile stored : checkedStored(existing, given)) {
			attachmentRepository.save(Attachment.ofSubmission(submission, stored.storedName(), stored.originalName()));
		}
	}

	@Override
	@Transactional
	public void removeAllOfAssignment(Long assignmentId) {
		List<Attachment> attachments = attachmentRepository.findByAssignmentIdOrderByIdAsc(assignmentId);
		attachmentRepository.deleteAll(attachments);
		attachments.forEach(a -> fileStorage.deleteAfterCommit(a.getStoredName()));
	}

	/**
	 * 없는 첨부는 404. 권한이 없으면 403이며, 권한 확인을 먼저 해서 첨부 여부가 노출되지 않게 한다.
	 */
	@Override
	public AttachmentFileResponse getFile(Long userId, Role role, Long attachmentId) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
				.orElseThrow(AttachmentNotFoundException::new);

		boolean allowed;
		if (attachment.getAssignment() != null) {
			Assignment assignment = attachment.getAssignment();
			allowed = switch (role) {
				case INSTRUCTOR -> assignment.getCourse().getInstructor().getId().equals(userId);
				case STUDENT -> enrollmentRepository.existsByCourseIdAndStudentId(assignment.getCourse().getId(), userId);
			};
		} else {
			Submission submission = attachment.getSubmission();
			allowed = switch (role) {
				case STUDENT -> submission.getStudent().getId().equals(userId);
				case INSTRUCTOR -> submission.getAssignment().getCourse().getInstructor().getId().equals(userId);
			};
		}
		if (!allowed) {
			throw new ForbiddenException();
		}

		Resource resource = fileStorage.load(attachment.getStoredName());
		if (resource == null) {
			throw new AttachmentNotFoundException();
		}
		return new AttachmentFileResponse(resource, attachment.getOriginalFilename());
	}

	@Override
	@Transactional
	public Long removeFromAssignment(Long instructorId, Long attachmentId) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
				.orElseThrow(AttachmentNotFoundException::new);
		Assignment assignment = attachment.getAssignment();
		if (assignment == null) {
			throw new AttachmentNotFoundException();
		}
		if (!assignment.getCourse().getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}

		attachmentRepository.delete(attachment);
		fileStorage.deleteAfterCommit(attachment.getStoredName());
		return assignment.getId();
	}

	/**
	 * 순서: 첨부 존재(404) → 이 과제의 제출 첨부(404) → 본인 제출 · 수강 등록(B2, 403) → 제출 기간(B1) → 채점 완료(B4).
	 */
	@Override
	@Transactional
	public void removeFromSubmission(Long studentId, Long assignmentId, Long attachmentId) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
				.orElseThrow(AttachmentNotFoundException::new);
		Submission submission = attachment.getSubmission();
		if (submission == null || !submission.getAssignment().getId().equals(assignmentId)) {
			throw new AttachmentNotFoundException();
		}
		Assignment assignment = submission.getAssignment();
		if (!submission.getStudent().getId().equals(studentId)
				|| !enrollmentRepository.existsByCourseIdAndStudentId(assignment.getCourse().getId(), studentId)) {
			throw new ForbiddenException();
		}
		LocalDateTime now = LocalDateTime.now(clock);
		if (AssignmentStatus.of(assignment.getStartAt(), assignment.getEndAt(), now) != AssignmentStatus.IN_PROGRESS) {
			throw new SubmissionPeriodException();
		}
		if (submission.isGraded()) {
			throw new AlreadyGradedException();
		}

		attachmentRepository.delete(attachment);
		fileStorage.deleteAfterCommit(attachment.getStoredName());
	}

	private List<MultipartFile> nonEmpty(List<MultipartFile> files) {
		if (files == null) {
			return List.of();
		}
		return files.stream().filter(f -> f != null && !f.isEmpty()).toList();
	}

	// 개수를 먼저 검사하고, 저장한 파일은 롤백되면 지워지도록 등록한다 (X22)
	private List<StoredFile> checkedStored(long existing, List<MultipartFile> files) {
		if (existing + files.size() > MAX_FILES) {
			throw new TooManyAttachmentsException(MAX_FILES);
		}
		return files.stream().map(file -> {
			StoredFile stored = fileStorage.store(file);
			fileStorage.deleteOnRollback(stored.storedName());
			return stored;
		}).toList();
	}
}
