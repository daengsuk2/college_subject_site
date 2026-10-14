package kr.ac.ync.midterm.dashboard.service;

import kr.ac.ync.midterm.dashboard.dto.response.InstructorDashboardResponse;
import kr.ac.ync.midterm.dashboard.dto.response.StudentDashboardResponse;

public interface DashboardService {

	/** 학생 대시보드. 수강 등록한 강좌의 과제와 본인의 제출만 집계한다 (B2). */
	StudentDashboardResponse findStudentDashboard(Long studentId);

	/** 강사 대시보드. 본인 강좌의 데이터만 집계한다 (B5). */
	InstructorDashboardResponse findInstructorDashboard(Long instructorId);
}
