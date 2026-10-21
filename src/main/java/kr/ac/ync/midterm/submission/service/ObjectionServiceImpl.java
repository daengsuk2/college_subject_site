package kr.ac.ync.midterm.submission.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.assignment.exception.AssignmentNotFoundException;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.ObjectionRequest;
import kr.ac.ync.midterm.submission.dto.request.ObjectionResponseRequest;
import kr.ac.ync.midterm.submission.dto.response.ObjectionAlertResponse;
import kr.ac.ync.midterm.submission.dto.response.ObjectionAlerts;
import kr.ac.ync.midterm.submission.exception.InvalidScoreException;
import kr.ac.ync.midterm.submission.exception.ObjectionException;
import kr.ac.ync.midterm.submission.exception.SubmissionNotFoundException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ObjectionServiceImpl implements ObjectionService {

	private final SubmissionRepository submissionRepository;
	private final AssignmentRepository assignmentRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final Clock clock;

	/**
	 * 이의 신청. 순서: 과제 존재(404) → 수강 등록(B2, 403) → 본인 제출(404) → 채점 완료 → 신청 이력 없음.
	 */
	@Override
	@Transactional
	public void raise(Long studentId, Long assignmentId, ObjectionRequest request) {
		Long courseId = assignmentRepository.findById(assignmentId)
				.orElseThrow(AssignmentNotFoundException::new).getCourse().getId();
		if (!enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
			throw new ForbiddenException();
		}

		Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
				.orElseThrow(SubmissionNotFoundException::new);
		if (!submission.isGraded()) {
			throw new ObjectionException("채점 완료된 제출에만 이의를 신청할 수 있습니다.");
		}
		if (submission.hasObjection()) {
			throw new ObjectionException("이미 이의를 신청했습니다.");
		}

		String content = request.getContent() == null ? "" : request.getContent().trim();
		if (content.isEmpty() || content.length() > ObjectionRequest.MAX_LENGTH) {
			throw new ObjectionException("이의 내용은 1자 이상 500자 이하로 입력해 주세요.");
		}
		submission.raiseObjection(content, LocalDateTime.now(clock));
	}

	/** 알림 목록에 보여 줄 최대 건수 */
	private static final int ALERT_LIMIT = 5;

	@Override
	public ObjectionAlerts findPendingAlerts(Long instructorId) {
		long count = submissionRepository.countPendingObjections(instructorId);
		if (count == 0) {
			return new ObjectionAlerts(0, List.of());
		}
		List<ObjectionAlertResponse> items = submissionRepository
				.findPendingObjections(instructorId, PageRequest.of(0, ALERT_LIMIT)).stream()
				.map(ObjectionAlertResponse::of).toList();
		return new ObjectionAlerts(count, items);
	}

	/**
	 * 이의 답변. 순서: 제출 존재(404) → 본인 강좌(B5, 403) → 신청 있음 → 답변 전 → 점수 범위(B6).
	 */
	@Override
	@Transactional
	public void respond(Long instructorId, Long submissionId, ObjectionResponseRequest request) {
		Submission submission = submissionRepository.findById(submissionId)
				.orElseThrow(SubmissionNotFoundException::new);
		if (!submission.getAssignment().getCourse().getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}
		if (!submission.hasObjection()) {
			throw new ObjectionException("이의 신청이 없는 제출입니다.");
		}
		if (submission.hasObjectionResponse()) {
			throw new ObjectionException("이미 답변한 이의 신청입니다.");
		}

		String response = request.getResponse() == null ? "" : request.getResponse().trim();
		if (response.isEmpty() || response.length() > ObjectionRequest.MAX_LENGTH) {
			throw new ObjectionException("답변은 1자 이상 500자 이하로 입력해 주세요.");
		}

		LocalDateTime now = LocalDateTime.now(clock);
		Integer score = request.getScore();
		if (score != null) {
			int maxScore = submission.getAssignment().getMaxScore();
			if (score < 0 || score > maxScore) {
				throw new InvalidScoreException(maxScore);
			}
			// 재채점: 점수만 바꾸고 피드백은 그대로 둔다
			submission.grade(score, submission.getFeedback(), now);
		}
		submission.answerObjection(response, now);
	}
}
