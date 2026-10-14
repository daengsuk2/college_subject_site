package kr.ac.ync.midterm.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

/**
 * 표 검색 · 정렬 (X7 · X12 · X16). 검색과 정렬은 브라우저에서 동작하므로, 여기서는 서버가 내려 주는 화면에
 * 필요한 표 설정(class, 정렬용 data-order, 정렬 제외 열), 스크립트, 필터가 들어 있는지와 기존 내용이 그대로인지를 확인한다.
 * 실제 검색 · 정렬 동작은 브라우저에서 따로 확인한다.
 */
class DataTablesPagesTest extends BaseController {

	private static final String DATATABLES_JS = "/vendor/datatables/jquery.dataTables.min.js";
	private static final String INIT_JS = "/js/datatables-init.js";

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private User instructor;
	private Course course;
	private Assignment assignment;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(LocalDateTime.of(2026, 10, 10, 9, 0)).endAt(LocalDateTime.of(2026, 10, 17, 23, 59))
				.maxScore(100).build());
		submissionRepository.save(Submission.builder().assignment(assignment).student(student).content("답안")
				.submittedAt(LocalDateTime.of(2026, 10, 12, 14, 30)).build());
	}

	private ResultActions as(User actor, String url) throws Exception {
		return mockMvc.perform(get(url).with(user(new CustomUserDetails(actor))));
	}

	// ---------- 정적 파일 ----------

	@Test
	@DisplayName("공통 스크립트와 DataTables 파일이 로그인 없이 내려오고, 한글 설정과 기본 정렬 유지 옵션이 들어 있음")
	void staticFiles() throws Exception {
		// 정적 파일은 인코딩 헤더 없이 내려가므로(브라우저는 페이지의 UTF-8로 읽는다) 바이트를 UTF-8로 직접 읽는다
		String script = new String(mockMvc.perform(get(INIT_JS)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
		org.assertj.core.api.Assertions.assertThat(script).contains("검색:", "일치하는 항목이 없습니다.", "order: []",
				"td[colspan]", "data-search");
		mockMvc.perform(get(DATATABLES_JS)).andExpect(status().isOk());
		mockMvc.perform(get("/vendor/datatables/dataTables.bootstrap4.min.js")).andExpect(status().isOk());
		mockMvc.perform(get("/vendor/datatables/dataTables.bootstrap4.min.css")).andExpect(status().isOk());
	}

	// ---------- 강사 화면 ----------

	@Test
	@DisplayName("강좌 상세 - 과제 표 · 수강생 표에 검색 · 정렬이 켜지고, 날짜 · 배점의 정렬용 값과 정렬 제외 열이 있음")
	void courseDetail() throws Exception {
		as(instructor, "/instructor/courses/" + course.getId())
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(DATATABLES_JS)))
				.andExpect(content().string(containsString(INIT_JS)))
				.andExpect(content().string(containsString("dataTables.bootstrap4.min.css")))
				.andExpect(content().string(containsString("id=\"assignmentTable\"")))
				.andExpect(content().string(containsString("id=\"studentTable\"")))
				.andExpect(content().string(containsString("js-datatable")))
				// 시작 · 종료 일시와 배점의 정렬용 값 (화면 문자열은 그대로)
				.andExpect(content().string(containsString("data-order=\"202610100900\"")))
				.andExpect(content().string(containsString("data-order=\"202610172359\"")))
				.andExpect(content().string(containsString("data-order=\"100\"")))
				.andExpect(content().string(containsString("2026-10-10 09:00")))
				// 버튼이 있는 열은 정렬에서 제외
				.andExpect(content().string(containsString("class=\"no-sort\"")))
				.andExpect(content().string(containsString("1주차 과제")));
	}

	@Test
	@DisplayName("강좌 상세 - 수강생 · 과제가 없으면 안내 문구가 그대로 보이고 표 설정은 유지됨")
	void courseDetail_empty() throws Exception {
		Course empty = courseRepository.save(
				Course.builder().instructor(instructor).name("빈 강좌").joinCode("BBBBB3").build());

		as(instructor, "/instructor/courses/" + empty.getId())
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("등록된 과제가 없습니다.")))
				.andExpect(content().string(containsString("등록된 수강생이 없습니다.")))
				.andExpect(content().string(containsString(INIT_JS)));
	}

	@Test
	@DisplayName("강좌 상세 - 제목 · 이름에 들어 있는 HTML은 표에서 이스케이프됨 (XSS)")
	void courseDetail_escaped() throws Exception {
		assignmentRepository.save(Assignment.builder().course(course).title("<script>alert(1)</script>")
				.content("내용").startAt(LocalDateTime.of(2026, 10, 11, 9, 0))
				.endAt(LocalDateTime.of(2026, 10, 18, 9, 0)).maxScore(50).build());

		as(instructor, "/instructor/courses/" + course.getId())
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
	}

	@Test
	@DisplayName("강좌 목록 · 제출 현황 - 표에 검색 · 정렬이 켜지고, 제출 시각 · 점수의 정렬용 값과 정렬 제외 열이 있음")
	void courseListAndSubmissionStatus() throws Exception {
		as(instructor, "/instructor/courses")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(INIT_JS)))
				.andExpect(content().string(containsString("id=\"courseTable\"")))
				.andExpect(content().string(containsString("js-datatable")));

		as(instructor, "/instructor/assignments/" + assignment.getId() + "/submissions")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(INIT_JS)))
				.andExpect(content().string(containsString("id=\"submittedTable\"")))
				.andExpect(content().string(containsString("js-datatable")))
				.andExpect(content().string(containsString("data-order=\"202610121430\"")))
				// 채점 전이라 점수의 정렬용 값은 -1 (채점된 제출보다 앞)
				.andExpect(content().string(containsString("data-order=\"-1\"")))
				.andExpect(content().string(containsString("class=\"no-sort\"")));
	}

	@Test
	@DisplayName("제출 현황 - 미제출자 표에도 검색 · 정렬이 켜지고, 아무도 미제출이 아니면 표 없이 안내 문구")
	void submissionStatus_notSubmittedTable() throws Exception {
		User other = createUser("other@test.com", Role.STUDENT);
		enrollmentRepository.save(Enrollment.builder().course(course).student(other).build());

		as(instructor, "/instructor/assignments/" + assignment.getId() + "/submissions")
				.andExpect(content().string(containsString("id=\"notSubmittedTable\"")));
	}

	// ---------- 학생 화면 ----------

	@Test
	@DisplayName("내 과제 목록 - 검색 · 정렬이 켜지고 상태의 정렬용 값(진행중 0, 예정 1, 마감 2)과 강좌 · 상태 필터가 있음")
	void myAssignments() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		assignmentRepository.save(Assignment.builder().course(course).title("진행중 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
		assignmentRepository.save(Assignment.builder().course(course).title("예정 과제").content("내용")
				.startAt(now.plusDays(1)).endAt(now.plusDays(2)).maxScore(100).build());
		assignmentRepository.save(Assignment.builder().course(course).title("마감 과제").content("내용")
				.startAt(now.minusDays(5)).endAt(now.minusDays(1)).maxScore(100).build());

		as(student, "/student/assignments")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(INIT_JS)))
				.andExpect(content().string(containsString("id=\"assignmentTable\"")))
				.andExpect(content().string(containsString("js-datatable")))
				.andExpect(content().string(containsString("data-order=\"0\"")))
				.andExpect(content().string(containsString("data-order=\"1\"")))
				.andExpect(content().string(containsString("data-order=\"2\"")))
				// 필터: 강좌 선택(표에서 채움)과 상태 선택
				.andExpect(content().string(containsString("id=\"filterCourse\"")))
				.andExpect(content().string(containsString("id=\"filterStatus\"")))
				.andExpect(content().string(containsString("<option value=\"진행중\">")))
				.andExpect(content().string(containsString("<option value=\"예정\">")))
				.andExpect(content().string(containsString("<option value=\"마감\">")));
	}

	@Test
	@DisplayName("내 과제 목록 - 서버가 정한 처음 정렬(진행중 → 예정 → 마감)은 그대로이고 수강하지 않는 강좌의 과제는 없음")
	void myAssignments_serverOrderAndScope() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		assignmentRepository.save(Assignment.builder().course(course).title("마감 과제").content("내용")
				.startAt(now.minusDays(5)).endAt(now.minusDays(1)).maxScore(100).build());
		assignmentRepository.save(Assignment.builder().course(course).title("예정 과제").content("내용")
				.startAt(now.plusDays(1)).endAt(now.plusDays(2)).maxScore(100).build());
		assignmentRepository.save(Assignment.builder().course(course).title("진행중 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
		Course notEnrolled = courseRepository.save(
				Course.builder().instructor(instructor).name("수강하지 않는 강좌").joinCode("CCCCC4").build());
		assignmentRepository.save(Assignment.builder().course(notEnrolled).title("남의 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());

		String html = as(student, "/student/assignments").andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(html).doesNotContain("남의 과제");
		int running = html.indexOf("진행중 과제");
		int upcoming = html.indexOf("예정 과제");
		int closed = html.indexOf("마감 과제");
		org.assertj.core.api.Assertions.assertThat(running).isPositive().isLessThan(upcoming);
		org.assertj.core.api.Assertions.assertThat(upcoming).isLessThan(closed);
	}

	@Test
	@DisplayName("내 과제 목록 - 과제가 없으면 필터 없이 안내 문구만 보임")
	void myAssignments_empty() throws Exception {
		User fresh = createUser("fresh@test.com", Role.STUDENT);

		as(fresh, "/student/assignments")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("표시할 과제가 없습니다.")))
				.andExpect(content().string(not(containsString("id=\"filterCourse\""))));
	}

	@Test
	@DisplayName("내 제출 · 점수 - 검색 · 정렬이 켜지고 제출 시각 · 점수의 정렬용 값이 있음")
	void mySubmissions() throws Exception {
		as(student, "/student/submissions")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(INIT_JS)))
				.andExpect(content().string(containsString("id=\"submissionTable\"")))
				.andExpect(content().string(containsString("data-order=\"202610121430\"")))
				.andExpect(content().string(containsString("data-order=\"-1\"")));
	}

	// ---------- 필요한 화면에만 불러오기 ----------

	@Test
	@DisplayName("표 검색 · 정렬 스크립트는 표가 있는 화면에만 불러오고 로그인 · 대시보드 · 마이페이지 등에는 불러오지 않음")
	void onlyWhereNeeded() throws Exception {
		mockMvc.perform(get("/login")).andExpect(content().string(not(containsString(DATATABLES_JS))));
		as(instructor, "/").andExpect(content().string(not(containsString(DATATABLES_JS))));
		// 학생 대시보드는 강좌별 통계 표(X35)가 있을 때만 불러온다: 수강 강좌가 없는 학생에게는 불러오지 않는다
		User noCourse = createUser("no-course@test.com", Role.STUDENT);
		as(noCourse, "/").andExpect(content().string(not(containsString(DATATABLES_JS))));
		as(student, "/").andExpect(content().string(containsString(DATATABLES_JS)));
		as(student, "/mypage").andExpect(content().string(not(containsString(DATATABLES_JS))));
		as(instructor, "/instructor/stats").andExpect(content().string(not(containsString(DATATABLES_JS))));
	}
}
