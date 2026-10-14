package kr.ac.ync.midterm.submission.service;

import java.util.Optional;

import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;

public interface SubmissionService {

	/** 내 제출 조회. 수강 등록하지 않은 강좌의 과제는 403 (B2). 제출이 없으면 빈 값. */
	Optional<SubmissionResponse> findMySubmission(Long studentId, Long assignmentId);

	/** 제출 또는 재제출. B1 · B2 · B3 · B4를 검사한다. */
	void submit(Long studentId, Long assignmentId, SubmissionRequest request);
}
