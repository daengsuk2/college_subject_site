package kr.ac.ync.midterm.assignment.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentFileResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.exception.AttachmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.user.domain.Role;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentFileServiceImpl implements AssignmentFileService {

	private final AssignmentRepository assignmentRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final FileStorage fileStorage;

	/**
	 * 없는 과제는 404. 권한이 없으면 403이며, 권한 확인을 먼저 해서 첨부 여부가 노출되지 않게 한다.
	 */
	@Override
	public AssignmentFileResponse getFile(Long userId, Role role, Long assignmentId) {
		Assignment assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(AssignmentNotFoundException::new);

		checkAccess(userId, role, assignment);

		if (!assignment.hasFile()) {
			throw new AttachmentNotFoundException();
		}
		Resource resource = fileStorage.load(assignment.getFilePath());
		if (resource == null) {
			throw new AttachmentNotFoundException();
		}
		return new AssignmentFileResponse(resource, assignment.getOriginalFilename());
	}

	// 강사는 본인 강좌의 과제만(B5), 학생은 수강 등록한 강좌의 과제만(B2)
	private void checkAccess(Long userId, Role role, Assignment assignment) {
		Long courseId = assignment.getCourse().getId();
		boolean allowed = switch (role) {
			case INSTRUCTOR -> assignment.getCourse().getInstructor().getId().equals(userId);
			case STUDENT -> enrollmentRepository.existsByCourseIdAndStudentId(courseId, userId);
		};
		if (!allowed) {
			throw new ForbiddenException();
		}
	}
}
