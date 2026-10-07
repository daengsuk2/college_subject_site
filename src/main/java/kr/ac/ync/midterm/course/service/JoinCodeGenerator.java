package kr.ac.ync.midterm.course.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 참여코드 생성기. 6자리, 대문자 + 숫자, 헷갈리는 문자(I, O, 0, 1)는 제외한다.
 */
@Component
public class JoinCodeGenerator {

	private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int LENGTH = 6;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder sb = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
		}
		return sb.toString();
	}
}
