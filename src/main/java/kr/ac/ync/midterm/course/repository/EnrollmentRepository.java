package kr.ac.ync.midterm.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.course.domain.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	// S1: 이미 수강 등록한 강좌인지 확인 (UNIQUE(course_id, student_id))
	boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);
}
