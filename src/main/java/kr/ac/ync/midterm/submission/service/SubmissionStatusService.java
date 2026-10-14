package kr.ac.ync.midterm.submission.service;

import kr.ac.ync.midterm.submission.dto.response.SubmissionStatusResponse;

public interface SubmissionStatusService {

	/** 과제별 제출 현황. 없는 과제는 404, 다른 강사의 과제는 403 (B5). */
	SubmissionStatusResponse findStatus(Long instructorId, Long assignmentId);
}
