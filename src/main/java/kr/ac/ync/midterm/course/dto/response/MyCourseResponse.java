package kr.ac.ync.midterm.course.dto.response;

import java.time.LocalDateTime;

/**
 * 학생의 "내 수강 강좌" 한 줄 (X3): 강좌명, 담당 강사, 수강 등록일, 시작된 과제 수, 내가 제출한 과제 수.
 * 과제 수는 시작된 과제만 센다 (예정 과제는 제출할 수 없으므로 제외).
 */
public record MyCourseResponse(Long courseId, String courseName, String instructorName, LocalDateTime joinedAt,
		Long startedAssignmentCount, Long submittedCount) {
}
