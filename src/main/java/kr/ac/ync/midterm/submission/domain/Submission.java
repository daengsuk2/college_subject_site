package kr.ac.ync.midterm.submission.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.user.domain.User;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "submission", uniqueConstraints = @UniqueConstraint(
		name = "uk_submission_assignment_student", columnNames = { "assignment_id", "student_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assignment_id", nullable = false)
	private Assignment assignment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "file_path")
	private String filePath;

	@Column(name = "submitted_at", nullable = false)
	private LocalDateTime submittedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SubmissionStatus status;

	private Integer score;

	@Column(columnDefinition = "TEXT")
	private String feedback;

	@Column(name = "graded_at")
	private LocalDateTime gradedAt;

	@Builder
	private Submission(Assignment assignment, User student, String content, String filePath) {
		this.assignment = assignment;
		this.student = student;
		this.content = content;
		this.filePath = filePath;
	}

	@PrePersist
	void onCreate() {
		if (this.submittedAt == null) {
			this.submittedAt = LocalDateTime.now();
		}
		if (this.status == null) {
			this.status = SubmissionStatus.SUBMITTED;
		}
	}
}
