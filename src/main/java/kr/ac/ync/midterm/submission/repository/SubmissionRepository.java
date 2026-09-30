package kr.ac.ync.midterm.submission.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.submission.domain.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
}
