package kr.ac.ync.midterm.submission.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.ync.midterm.dashboard.dto.response.StudentSubmissionAggregate;
import kr.ac.ync.midterm.stats.dto.response.AssignmentSubmissionAggregate;
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

	// I6: 강좌의 시작된 과제별 제출 수 · 채점 완료 수 · 평균 점수 (group by 1회). 이 강좌 수강생의 제출만 센다
	@Query("""
			select new kr.ac.ync.midterm.stats.dto.response.AssignmentSubmissionAggregate(
				s.assignment.id,
				count(s),
				count(case when s.status = kr.ac.ync.midterm.submission.domain.SubmissionStatus.GRADED then 1 end),
				avg(case when s.status = kr.ac.ync.midterm.submission.domain.SubmissionStatus.GRADED then s.score end))
			from Submission s
			where s.assignment.course.id = :courseId
			  and s.assignment.startAt <= :now
			  and exists (select e.id from Enrollment e
			              where e.course.id = :courseId and e.student.id = s.student.id)
			group by s.assignment.id
			""")
	List<AssignmentSubmissionAggregate> aggregateByAssignment(@Param("courseId") Long courseId,
			@Param("now") LocalDateTime now);

	// S5: 학생의 제출 집계 (수강 강좌의 시작된 과제에 낸 제출). 점수 합 · 배점 합은 채점 완료된 제출만 더한다
	@Query("""
			select new kr.ac.ync.midterm.dashboard.dto.response.StudentSubmissionAggregate(
				count(s),
				count(case when s.status = kr.ac.ync.midterm.submission.domain.SubmissionStatus.GRADED then 1 end),
				sum(case when s.status = kr.ac.ync.midterm.submission.domain.SubmissionStatus.GRADED then s.score end),
				sum(case when s.status = kr.ac.ync.midterm.submission.domain.SubmissionStatus.GRADED then s.assignment.maxScore end))
			from Submission s
			where s.student.id = :studentId
			  and s.assignment.startAt <= :now
			  and exists (select e.id from Enrollment e
			              where e.course.id = s.assignment.course.id and e.student.id = :studentId)
			""")
	StudentSubmissionAggregate aggregateForStudent(@Param("studentId") Long studentId,
			@Param("now") LocalDateTime now);

	// 강사 대시보드: 내 강좌에서 채점 대기 중인 제출 수 (수강생의 제출만)
	@Query("""
			select count(s) from Submission s
			where s.assignment.course.instructor.id = :instructorId
			  and s.status = kr.ac.ync.midterm.submission.domain.SubmissionStatus.SUBMITTED
			  and exists (select e.id from Enrollment e
			              where e.course.id = s.assignment.course.id and e.student.id = s.student.id)
			""")
	long countPendingGrading(@Param("instructorId") Long instructorId);

	// 강사 대시보드: 내 강좌의 최근 제출 (수강생의 제출만, 최근순)
	@Query("""
			select s from Submission s join fetch s.student join fetch s.assignment a join fetch a.course c
			where c.instructor.id = :instructorId
			  and exists (select e.id from Enrollment e
			              where e.course.id = c.id and e.student.id = s.student.id)
			order by s.submittedAt desc, s.id desc
			""")
	List<Submission> findRecentByInstructor(@Param("instructorId") Long instructorId, Pageable pageable);

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
