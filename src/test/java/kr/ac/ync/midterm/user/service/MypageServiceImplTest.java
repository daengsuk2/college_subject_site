package kr.ac.ync.midterm.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;
import kr.ac.ync.midterm.user.dto.request.PasswordChangeRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;
import kr.ac.ync.midterm.user.exception.PasswordMismatchException;
import kr.ac.ync.midterm.user.exception.SamePasswordException;
import kr.ac.ync.midterm.user.exception.WrongCurrentPasswordException;
import kr.ac.ync.midterm.user.repository.UserRepository;

/**
 * 마이페이지 (X1): 이름 변경, 비밀번호 변경 규칙과 본인 데이터만 바뀌는지 확인한다.
 */
@SpringBootTest
@Transactional
class MypageServiceImplTest {

	private static final String CURRENT = "password123";

	@Autowired
	private MypageService mypageService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User student;
	private User other;

	@BeforeEach
	void setUp() {
		student = savedUser("student@test.com", Role.STUDENT, "20260001", "홍길동");
		other = savedUser("other@test.com", Role.STUDENT, "20260002", "김철수");
	}

	private User savedUser(String email, Role role, String studentNo, String name) {
		return userRepository.save(User.builder().email(email).password(passwordEncoder.encode(CURRENT))
				.name(name).studentNo(studentNo).role(role).build());
	}

	private PasswordChangeRequest request(String current, String newPassword, String confirm) {
		PasswordChangeRequest request = new PasswordChangeRequest();
		request.setCurrentPassword(current);
		request.setNewPassword(newPassword);
		request.setNewPasswordConfirm(confirm);
		return request;
	}

	// ---------- 내 정보 · 이름 ----------

	@Test
	@DisplayName("findMe - 로그인한 본인의 정보가 반환됨")
	void findMe() {
		UserResponse me = mypageService.findMe(student.getId());

		assertThat(me.email()).isEqualTo("student@test.com");
		assertThat(me.name()).isEqualTo("홍길동");
		assertThat(me.studentNo()).isEqualTo("20260001");
		assertThat(me.role()).isEqualTo(Role.STUDENT);
	}

	@Test
	@DisplayName("changeName - 이름이 바뀌고 앞뒤 공백은 제거됨")
	void changeName_trims() {
		UserResponse changed = mypageService.changeName(student.getId(), "  새이름  ");

		assertThat(changed.name()).isEqualTo("새이름");
		assertThat(userRepository.findById(student.getId()).orElseThrow().getName()).isEqualTo("새이름");
	}

	@Test
	@DisplayName("changeName - 빈 이름 · 공백뿐인 이름 · null은 거부되고 이름이 그대로임")
	void changeName_blankRejected() {
		for (String invalid : new String[] { "", "   ", null }) {
			assertThatThrownBy(() -> mypageService.changeName(student.getId(), invalid))
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThat(userRepository.findById(student.getId()).orElseThrow().getName()).isEqualTo("홍길동");
	}

	@Test
	@DisplayName("changeName - 이름만 바뀌고 이메일 · 학번 · 역할 · 비밀번호는 그대로, 다른 사용자도 그대로")
	void changeName_onlyName() {
		String passwordBefore = student.getPassword();

		mypageService.changeName(student.getId(), "새이름");

		User after = userRepository.findById(student.getId()).orElseThrow();
		assertThat(after.getEmail()).isEqualTo("student@test.com");
		assertThat(after.getStudentNo()).isEqualTo("20260001");
		assertThat(after.getRole()).isEqualTo(Role.STUDENT);
		assertThat(after.getPassword()).isEqualTo(passwordBefore);
		assertThat(userRepository.findById(other.getId()).orElseThrow().getName()).isEqualTo("김철수");
	}

	@Test
	@DisplayName("changeName - 강사 계정도 이름을 바꿀 수 있음")
	void changeName_instructor() {
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null, "김강사");

		UserResponse changed = mypageService.changeName(instructor.getId(), "박강사");

		assertThat(changed.name()).isEqualTo("박강사");
		assertThat(changed.studentNo()).isNull();
		assertThat(changed.role()).isEqualTo(Role.INSTRUCTOR);
	}

	// ---------- 비밀번호 ----------

