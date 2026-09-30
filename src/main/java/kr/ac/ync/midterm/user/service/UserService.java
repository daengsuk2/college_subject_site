package kr.ac.ync.midterm.user.service;

import kr.ac.ync.midterm.user.dto.request.SignupRequest;
import kr.ac.ync.midterm.user.dto.response.UserResponse;

public interface UserService {

	UserResponse signup(SignupRequest request);
}
