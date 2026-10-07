package kr.ac.ync.midterm.assignment.service;

import kr.ac.ync.midterm.assignment.dto.response.AssignmentFileResponse;
import kr.ac.ync.midterm.user.domain.Role;

public interface AssignmentFileService {

	/**
	 * 첨부파일 다운로드 (I3-b). 해당 강좌의 강사(B5)와 수강 등록한 학생(B2)만 받을 수 있다.
	 */
	AssignmentFileResponse getFile(Long userId, Role role, Long assignmentId);
}
