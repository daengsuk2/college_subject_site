package kr.ac.ync.midterm.course.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.stats.dto.response.StudentSubmissionCount;
import kr.ac.ync.midterm.user.domain.User;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	// S1: 이미 수강 등록한 강좌인지 확인 (UNIQUE(course_id, student_id))
	boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

	// I2: 강좌의 수강생 목록 (등록한 순서). 학생 정보를 함께 조회해 추가 쿼리를 막는다
	@EntityGraph(attributePaths = "student")
	List<Enrollment> findByCourseIdOrderByJoinedAtAsc(Long courseId);

	// I4: 강좌의 수강생 수
	long countByCourseId(Long courseId);

	// S5: 학생이 수강 등록한 강좌 수
	long countByStudentId(Long studentId);

	// I6: 수강생별로 시작된 과제 중 제출한 과제 수 (1회 조회)
	@Query("""
			select new kr.ac.ync.midterm.stats.dto.response.StudentSubmissionCount(
				st.id, st.studentNo, st.name,
				(select count(s.id) from Submission s
				 where s.student.id = st.id and s.assignment.course.id = :courseId
				   and s.assignment.startAt <= :now))
			from Enrollment e join e.student st
			where e.course.id = :courseId
			order by st.name asc, st.id asc
			""")
	List<StudentSubmissionCount> countSubmissionsPerStudent(@Param("courseId") Long courseId,
			@Param("now") LocalDateTime now);

	// I4: 수강생 중 이 과제를 아직 제출하지 않은 학생 (수강생 - 제출자), 이름 순
	@Query("""
			select e.student from Enrollment e
			where e.course.id = :courseId
			  and not exists (select s.id from Submission s
			                  where s.assignment.id = :assignmentId and s.student.id = e.student.id)
			order by e.student.name asc, e.student.id asc
			""")
	List<User> findStudentsNotSubmitted(@Param("courseId") Long courseId, @Param("assignmentId") Long assignmentId);
}
