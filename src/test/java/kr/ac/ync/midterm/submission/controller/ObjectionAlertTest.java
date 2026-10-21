package kr.ac.ync.midterm.submission.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import kr.ac.ync.midterm.assignment.domain.Assignment;
import kr.ac.ync.midterm.assignment.repository.AssignmentRepository;
import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.security.CustomUserDetails;
import kr.ac.ync.midterm.submission.domain.Submission;
import kr.ac.ync.midterm.submission.dto.response.ObjectionAlerts;
import kr.ac.ync.midterm.submission.repository.SubmissionRepository;
import kr.ac.ync.midterm.submission.service.ObjectionService;
import kr.ac.ync.midterm.support.BaseController;
import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 이의 신청 알림 (X29): 강사 상단바의 종 아이콘에 답변을 기다리는 이의 신청이 표시되는지,
 * 본인 강좌의 것만 · 강사에게만 보이는지, 답변하면 사라지는지 확인한다.
 */
class ObjectionAlertTest extends BaseController {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectionService objectionService;

	private User instructor;
	private User otherInstructor;
	private Course course;
	private Assignment assignment;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		otherInstructor = createUser("other-teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("ALERT1").build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("1주차 과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
	}

	private CustomUserDetails principal(User user) {
		return new CustomUserDetails(user);
	}

	// 채점까지 끝난 제출에 이의를 신청한 상태를 만든다
	private Submission objected(User owner, String objection) {
		enrollmentRepository.save(Enrollment.builder().course(course).student(owner).build());
		Submission submission = submissionRepository.save(Submission.builder().assignment(assignment).student(owner)
				.content("답안").submittedAt(LocalDateTime.now().minusHours(1)).build());
		submission.grade(70, "피드백", LocalDateTime.now());
		submission.raiseObjection(objection, LocalDateTime.now());
		return submissionRepository.saveAndFlush(submission);
	}

	private User namedStudent(String email, String studentNo, String name) {
		return userRepository.save(User.builder().email(email).password(passwordEncoder.encode("password123"))
				.name(name).studentNo(studentNo).role(Role.STUDENT).build());
	}

	@Test
	@DisplayName("이의 신청이 없으면 종 아이콘은 보이지만 개수 배지는 없고 안내 문구가 나옴")
	void noPending() throws Exception {
		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"objectionAlerts\"")))
				.andExpect(content().string(not(containsString("objectionAlertCount"))))
				.andExpect(content().string(containsString("답변을 기다리는 이의 신청이 없습니다.")));
	}

	@Test
	@DisplayName("이의 신청이 오면 개수 배지와 학생 이름 · 과제 제목 · 강좌명이 보이고 채점 화면으로 연결됨")
	void pendingShown() throws Exception {
		Submission submission = objected(namedStudent("kim@test.com", "20260101", "김테스트"), "점수 재확인 부탁드립니다");

		mockMvc.perform(get("/instructor/courses").with(user(principal(instructor))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("objectionAlertCount")))
				.andExpect(content().string(containsString("김테스트 · 1주차 과제")))
				.andExpect(content().string(containsString("자바스프링")))
				.andExpect(content().string(containsString("/instructor/submissions/" + submission.getId() + "/grade")))
				.andExpect(content().string(not(containsString("답변을 기다리는 이의 신청이 없습니다."))));
	}

	@Test
	@DisplayName("강사가 답변하면 알림에서 사라짐")
	void answeredDisappears() throws Exception {
		Submission submission = objected(namedStudent("kim@test.com", "20260101", "김테스트"), "이의");
		assertThat(objectionService.findPendingAlerts(instructor.getId()).count()).isEqualTo(1);

		submission.answerObjection("확인했습니다", LocalDateTime.now());
		submissionRepository.saveAndFlush(submission);

		assertThat(objectionService.findPendingAlerts(instructor.getId()).count()).isZero();
		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(content().string(not(containsString("objectionAlertCount"))))
				.andExpect(content().string(not(containsString("김테스트 · 1주차 과제"))));
	}

	@Test
	@DisplayName("다른 강사의 강좌에 온 이의는 알림에 보이지 않음 (B5)")
	void otherInstructorNotShown() throws Exception {
		objected(namedStudent("kim@test.com", "20260101", "김테스트"), "이의");

		ObjectionAlerts other = objectionService.findPendingAlerts(otherInstructor.getId());
		assertThat(other.count()).isZero();
		assertThat(other.items()).isEmpty();
		mockMvc.perform(get("/").with(user(principal(otherInstructor))))
				.andExpect(content().string(not(containsString("objectionAlertCount"))))
				.andExpect(content().string(not(containsString("김테스트 · 1주차 과제"))));
	}

	@Test
	@DisplayName("학생과 비로그인 화면에는 알림 영역이 없음")
	void notForStudentOrAnonymous() throws Exception {
		objected(student, "이의");

		mockMvc.perform(get("/").with(user(principal(student))))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("objectionAlerts"))));
		mockMvc.perform(get("/login"))
				.andExpect(content().string(not(containsString("objectionAlerts"))));
	}

	@Test
	@DisplayName("이의가 6건이면 배지는 6, 목록에는 최근 5건만 보이고 나머지는 '외 1건'으로 표시됨")
	void moreThanLimit() throws Exception {
		for (int i = 1; i <= 6; i++) {
			objected(namedStudent("s" + i + "@test.com", "2026020" + i, "학생" + i), "이의 " + i);
		}

		ObjectionAlerts alerts = objectionService.findPendingAlerts(instructor.getId());
		assertThat(alerts.count()).isEqualTo(6);
		assertThat(alerts.items()).hasSize(5);
		assertThat(alerts.hasMore()).isTrue();

		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(content().string(containsString("외 1건")));
	}

	@Test
	@DisplayName("학생 이름 · 과제 제목의 HTML은 알림에서 이스케이프됨")
	void xssEscaped() throws Exception {
		assignment.update("<b>굵게</b> 과제", "내용", assignment.getStartAt(), assignment.getEndAt(), 100);
		assignmentRepository.saveAndFlush(assignment);
		objected(namedStudent("x@test.com", "20260301", "<script>alert(1)</script>"), "이의");

		mockMvc.perform(get("/").with(user(principal(instructor))))
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
				.andExpect(content().string(containsString("&lt;b&gt;굵게&lt;/b&gt; 과제")))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(not(containsString("<b>굵게</b> 과제"))));
	}
}
