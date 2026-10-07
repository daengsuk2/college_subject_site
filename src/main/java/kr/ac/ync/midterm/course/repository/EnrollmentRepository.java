package kr.ac.ync.midterm.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.course.domain.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	// S1: 이미 수강 등록한 강좌인지 확인 (UNIQUE(course_id, student_id))
	boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

	// I2: 강좌의 수강생 목록 (등록한 순서). 학생 정보를 함께 조회해 추가 쿼리를 막는다
	@EntityGraph(attributePaths = "student")
	List<Enrollment> findByCourseIdOrderByJoinedAtAsc(Long courseId);
}
