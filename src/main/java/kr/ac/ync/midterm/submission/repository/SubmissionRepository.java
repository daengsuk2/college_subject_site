package kr.ac.ync.midterm.submission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.ync.midterm.submission.domain.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

	// B8: 제출이 1건이라도 있는 과제는 삭제할 수 없다
	boolean existsByAssignmentId(Long assignmentId);

	// B3: 과제당 학생 1건
	Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

	// I4: 과제의 제출자 목록 (제출 시각이 이른 순). 이 강좌의 수강생이 낸 제출만 포함한다
	@Query("""
			select s from Submission s join fetch s.student
			where s.assignment.id = :assignmentId
			  and exists (select e.id from Enrollment e
			              where e.course.id = s.assignment.course.id and e.student.id = s.student.id)
			order by s.submittedAt asc, s.id asc
			""")
	List<Submission> findSubmittedByEnrolledStudents(@Param("assignmentId") Long assignmentId);
}
