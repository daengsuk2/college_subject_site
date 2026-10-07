package kr.ac.ync.midterm.global.storage;

/**
 * 저장된 파일 정보. storedName은 서버에 저장된 UUID 이름, originalName은 사용자가 올린 원본 이름(표시용)이다.
 */
public record StoredFile(String storedName, String originalName) {
}
