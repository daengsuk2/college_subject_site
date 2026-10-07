package kr.ac.ync.midterm.assignment.domain;

import java.time.LocalDateTime;

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
import kr.ac.ync.midterm.course.domain.Course;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Column(name = "max_score", nullable = false)
	private int maxScore;

	// 서버에 저장된 첨부파일 이름(UUID). 폴더 경로가 아니라 파일 이름만 보관한다.
	@Column(name = "file_path")
	private String filePath;

	// 사용자가 올린 원본 파일명 (다운로드 때 보여 주기 위한 추가 컬럼, I3-b)
	@Column(name = "original_filename")
	private String originalFilename;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private Assignment(Course course, String title, String content, LocalDateTime startAt,
			LocalDateTime endAt, int maxScore, String filePath) {
		this.course = course;
		this.title = title;
		this.content = content;
		this.startAt = startAt;
		this.endAt = endAt;
		this.maxScore = maxScore;
		this.filePath = filePath;
	}

	/**
	 * 과제 내용 수정 (I3). 기간 검증(B7)은 서비스에서 한다.
	 */
	public void update(String title, String content, LocalDateTime startAt, LocalDateTime endAt, int maxScore) {
		this.title = title;
		this.content = content;
		this.startAt = startAt;
		this.endAt = endAt;
		this.maxScore = maxScore;
	}

	/**
	 * 첨부파일을 연결한다 (I3-b). 기존 첨부가 있으면 교체되며, 기존 파일 삭제는 서비스에서 한다.
	 */
	public void attachFile(String storedName, String originalFilename) {
		this.filePath = storedName;
		this.originalFilename = originalFilename;
	}

	public boolean hasFile() {
		return filePath != null;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
