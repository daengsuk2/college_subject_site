package kr.ac.ync.midterm.assignment.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.ync.midterm.assignment.domain.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

	// I3: 강좌의 과제 목록 (시작일시가 늦은 순)
	List<Assignment> findByCourseIdOrderByStartAtDesc(Long courseId);

	// S2, B2: 학생이 수강 등록한 강좌의 과제만 조회한다 (강좌 정보를 함께 조회해 추가 쿼리를 막는다)
	@Query("""
			select a from Assignment a
			join fetch a.course
			where exists (
				select e.id from Enrollment e
				where e.course = a.course and e.student.id = :studentId
			)
			""")
	List<Assignment> findAllByEnrolledStudent(@Param("studentId") Long studentId);

	// S5: 학생이 수강 등록한 강좌의 시작된 과제 수 (예정 과제는 제외)
	@Query("""
			select count(a) from Assignment a
			where a.startAt <= :now
			  and exists (select e.id from Enrollment e where e.course = a.course and e.student.id = :studentId)
			""")
	long countStartedByEnrolledStudent(@Param("studentId") Long studentId, @Param("now") LocalDateTime now);

	// S5: 마감 임박 과제 = 진행중이고 종료까지 limit 이내이며 내가 아직 제출하지 않은 과제 (종료가 이른 순)
	@Query("""
			select a from Assignment a join fetch a.course
			where a.startAt <= :now and a.endAt >= :now and a.endAt <= :limit
			  and exists (select e.id from Enrollment e where e.course = a.course and e.student.id = :studentId)
			  and not exists (select s.id from Submission s where s.assignment = a and s.student.id = :studentId)
			order by a.endAt asc, a.id asc
			""")
	List<Assignment> findDueSoon(@Param("studentId") Long studentId, @Param("now") LocalDateTime now,
			@Param("limit") LocalDateTime limit);

	// 강사 대시보드: 내 강좌에서 진행중인 과제 수 (시작 <= 현재 <= 종료)
	@Query("""
			select count(a) from Assignment a
			where a.course.instructor.id = :instructorId and a.startAt <= :now and a.endAt >= :now
			""")
	long countInProgressByInstructor(@Param("instructorId") Long instructorId, @Param("now") LocalDateTime now);
}
