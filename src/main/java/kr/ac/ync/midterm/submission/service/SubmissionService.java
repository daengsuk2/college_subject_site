package kr.ac.ync.midterm.submission.service;

import java.util.List;
import java.util.Optional;

import kr.ac.ync.midterm.submission.dto.request.SubmissionRequest;
import kr.ac.ync.midterm.submission.dto.response.MySubmissionResponse;
import kr.ac.ync.midterm.submission.dto.response.SubmissionResponse;

public interface SubmissionService {

	/** 내 제출 조회. 수강 등록하지 않은 강좌의 과제는 403 (B2). 제출이 없으면 빈 값. */
	Optional<SubmissionResponse> findMySubmission(Long studentId, Long assignmentId);

	/** 내 제출 목록 (최근 제출순). 본인의 제출만, 수강 중인 강좌의 것만 나온다. */
	List<MySubmissionResponse> findMySubmissions(Long studentId);

	/**
	 * 제출 파일 삭제 (X23). 수강 등록한 학생의 본인 제출만(B2), 제출 기간 안에서만(B1), 채점 완료 전에만(B4) 가능하다.
	 * 제출 내용과 제출 시각은 바뀌지 않는다. 제출이 없으면 404, 첨부가 없으면 404.
	 */
	void removeFile(Long studentId, Long assignmentId);

	/** 제출 또는 재제출. B1 · B2 · B3 · B4를 검사한다. */
	void submit(Long studentId, Long assignmentId, SubmissionRequest request);
}
