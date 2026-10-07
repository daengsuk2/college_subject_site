package kr.ac.ync.midterm.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.course.domain.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

	boolean existsByJoinCode(String joinCode);

	// S1: 참여코드로 강좌를 찾는다
	Optional<Course> findByJoinCode(String joinCode);

	// B5: 강사는 본인이 개설한 강좌만 조회한다
	List<Course> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);
}
