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
}
