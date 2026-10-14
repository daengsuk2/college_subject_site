package kr.ac.ync.midterm.attachment.dto;

import org.springframework.core.io.Resource;

public record AttachmentFileResponse(Resource resource, String originalFilename) {
}
