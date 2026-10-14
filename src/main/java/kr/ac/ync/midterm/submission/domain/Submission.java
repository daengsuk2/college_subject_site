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

	/** 다운로드 시 보여 줄 원본 파일명 (file_path는 서버에 저장된 UUID 이름) */
	@Column(name = "original_filename")
	private String originalFilename;

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

	/** 학생의 이의 신청 내용 (X29). 제출당 1회만 신청할 수 있어 값이 있으면 신청한 것이다. */
	@Column(name = "objection_content", length = 500)
	private String objectionContent;

	@Column(name = "objection_at")
	private LocalDateTime objectionAt;

	/** 강사의 이의 답변 (X29) */
	@Column(name = "objection_response", length = 500)
	private String objectionResponse;

	@Column(name = "objection_responded_at")
	private LocalDateTime objectionRespondedAt;

	@Builder
	private Submission(Assignment assignment, User student, String content, String filePath,
			LocalDateTime submittedAt) {
		this.assignment = assignment;
		this.student = student;
		this.content = content;
		this.filePath = filePath;
		this.submittedAt = submittedAt;
	}

	/** 재제출(B3): 같은 제출을 덮어쓴다. 채점 완료 여부(B4)는 서비스에서 먼저 검사한다. */
	public void resubmit(String content, LocalDateTime submittedAt) {
		this.content = content;
		this.submittedAt = submittedAt;
	}

	/** 첨부파일을 연결한다 (S3-b). 기존 첨부가 있으면 교체되며, 기존 파일 삭제는 서비스에서 한다. */
	public void attachFile(String storedName, String originalFilename) {
		this.filePath = storedName;
		this.originalFilename = originalFilename;
	}

	/** 첨부파일 정보를 비운다 (X23). 서버의 파일 삭제는 서비스에서 DB 변경이 확정된 뒤에 한다. */
	public void detachFile() {
		this.filePath = null;
		this.originalFilename = null;
	}

	public boolean hasFile() {
		return filePath != null;
	}

	/** 채점(I5): 점수와 피드백을 기록하고 채점 완료 상태로 바꾼다. 점수 범위(B6)는 서비스에서 먼저 검사한다. */
	public void grade(int score, String feedback, LocalDateTime gradedAt) {
		this.score = score;
		this.feedback = feedback;
		this.gradedAt = gradedAt;
		this.status = SubmissionStatus.GRADED;
	}

	/** 이의 신청 (X29): 채점 완료 · 1회 제한은 서비스에서 먼저 검사한다. */
	public void raiseObjection(String content, LocalDateTime raisedAt) {
		this.objectionContent = content;
		this.objectionAt = raisedAt;
	}

	/** 이의 답변 (X29): 신청이 있고 아직 답변하지 않았는지는 서비스에서 먼저 검사한다. */
	public void answerObjection(String response, LocalDateTime respondedAt) {
		this.objectionResponse = response;
		this.objectionRespondedAt = respondedAt;
	}

	public boolean hasObjection() {
		return objectionContent != null;
	}

	public boolean hasObjectionResponse() {
		return objectionResponse != null;
	}

	public boolean isGraded() {
		return this.status == SubmissionStatus.GRADED;
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
