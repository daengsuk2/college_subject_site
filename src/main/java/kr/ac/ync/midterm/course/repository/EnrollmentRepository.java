package kr.ac.ync.midterm.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.course.domain.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
}
