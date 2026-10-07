package kr.ac.ync.midterm.assignment.service;

import java.util.List;

import kr.ac.ync.midterm.assignment.dto.response.StudentAssignmentResponse;

public interface StudentAssignmentService {

	List<StudentAssignmentResponse> findMyAssignments(Long studentId);

	StudentAssignmentResponse findMyAssignment(Long studentId, Long assignmentId);
}
