package kr.ac.ync.midterm.submission.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.request.GradeRequest;
import kr.ac.ync.midterm.submission.dto.response.GradeFormResponse;
import kr.ac.ync.midterm.submission.exception.InvalidScoreException;
import kr.ac.ync.midterm.submission.exception.SubmissionNotFoundException;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradingServiceImpl implements GradingService {

	private final SubmissionRepository submissionRepository;
	private final Clock clock;

	@Override
	public GradeFormResponse findForGrading(Long instructorId, Long submissionId) {
		return GradeFormResponse.of(findOwnedSubmission(instructorId, submissionId));
	}

	/**
	 * 채점 (I5). 본인 강좌의 제출만 채점할 수 있고(B5), 점수는 0 이상 배점 이하여야 한다(B6).
	 * 저장하면 채점 완료(GRADED)가 되어 학생은 더 이상 재제출할 수 없다(B4).
	 */
	@Override
	@Transactional
	public void grade(Long instructorId, Long submissionId, GradeRequest request) {
		Submission submission = findOwnedSubmission(instructorId, submissionId);

		int maxScore = submission.getAssignment().getMaxScore();
		Integer score = request.getScore();
		if (score == null || score < 0 || score > maxScore) {
			throw new InvalidScoreException(maxScore);
		}

		String feedback = request.getFeedback() == null || request.getFeedback().isBlank()
				? null
				: request.getFeedback().trim();
		submission.grade(score, feedback, LocalDateTime.now(clock));
	}

	// 없는 제출은 404, 다른 강사의 강좌 제출은 403 (B5)
	private Submission findOwnedSubmission(Long instructorId, Long submissionId) {
		Submission submission = submissionRepository.findById(submissionId)
				.orElseThrow(SubmissionNotFoundException::new);
		if (!submission.getAssignment().getCourse().getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}
		return submission;
	}
}
