package kr.ac.ync.midterm.assignment.dto.response;

import org.springframework.core.io.Resource;

/**
 * 다운로드할 첨부파일 (실제 파일 + 사용자에게 보여 줄 원본 이름).
 */
public record AssignmentFileResponse(Resource resource, String originalFilename) {
}
