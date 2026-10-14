package kr.ac.ync.midterm.submission.service;

import kr.ac.ync.midterm.submission.dto.request.ObjectionRequest;
import kr.ac.ync.midterm.submission.dto.request.ObjectionResponseRequest;

/**
 * 이의 신청 (X29): 학생이 채점 결과에 이의를 남기고, 강사가 답변하며 필요하면 점수를 고친다.
 */
public interface ObjectionService {

	/**
	 * 이의 신청. 수강 등록한 학생의 본인 제출만(B2), 채점 완료 후에만, 제출당 1회만 가능하다.
	 * 제출이 없으면 404, 채점 전 · 이미 신청함은 409.
	 */
	void raise(Long studentId, Long assignmentId, ObjectionRequest request);

	/**
	 * 이의 답변. 본인 강좌의 제출에만(B5), 신청이 있고 아직 답변하지 않은 경우에만 가능하다.
	 * 점수를 함께 보내면 재채점되며 0 이상 배점 이하여야 한다(B6).
	 */
	void respond(Long instructorId, Long submissionId, ObjectionResponseRequest request);
}
