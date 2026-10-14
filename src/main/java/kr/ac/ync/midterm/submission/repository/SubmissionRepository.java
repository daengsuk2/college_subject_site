package kr.ac.ync.midterm.submission.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.submission.domain.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

	// B8: 제출이 1건이라도 있는 과제는 삭제할 수 없다
	boolean existsByAssignmentId(Long assignmentId);

	// B3: 과제당 학생 1건
	Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
}
