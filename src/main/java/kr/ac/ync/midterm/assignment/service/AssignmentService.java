package kr.ac.ync.midterm.assignment.service;

import java.util.List;

import kr.ac.ync.midterm.assignment.dto.request.AssignmentRequest;
import kr.ac.ync.midterm.assignment.dto.response.AssignmentResponse;

public interface AssignmentService {

	AssignmentResponse create(Long instructorId, Long courseId, AssignmentRequest request);

	List<AssignmentResponse> findMyAssignments(Long instructorId, Long courseId);

	AssignmentResponse findMyAssignment(Long instructorId, Long assignmentId);

	AssignmentResponse update(Long instructorId, Long assignmentId, AssignmentRequest request);

	void delete(Long instructorId, Long assignmentId);

	/** 과제 첨부 삭제 (X18). 본인 강좌의 과제만 (B5), 첨부가 없으면 404. 과제의 다른 값은 바뀌지 않는다. */
	void removeAttachment(Long instructorId, Long assignmentId);
}
