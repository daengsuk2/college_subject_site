package kr.ac.ync.midterm.user.dto.response;

import kr.ac.ync.midterm.user.domain.Role;
import kr.ac.ync.midterm.user.domain.User;

public record UserResponse(Long id, String email, String name, String studentNo, Role role) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getStudentNo(),
				user.getRole());
	}
}
