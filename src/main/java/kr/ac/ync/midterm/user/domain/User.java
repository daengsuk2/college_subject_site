package kr.ac.ync.midterm.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String name;

	@Column(name = "student_no")
	private String studentNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Builder
	private User(String email, String password, String name, String studentNo, Role role) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.studentNo = studentNo;
		this.role = role;
	}

	/** 이름 변경 (마이페이지). 이메일 · 학번 · 역할은 바꾸는 메서드를 두지 않는다. */
	public void changeName(String name) {
		this.name = name;
	}

	/** 비밀번호 변경 (마이페이지). 이미 BCrypt로 인코딩된 값을 받는다. */
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	/**
	 * 이메일은 공백 제거 후 소문자로 저장·조회한다. (가입, 로그인, 초기 데이터에서 공통 사용)
	 */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}
}
