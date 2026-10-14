package kr.ac.ync.midterm.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
 * 한 화면 안에서 같은 id가 두 번 나오지 않는지 확인한다. (공통 레이아웃의 id와 폼 입력칸의 id가 겹치면
 * 라벨을 눌러도 입력칸에 포커스가 가지 않는다.)
 */
class DuplicateIdTest extends BaseController {

	private static final Pattern ID = Pattern.compile("\\sid=\"([^\"]+)\"");

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
	private Submission submission;

	@BeforeEach
	void setUpData() {
		instructor = createUser("teacher@test.com", Role.INSTRUCTOR);
		course = courseRepository.save(
				Course.builder().instructor(instructor).name("자바스프링").joinCode("AAAAA2").build());
		enrollmentRepository.save(Enrollment.builder().course(course).student(student).build());
		LocalDateTime now = LocalDateTime.now();
		assignment = assignmentRepository.save(Assignment.builder().course(course).title("과제").content("내용")
				.startAt(now.minusDays(1)).endAt(now.plusDays(1)).maxScore(100).build());
		submission = submissionRepository.save(Submission.builder().assignment(assignment).student(student)
				.content("답안").submittedAt(now.minusHours(1)).build());
	}

	private List<String> duplicateIds(String html) {
		Set<String> seen = new HashSet<>();
		List<String> duplicates = new ArrayList<>();
		Matcher m = ID.matcher(html);
		while (m.find()) {
			if (!seen.add(m.group(1))) {
				duplicates.add(m.group(1));
			}
		}
		return duplicates;
	}

	private String page(String url, User actor) throws Exception {
		return mockMvc.perform(get(url).with(user(new CustomUserDetails(actor))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("과제 등록 폼 - '내용' 라벨과 입력칸이 겹치지 않는 id로 연결되고 화면 안에 중복 id가 없음")
	void assignmentForm_contentLabelLinked() throws Exception {
		String html = page("/instructor/assignments/new?courseId=" + course.getId(), instructor);

		assertThat(html).contains("<label for=\"assignmentContent\">");
		assertThat(html).contains("id=\"assignmentContent\"");
		assertThat(duplicateIds(html)).isEmpty();
	}

	@Test
	@DisplayName("과제 수정 폼 - 중복 id가 없고 기존 내용이 입력칸에 채워짐")
	void assignmentEditForm() throws Exception {
		String html = page("/instructor/assignments/" + assignment.getId() + "/edit", instructor);

		assertThat(duplicateIds(html)).isEmpty();
		assertThat(html).contains("id=\"assignmentContent\"");
		mockMvc.perform(get("/instructor/assignments/" + assignment.getId() + "/edit")
						.with(user(new CustomUserDetails(instructor))))
				.andExpect(content().string(containsString(">내용</textarea>")));
	}

	@Test
	@DisplayName("다른 폼과 주요 화면에도 중복 id가 없음 (제출 폼, 채점 폼, 통계, 대시보드, 과제 · 제출 현황)")
	void otherPages() throws Exception {
		assertThat(duplicateIds(page("/student/assignments/" + assignment.getId() + "/submit", student))).isEmpty();
		assertThat(duplicateIds(page("/instructor/submissions/" + submission.getId() + "/grade", instructor)))
				.isEmpty();
		assertThat(duplicateIds(page("/instructor/stats", instructor))).isEmpty();
		assertThat(duplicateIds(page("/", instructor))).isEmpty();
		assertThat(duplicateIds(page("/", student))).isEmpty();
		assertThat(duplicateIds(page("/instructor/courses/" + course.getId(), instructor))).isEmpty();
		assertThat(duplicateIds(page("/instructor/assignments/" + assignment.getId() + "/submissions", instructor)))
				.isEmpty();
		assertThat(duplicateIds(page("/student/assignments", student))).isEmpty();
	}
}
