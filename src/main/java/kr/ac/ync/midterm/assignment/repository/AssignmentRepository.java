package kr.ac.ync.midterm.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.assignment.domain.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
}
