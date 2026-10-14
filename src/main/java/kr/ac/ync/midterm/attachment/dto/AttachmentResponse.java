package kr.ac.ync.midterm.attachment.dto;

import kr.ac.ync.midterm.attachment.domain.Attachment;

/**
 * 화면에 보여 줄 추가 첨부 한 줄 (X17).
 */
public record AttachmentResponse(Long id, String originalFilename) {

	public static AttachmentResponse of(Attachment attachment) {
		return new AttachmentResponse(attachment.getId(), attachment.getOriginalFilename());
	}
}
