package kr.ac.ync.midterm.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.ac.ync.midterm.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmail(String email);
}
