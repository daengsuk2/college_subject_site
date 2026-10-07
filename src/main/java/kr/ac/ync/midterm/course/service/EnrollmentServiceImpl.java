package kr.ac.ync.midterm.course.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.course.domain.Course;
import kr.ac.ync.midterm.course.domain.Enrollment;
import kr.ac.ync.midterm.course.dto.request.EnrollRequest;
import kr.ac.ync.midterm.course.dto.response.EnrollmentResponse;
import kr.ac.ync.midterm.course.exception.AlreadyEnrolledException;
import kr.ac.ync.midterm.course.exception.InvalidJoinCodeException;
import kr.ac.ync.midterm.course.repository.CourseRepository;
import kr.ac.ync.midterm.course.repository.EnrollmentRepository;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

	private final CourseRepository courseRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final UserRepository userRepository;

	/**
	 * 참여코드로 수강 등록 (S1). 입력한 코드는 앞뒤 공백을 제거하고 대문자로 맞춰 비교한다.
	 */
	@Override
	@Transactional
	public EnrollmentResponse enroll(Long studentId, EnrollRequest request) {
		String joinCode = request.getJoinCode().trim().toUpperCase();

		Course course = courseRepository.findByJoinCode(joinCode)
				.orElseThrow(InvalidJoinCodeException::new);

		if (enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), studentId)) {
			throw new AlreadyEnrolledException();
		}

		User student = userRepository.getReferenceById(studentId);
		Enrollment enrollment = Enrollment.builder()
				.course(course)
				.student(student)
				.build();

		try {
			return EnrollmentResponse.from(enrollmentRepository.saveAndFlush(enrollment));
		} catch (DataIntegrityViolationException e) {
			// 동시 요청으로 UNIQUE(course_id, student_id) 제약에 걸린 경우
			throw new AlreadyEnrolledException();
		}
	}
}
