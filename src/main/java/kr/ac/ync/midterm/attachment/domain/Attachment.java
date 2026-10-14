package kr.ac.ync.midterm.attachment.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.submission.domain.Submission;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추가 첨부파일 (X17). 과제의 첨부 또는 제출의 첨부 중 한쪽에만 연결된다.
 * 처음 올린 첨부 1개는 기존대로 assignment / submission 의 file_path 에 있고, 그 밖의 첨부가 이 테이블에 쌓인다.
 */
@Entity
@Table(name = "attachment")
@Check(constraints = "(assignment_id is not null and submission_id is null) or (assignment_id is null and submission_id is not null)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignment_id")
	private Assignment assignment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "submission_id")
	private Submission submission;

	/** 서버에 저장된 UUID 이름 (폴더 경로가 아니라 파일 이름만 보관) */
	@Column(name = "stored_name", nullable = false)
	private String storedName;

	/** 사용자가 올린 원본 파일명 (다운로드 때 보여 주기 위한 값) */
	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private Attachment(Assignment assignment, Submission submission, String storedName, String originalFilename) {
		this.assignment = assignment;
		this.submission = submission;
		this.storedName = storedName;
		this.originalFilename = originalFilename;
	}

	public static Attachment ofAssignment(Assignment assignment, String storedName, String originalFilename) {
		return new Attachment(assignment, null, storedName, originalFilename);
	}

	public static Attachment ofSubmission(Submission submission, String storedName, String originalFilename) {
		return new Attachment(null, submission, storedName, originalFilename);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
