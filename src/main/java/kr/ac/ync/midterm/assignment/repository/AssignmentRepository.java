package kr.ac.ync.midterm.assignment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.assignment.domain.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

	// I3: 강좌의 과제 목록 (시작일시가 늦은 순)
	List<Assignment> findByCourseIdOrderByStartAtDesc(Long courseId);
}
