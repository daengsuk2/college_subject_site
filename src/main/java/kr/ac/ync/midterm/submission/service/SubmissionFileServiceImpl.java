package kr.ac.ync.midterm.submission.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.response.SubmissionFileResponse;
import kr.ac.ync.midterm.submission.exception.SubmissionFileNotFoundException;
import kr.ac.ync.midterm.submission.exception.SubmissionNotFoundException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.user.domain.Role;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionFileServiceImpl implements SubmissionFileService {

	private final SubmissionRepository submissionRepository;
	private final FileStorage fileStorage;

	/**
	 * 없는 제출은 404. 권한이 없으면 403이며, 권한 확인을 먼저 해서 첨부 여부가 노출되지 않게 한다.
	 */
	@Override
	public SubmissionFileResponse getFile(Long userId, Role role, Long submissionId) {
		Submission submission = submissionRepository.findById(submissionId)
				.orElseThrow(SubmissionNotFoundException::new);

		checkAccess(userId, role, submission);

		if (!submission.hasFile()) {
			throw new SubmissionFileNotFoundException();
		}
		Resource resource = fileStorage.load(submission.getFilePath());
		if (resource == null) {
			throw new SubmissionFileNotFoundException();
		}
		return new SubmissionFileResponse(resource, submission.getOriginalFilename());
	}

	// 학생은 본인 제출만(B2), 강사는 본인 강좌의 제출만(B5)
	private void checkAccess(Long userId, Role role, Submission submission) {
		boolean allowed = switch (role) {
			case STUDENT -> submission.getStudent().getId().equals(userId);
			case INSTRUCTOR -> submission.getAssignment().getCourse().getInstructor().getId().equals(userId);
		};
		if (!allowed) {
			throw new ForbiddenException();
		}
	}
}
