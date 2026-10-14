package kr.ac.ync.midterm.submission.service;

import kr.ac.ync.midterm.submission.dto.response.SubmissionFileResponse;
import kr.ac.ync.midterm.user.domain.Role;

public interface SubmissionFileService {

	/** 제출 파일 다운로드. 제출한 학생 본인(B2)과 해당 강좌의 강사(B5)만 가능하다. */
	SubmissionFileResponse getFile(Long userId, Role role, Long submissionId);
}