	@Test
	@DisplayName("changePassword - 새 비밀번호가 BCrypt로 저장되고 새 비밀번호로만 일치함")
	void changePassword_bcrypt() {
		mypageService.changePassword(student.getId(), request(CURRENT, "newpassword1", "newpassword1"));

		User after = userRepository.findById(student.getId()).orElseThrow();
		assertThat(after.getPassword()).startsWith("$2").isNotEqualTo("newpassword1");
		assertThat(passwordEncoder.matches("newpassword1", after.getPassword())).isTrue();
		assertThat(passwordEncoder.matches(CURRENT, after.getPassword())).isFalse();
	}

	@Test
	@DisplayName("changePassword - 현재 비밀번호가 틀리면 거부되고 비밀번호가 그대로임")
	void changePassword_wrongCurrent() {
		String before = student.getPassword();

		assertThatThrownBy(() -> mypageService.changePassword(student.getId(),
				request("wrongpassword", "newpassword1", "newpassword1")))
				.isInstanceOf(WrongCurrentPasswordException.class)
				.hasMessage("현재 비밀번호가 올바르지 않습니다.");
		assertThatThrownBy(() -> mypageService.changePassword(student.getId(),
				request(null, "newpassword1", "newpassword1")))
				.isInstanceOf(WrongCurrentPasswordException.class);

		assertThat(userRepository.findById(student.getId()).orElseThrow().getPassword()).isEqualTo(before);
	}

	@Test
	@DisplayName("changePassword - 새 비밀번호는 8자부터 허용되고 7자는 거부됨 (경계)")
	void changePassword_lengthBoundary() {
		assertThatThrownBy(() -> mypageService.changePassword(student.getId(), request(CURRENT, "abcdefg", "abcdefg")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(passwordEncoder.matches(CURRENT,
				userRepository.findById(student.getId()).orElseThrow().getPassword())).isTrue();

		mypageService.changePassword(student.getId(), request(CURRENT, "abcdefgh", "abcdefgh"));

		assertThat(passwordEncoder.matches("abcdefgh",
				userRepository.findById(student.getId()).orElseThrow().getPassword())).isTrue();
	}

	@Test
	@DisplayName("changePassword - 새 비밀번호와 확인이 다르면 거부됨")
	void changePassword_mismatch() {
		assertThatThrownBy(() -> mypageService.changePassword(student.getId(),
				request(CURRENT, "newpassword1", "newpassword2")))
				.isInstanceOf(PasswordMismatchException.class);
		assertThatThrownBy(() -> mypageService.changePassword(student.getId(),
				request(CURRENT, "newpassword1", null)))
				.isInstanceOf(PasswordMismatchException.class);

		assertThat(passwordEncoder.matches(CURRENT,
				userRepository.findById(student.getId()).orElseThrow().getPassword())).isTrue();
	}

	@Test
	@DisplayName("changePassword - 새 비밀번호가 현재 비밀번호와 같으면 거부됨")
	void changePassword_same() {
		assertThatThrownBy(() -> mypageService.changePassword(student.getId(), request(CURRENT, CURRENT, CURRENT)))
				.isInstanceOf(SamePasswordException.class);
	}

	@Test
	@DisplayName("changePassword - 본인 비밀번호만 바뀌고 다른 사용자는 그대로, 이름 · 이메일도 그대로")
	void changePassword_onlySelf() {
		String otherBefore = other.getPassword();

		mypageService.changePassword(student.getId(), request(CURRENT, "newpassword1", "newpassword1"));

		assertThat(userRepository.findById(other.getId()).orElseThrow().getPassword()).isEqualTo(otherBefore);
		User after = userRepository.findById(student.getId()).orElseThrow();
		assertThat(after.getName()).isEqualTo("홍길동");
		assertThat(after.getEmail()).isEqualTo("student@test.com");
	}

	@Test
	@DisplayName("changePassword - 강사 계정도 비밀번호를 바꿀 수 있음")
	void changePassword_instructor() {
		User instructor = savedUser("teacher@test.com", Role.INSTRUCTOR, null, "김강사");

		mypageService.changePassword(instructor.getId(), request(CURRENT, "newpassword1", "newpassword1"));

		assertThat(passwordEncoder.matches("newpassword1",
				userRepository.findById(instructor.getId()).orElseThrow().getPassword())).isTrue();
	}
}
