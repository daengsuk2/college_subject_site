package kr.ac.ync.midterm.course.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.dto.request.CourseCreateRequest;
import kr.ac.ync.midterm.course.dto.response.CourseDetailResponse;
import kr.ac.ync.midterm.course.dto.response.CourseResponse;
import kr.ac.ync.midterm.course.dto.response.EnrolledStudentResponse;
import kr.ac.ync.midterm.course.exception.CourseNotFoundException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.global.exception.ForbiddenException;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

	private static final int MAX_ATTEMPTS = 10;

	private final CourseRepository courseRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final UserRepository userRepository;
	private final JoinCodeGenerator joinCodeGenerator;

	/**
	 * 강좌 개설 (I1). 참여코드를 자동으로 발급한다.
	 */
	@Override
	@Transactional
	public CourseResponse create(Long instructorId, CourseCreateRequest request) {
		User instructor = userRepository.getReferenceById(instructorId);

		Course course = Course.builder()
				.instructor(instructor)
				.name(request.getName().trim())
				.joinCode(newJoinCode())
				.build();

		return CourseResponse.from(courseRepository.save(course));
	}

	/**
	 * 내 강좌 목록 (B5: 본인이 개설한 강좌만).
	 */
	@Override
	public List<CourseResponse> findMyCourses(Long instructorId) {
		return courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId).stream()
				.map(CourseResponse::from)
				.toList();
	}

	/**
	 * 강좌 상세 + 수강생 목록 (I2). 없는 강좌는 404, 다른 강사의 강좌는 403 (B5).
	 */
	@Override
	public CourseDetailResponse findMyCourseDetail(Long instructorId, Long courseId) {
		Course course = courseRepository.findById(courseId)
				.orElseThrow(CourseNotFoundException::new);

		if (!course.getInstructor().getId().equals(instructorId)) {
			throw new ForbiddenException();
		}

		List<EnrolledStudentResponse> students = enrollmentRepository.findByCourseIdOrderByJoinedAtAsc(courseId)
				.stream()
				.map(EnrolledStudentResponse::from)
				.toList();

		return CourseDetailResponse.of(course, students);
	}

	// 중복되지 않는 참여코드를 발급한다. (최종 안전장치는 DB의 join_code UNIQUE 제약)
	private String newJoinCode() {
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			String code = joinCodeGenerator.generate();
			if (!courseRepository.existsByJoinCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("참여코드를 발급하지 못했습니다. 다시 시도해 주세요.");
	}
}
