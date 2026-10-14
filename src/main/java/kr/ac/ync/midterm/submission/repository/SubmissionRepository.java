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

	// S4: 내 제출 목록 (최근 제출순). 수강 중인 강좌의 제출만, 과제와 강좌를 함께 조회해 추가 쿼리를 막는다
	@Query("""
			select s from Submission s join fetch s.assignment a join fetch a.course c
			where s.student.id = :studentId
			  and exists (select e.id from Enrollment e
			              where e.course.id = c.id and e.student.id = :studentId)
			order by s.submittedAt desc, s.id desc
			""")
	List<Submission> findMySubmissions(@Param("studentId") Long studentId);

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
