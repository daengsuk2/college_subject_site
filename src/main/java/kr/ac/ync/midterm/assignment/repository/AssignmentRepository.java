package kr.ac.ync.midterm.assignment.repository;

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
}
