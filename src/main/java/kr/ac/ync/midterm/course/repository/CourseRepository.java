package kr.ac.ync.midterm.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.course.domain.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
