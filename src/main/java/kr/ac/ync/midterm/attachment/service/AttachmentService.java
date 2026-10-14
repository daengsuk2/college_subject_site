package kr.ac.ync.midterm.attachment.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.attachment.dto.AttachmentFileResponse;
import kr.ac.ync.midterm.attachment.dto.AttachmentResponse;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.user.domain.Role;

/**
 * 여러 파일 첨부 (X17). 처음 올린 첨부 1개는 기존대로 assignment / submission 에 있고, 추가 첨부를 이 서비스가 다룬다.
 * 과제 · 제출 하나에 붙일 수 있는 첨부는 처음 첨부를 포함해 최대 {@value #MAX_FILES}개이다.
 */
public interface AttachmentService {

	int MAX_FILES = 5;

	List<AttachmentResponse> findByAssignment(Long assignmentId);

	List<AttachmentResponse> findBySubmission(Long submissionId);

	/**
	 * 과제에 추가 첨부를 저장한다 (과제 등록 · 수정의 트랜잭션 안에서 호출). 권한 · 기간 검사는 호출하는 쪽에서 끝낸 상태이다.
	 * 비어 있는 파일은 건너뛰고, 개수 · 확장자 · 크기를 어기면 InvalidFileException 이며 이미 저장한 파일은 롤백 때 지워진다.
	 */
	void addToAssignment(Assignment assignment, List<MultipartFile> files);

	/** 제출에 추가 첨부를 저장한다 (제출 · 재제출의 트랜잭션 안에서 호출). */
	void addToSubmission(Submission submission, List<MultipartFile> files);

	/** 과제를 지울 때 추가 첨부의 DB 행과 서버 파일(커밋 뒤)을 함께 지운다. */
	void removeAllOfAssignment(Long assignmentId);

	/** 다운로드. 과제 첨부는 본인 강좌의 강사(B5) · 수강 중인 학생(B2), 제출 첨부는 본인 제출의 학생 · 본인 강좌의 강사(B5). */
	AttachmentFileResponse getFile(Long userId, Role role, Long attachmentId);

	/** 강사가 과제의 추가 첨부 1개를 삭제한다 (B5). 과제 id를 돌려준다. 과제 첨부가 아니면 404. */
	Long removeFromAssignment(Long instructorId, Long attachmentId);

	/** 학생이 제출의 추가 첨부 1개를 삭제한다. 본인 제출(B2) · 제출 기간(B1) · 채점 전(B4). 이 과제의 제출 첨부가 아니면 404. */
	void removeFromSubmission(Long studentId, Long assignmentId, Long attachmentId);
}
