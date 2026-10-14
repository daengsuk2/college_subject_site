package kr.ac.ync.midterm.submission.service;

import kr.ac.ync.midterm.submission.dto.request.GradeRequest;
import kr.ac.ync.midterm.submission.dto.response.GradeFormResponse;

public interface GradingService {

	/** 채점 화면 정보. 없는 제출은 404, 다른 강사의 강좌 제출은 403 (B5). */
	GradeFormResponse findForGrading(Long instructorId, Long submissionId);

	/** 채점 저장. B5, B6을 검사한다. 이미 채점한 제출은 다시 채점(수정)할 수 있다. */
	void grade(Long instructorId, Long submissionId, GradeRequest request);
}
