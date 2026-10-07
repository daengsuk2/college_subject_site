package kr.ac.ync.midterm.assignment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentResponse;
import kr.ac.ync.midterm.assignment.exception.AssignmentHasSubmissionsException;
import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.exception.InvalidAssignmentPeriodException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.exception.CourseNotFoundException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.global.storage.FileStorage;
import kr.ac.ync.midterm.global.storage.StoredFile;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentServiceImpl implements AssignmentService {

	private final AssignmentRepository assignmentRepository;
	private final CourseRepository courseRepository;
	private final SubmissionRepository submissionRepository;
	private final FileStorage fileStorage;

	/**
	 * 과제 등록 (I3). 본인 강좌에만 등록할 수 있다 (B5), 종료일시는 시작일시보다 뒤여야 한다 (B7).
	 * 첨부파일(I3-b)은 권한과 기간 검증을 통과한 뒤에만 저장한다.
	 */
	@Override
	@Transactional
	public AssignmentResponse create(Long instructorId, Long courseId, AssignmentRequest request) {
		Course course = findOwnedCourse(instructorId, courseId);
		validatePeriod(request.getStartAt(), request.getEndAt());
		StoredFile stored = fileStorage.store(request.getFile());

		try {
			Assignment assignment = Assignment.builder()
					.course(course)
					.title(request.getTitle().trim())
					.content(request.getContent().trim())
					.startAt(request.getStartAt())
					.endAt(request.getEndAt())
					.maxScore(request.getMaxScore())
					.build();
			if (stored != null) {
				assignment.attachFile(stored.storedName(), stored.originalName());
			}
			return AssignmentResponse.from(assignmentRepository.save(assignment));
		} catch (RuntimeException e) {
			// 저장에 실패하면 방금 올린 파일이 남지 않게 지운다
			if (stored != null) {
				fileStorage.deleteQuietly(stored.storedName());
			}
			throw e;
		}
	}

	/**
	 * 강좌의 과제 목록 (B5: 본인 강좌만).
	 */
	@Override
	public List<AssignmentResponse> findMyAssignments(Long instructorId, Long courseId) {
		findOwnedCourse(instructorId, courseId);
		return assignmentRepository.findByCourseIdOrderByStartAtDesc(courseId).stream()
				.map(AssignmentResponse::from)
				.toList();
	}

	@Override
	public AssignmentResponse findMyAssignment(Long instructorId, Long assignmentId) {
		return AssignmentResponse.from(findOwnedAssignment(instructorId, assignmentId));
	}

	/**
	 * 과제 수정 (I3). 수정할 때도 B5, B7을 검사한다.
	 * 새 파일을 올리면 첨부가 교체되고 기존 파일은 삭제된다. 올리지 않으면 기존 첨부가 유지된다 (I3-b).
	 */
	@Override
	@Transactional
	public AssignmentResponse update(Long instructorId, Long assignmentId, AssignmentRequest request) {
		Assignment assignment = findOwnedAssignment(instructorId, assignmentId);
		validatePeriod(request.getStartAt(), request.getEndAt());
		StoredFile stored = fileStorage.store(request.getFile());
		String oldStoredName = assignment.getFilePath();

		try {
			assignment.update(
					request.getTitle().trim(),
					request.getContent().trim(),
					request.getStartAt(),
					request.getEndAt(),
					request.getMaxScore());
			if (stored != null) {
				assignment.attachFile(stored.storedName(), stored.originalName());
			}
		} catch (RuntimeException e) {
			if (stored != null) {
				fileStorage.deleteQuietly(stored.storedName());
			}
			throw e;
		}

		if (stored != null) {
			fileStorage.deleteQuietly(oldStoredName);
		}
		return AssignmentResponse.from(assignment);
	}

	/**
	 * 과제 삭제 (I3). 제출이 1건이라도 있으면 삭제할 수 없다 (B8). 삭제하면 첨부 파일도 지운다.
	 */
	@Override
	@Transactional
	public void delete(Long instructorId, Long assignmentId) {
		Assignment assignment = findOwnedAssignment(instructorId, assignmentId);

		if (submissionRepository.existsByAssignmentId(assignmentId)) {
			throw new AssignmentHasSubmissionsException();
		}

		String storedName = assignment.getFilePath();
		assignmentRepository.delete(assignment);
		fileStorage.deleteQuietly(storedName);
	}

	// 없는 강좌는 404, 다른 강사의 강좌는 403 (B5)
	private Course findOwnedCourse(Long instructorId, Long courseId) {
		Course course = courseRepository.findById(courseId)
				.orElseThrow(CourseNotFoundException::new);
		if (!course.getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}
		return course;
	}

	// 없는 과제는 404, 다른 강사 강좌의 과제는 403 (B5)
	private Assignment findOwnedAssignment(Long instructorId, Long assignmentId) {
		Assignment assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(AssignmentNotFoundException::new);
		if (!assignment.getCourse().getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}
		return assignment;
	}

	// B7: 종료일시는 시작일시보다 뒤여야 한다
	private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
		if (!endAt.isAfter(startAt)) {
			throw new InvalidAssignmentPeriodException();
		}
	}
}
