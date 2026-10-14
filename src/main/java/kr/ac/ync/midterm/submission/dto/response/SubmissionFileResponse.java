package kr.ac.ync.midterm.submission.dto.response;

import org.springframework.core.io.Resource;

/**
 * 다운로드할 제출 파일 (실제 파일 + 사용자에게 보여 줄 원본 이름).
 */
public record SubmissionFileResponse(Resource resource, String originalFilename) {
}
