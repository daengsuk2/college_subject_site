package kr.ac.ync.midterm.submission.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse.NotSubmittedRow;
import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse.SubmittedRow;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionStatusServiceImpl implements SubmissionStatusService {

	private final AssignmentRepository assignmentRepository;
	private final SubmissionRepository submissionRepository;
	private final EnrollmentRepository enrollmentRepository;

	/**
	 * 제출 현황 (I4). 본인 강좌의 과제만 볼 수 있다 (B5).
	 * 미제출자는 수강생에서 제출자를 뺀 학생이며, 수강생이 아닌 학생의 제출은 포함하지 않는다.
	 */
	@Override
	public SubmissionStatusResponse findStatus(Long instructorId, Long assignmentId) {
		Assignment assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(AssignmentNotFoundException::new);
		if (!assignment.getCourse().getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}

		Long courseId = assignment.getCourse().getId();
		List<SubmittedRow> submitted = submissionRepository.findSubmittedByEnrolledStudents(assignmentId).stream()
				.map(SubmittedRow::of)
				.toList();
		List<NotSubmittedRow> notSubmitted = enrollmentRepository.findStudentsNotSubmitted(courseId, assignmentId)
				.stream()
				.map(NotSubmittedRow::of)
				.toList();

		return new SubmissionStatusResponse(assignment.getId(), courseId, assignment.getCourse().getName(),
				assignment.getTitle(), assignment.getMaxScore(), enrollmentRepository.countByCourseId(courseId),
				submitted, notSubmitted);
	}
}
