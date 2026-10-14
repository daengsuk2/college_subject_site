package kr.ac.ync.midterm.attachment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.attachment.domain.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	List<Attachment> findByAssignmentIdOrderByIdAsc(Long assignmentId);

	List<Attachment> findBySubmissionIdOrderByIdAsc(Long submissionId);

	long countByAssignmentId(Long assignmentId);

	long countBySubmissionId(Long submissionId);
}
