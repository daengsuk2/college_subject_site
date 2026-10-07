# 아이디어 · 추가 예정

필수 요구사항(U/I/S)을 모두 끝낸 뒤 여유가 있을 때 진행할 선택 기능입니다.
착수하면 ID를 붙여 `requirements.md`로 옮기고, 필요한 DB 변경은 README에 이유와 함께 기록합니다.
각 PR의 "개선점"에서 나온 내용은 이 문서에 계속 추가합니다.

| # | 아이디어 | 출처 | 상태 |
|---|----------|------|------|
| X1 | 마이페이지 (이름 · 비밀번호 변경) | 기획 | 아이디어 |
| X2 | 학번 · 이름 명단 확인 후 회원가입 | 기획 | 아이디어 |
| X3 | 내 수강 강좌 보기 | [PR #12](https://github.com/daengsuk2/college_subject_site/pull/12) (S1) | 아이디어 |
| X4 | 수강 등록 정정 (일정 시간 내) | [PR #12](https://github.com/daengsuk2/college_subject_site/pull/12) (S1) | 아이디어 |
| X5 | 강좌 삭제 (삭제 확인, 데이터 보존) | [PR #10](https://github.com/daengsuk2/college_subject_site/pull/10) (I1) | 아이디어 |
| X6 | 수강생 제거 | [PR #14](https://github.com/daengsuk2/college_subject_site/pull/14) (I2) | 아이디어 |
| X7 | 수강생 표 검색 · 정렬 (DataTables) | [PR #14](https://github.com/daengsuk2/college_subject_site/pull/14) (I2) | 아이디어 |
| X8 | 수강생 목록 내보내기 | [PR #14](https://github.com/daengsuk2/college_subject_site/pull/14) (I2) | 아이디어 |
| X9 | 로그인 시도 횟수 제한 (무차별 대입 방어) | [PR #17](https://github.com/daengsuk2/college_subject_site/pull/17) (보안 테스트) | 아이디어 |
| X10 | 세션 고정 · 쿠키 설정(Secure, HttpOnly) 검증 | [PR #17](https://github.com/daengsuk2/college_subject_site/pull/17) (보안 테스트) | 아이디어 |
| X11 | 의존 라이브러리(SB Admin 2의 jQuery 등) 취약점 점검 | [PR #17](https://github.com/daengsuk2/college_subject_site/pull/17) (보안 테스트) | 아이디어 |
| X12 | 과제 목록 검색 · 정렬 | [PR #19](https://github.com/daengsuk2/college_subject_site/pull/19) (I3-a) | 아이디어 |
| X13 | 과제 복사 (다른 강좌로 복제) | [PR #19](https://github.com/daengsuk2/college_subject_site/pull/19) (I3-a) | 아이디어 |
| X14 | 사이드바 메뉴 정리 (강좌 관리 / 과제 관리 중복 검토) | [PR #19](https://github.com/daengsuk2/college_subject_site/pull/19) (I3-a) | 아이디어 |
| X15 | 마감 임박 과제 표시 | [PR #21](https://github.com/daengsuk2/college_subject_site/pull/21) (S2) | 아이디어 |
| X16 | 내 과제 필터 · 검색 (강좌별, 상태별) | [PR #21](https://github.com/daengsuk2/college_subject_site/pull/21) (S2) | 아이디어 |
| X17 | 여러 파일 첨부 | [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) (I3-b) | 아이디어 |
| X18 | 첨부 삭제 버튼 (현재는 교체만 가능) | [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) (I3-b) | 아이디어 |
| X19 | 파일 내용(시그니처) 검사 | [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) (I3-b) | 아이디어 |
| X20 | 바이러스 검사 · 이미지 미리보기 | [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) (I3-b) | 아이디어 |
| X21 | 업로드 진행률 표시 | [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) (I3-b) | 아이디어 |
| X22 | DB 커밋 실패 시 삭제한 파일 복구 | [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) (I3-b) | 아이디어 |

## X1. 마이페이지
- 로그인한 사용자가 자기 이름과 비밀번호를 바꾼다.
- DB 컬럼 추가 없이 가능하다 (users 테이블만 사용).
- 고려: 비밀번호 변경 시 현재 비밀번호 확인, 8자 이상 검증, 이메일은 로그인 ID라 변경 제외.

## X2. 학번 · 이름 명단 확인 후 회원가입
- 현재는 학번을 자유롭게 입력해 누구나 가입할 수 있다.
- 수강 대상 학생의 학번 · 이름 명단을 DB에 넣어 두고, **명단과 일치하는 학생만** 가입할 수 있게 한다.
- 고려할 점
  - **테이블이 6개가 된다.** 슬라이드가 "테이블 5개"를 명시했으므로 추가해도 되는지 강사님께 확인이 필요하다.
  - 명단 관리 방법: 강사가 업로드 / 초기 데이터로 입력 / 관리 화면 등 (미정)
  - 이미 가입한 학번의 중복 가입 방지 (그때 `student_no`에 UNIQUE가 필요할 수 있음)
  - **실제 학생 이름과 학번은 공개 저장소에 올리면 안 된다.** 명단 데이터는 `.gitignore`로 제외하고 예시는 가짜 데이터로 만든다.

## X3. 내 수강 강좌 보기
- 출처: [PR #12](https://github.com/daengsuk2/college_subject_site/pull/12) 개선점 — "내 수강 보는 기능"
- 학생이 자신이 수강 등록한 강좌 목록을 볼 수 있게 한다. (현재 S1은 등록만 가능하고, 등록 결과는 등록 직후 안내 메시지로만 확인된다.)
- DB 변경 없이 가능하다 (enrollment에서 본인 행을 조회).
- 고려할 점
  - 요구사항 S2(내 과제 목록)가 수강 강좌의 과제를 보여 주므로, 화면을 합칠지 별도로 둘지 결정이 필요하다.
  - 강좌명, 담당 강사, 등록일 정도를 표로 보여 준다.

## X4. 수강 등록 정정 (일정 시간 내)
- 출처: [PR #12](https://github.com/daengsuk2/college_subject_site/pull/12) 개선점 — "일정 시간 내 정정 기능"
- 해석: 학생이 참여코드를 잘못 입력해 다른 강좌에 등록했을 때, 등록 후 **일정 시간 안에** 취소(정정)할 수 있게 한다.
- 고려할 점
  - 정정 가능 시간을 얼마로 할지 (예: 등록 후 10분, 1시간). `enrollment.joined_at`으로 판단할 수 있어 컬럼 추가는 필요 없다.
  - 정정이 가능한 시간이 지나면 취소할 수 없다는 점을 화면에 안내한다.
  - 과제를 이미 제출한 강좌는 취소할 수 없게 막을지 결정이 필요하다 (제출 데이터와의 관계).
  - 요구사항 S1에는 취소가 없으므로 선택 기능으로 둔다.

## X5. 강좌 삭제 (삭제 확인, 데이터 보존)
- 출처: [PR #10](https://github.com/daengsuk2/college_subject_site/pull/10) 개선 사항 — "강좌 삭제(삭제 확인, 등록 확인 삭제) + 한번 등록된 강좌는 삭제해도 계속 저장"
- 해석
  1. 강사가 본인의 강좌를 삭제할 수 있다.
  2. 삭제 전에 확인 단계를 둔다. 수강 등록된 학생이 있는지도 함께 확인해 안내한다.
  3. 한 번 등록(수강생이 생긴)된 강좌는 **삭제해도 데이터는 계속 보관**한다.
- 고려할 점
  - 3번을 지키려면 실제로 지우지 않고 삭제된 것으로 표시하는 **논리 삭제**가 필요하다. 그러면 course에 삭제 여부 컬럼(예: `deleted_at`)을 추가하게 되므로, README의 "추가한 컬럼과 이유"에 기록해야 한다.
  - 삭제된 강좌를 목록에서 숨기고, 이미 등록한 학생의 수강 정보와 제출 기록이 어떻게 보이는지 정해야 한다.
  - 과제 삭제 규칙 B8("제출이 1건이라도 있는 과제는 삭제 불가")과 일관되게, 수강생이나 과제가 있는 강좌의 삭제를 막는 방식과 논리 삭제 중 어느 쪽으로 할지 결정이 필요하다.
  - 요구사항 I1에는 삭제가 없으므로 선택 기능으로 둔다.

## X6. 수강생 제거
- 출처: [PR #14](https://github.com/daengsuk2/college_subject_site/pull/14) I2 개선점 — 수강생 제거 기능
- 강사가 잘못 등록된 학생을 수강생 목록에서 제거한다.
- 고려할 점
  - 제거해도 이미 제출한 기록을 어떻게 할지 정해야 한다 (X4의 정정 규칙과 함께 결정).
  - `enrollment` 행 삭제만으로 가능해 컬럼 추가는 필요 없다.

## X7. 수강생 표 검색 · 정렬 (DataTables)
- 출처: [PR #14](https://github.com/daengsuk2/college_subject_site/pull/14) I2 개선점 — 표 검색 · 정렬
- 수강생이 많아질 때를 대비해 이름 · 학번 검색과 열 정렬을 넣는다.
- 고려할 점
  - SB Admin 2에 포함된 DataTables를 쓰면 서버 변경 없이 가능하다.
  - 정렬 기본값(등록 순서)은 유지한다.

## X8. 수강생 목록 내보내기
- 출처: [PR #14](https://github.com/daengsuk2/college_subject_site/pull/14) I2 개선점 — 목록 내보내기
- 수강생 목록을 CSV 등으로 내려받게 한다.
- 고려할 점
  - 학번 · 이름 등 개인정보이므로 해당 강좌의 강사만 받을 수 있어야 한다 (B5).
  - 엑셀에서 수식으로 실행되지 않도록 `=`, `+` 등으로 시작하는 값을 처리해야 한다.

## X9. 로그인 시도 횟수 제한 (무차별 대입 방어)
- 출처: [PR #17](https://github.com/daengsuk2/college_subject_site/pull/17) 보안 테스트 개선점 — 무차별 대입 방어
- 로그인에 연속으로 실패하면 일정 시간 막아 비밀번호 대입 공격을 늦춘다.
- 고려할 점
  - 계정 기준 / IP 기준 중 선택이 필요하다.
  - 잠금 상태를 저장하려면 컬럼이나 메모리 저장소가 필요하다 (DB 변경이 생기면 README에 기록).

## X10. 세션 고정 · 쿠키 설정(Secure, HttpOnly) 검증
- 출처: [PR #17](https://github.com/daengsuk2/college_subject_site/pull/17) 보안 테스트 개선점 — 세션 · 쿠키
- 로그인 전후 세션 ID 교체(세션 고정 방어)와 쿠키의 `HttpOnly` · `Secure` 설정을 테스트로 확인한다.
- 고려할 점
  - `Secure`는 HTTPS에서만 의미가 있어 배포 방식에 따라 달라진다.

## X11. 의존 라이브러리(SB Admin 2의 jQuery 등) 취약점 점검
- 출처: [PR #17](https://github.com/daengsuk2/college_subject_site/pull/17) 보안 테스트 개선점 — 라이브러리 점검
- SB Admin 2에 포함된 jQuery 등 정적 라이브러리의 알려진 취약점을 점검한다.
- 고려할 점
  - Gradle 의존성도 함께 점검 대상이다.
  - 버전을 올리면 화면이 깨질 수 있어 올린 뒤 화면을 다시 확인해야 한다.

## X12. 과제 목록 검색 · 정렬
- 출처: [PR #19](https://github.com/daengsuk2/college_subject_site/pull/19) I3-a 개선점 — 검색 · 정렬
- 강사의 과제 목록을 제목으로 검색하고 열 기준으로 정렬한다.
- 고려할 점
  - X7과 같은 방식(DataTables)으로 처리할 수 있다.

## X13. 과제 복사 (다른 강좌로 복제)
- 출처: [PR #19](https://github.com/daengsuk2/college_subject_site/pull/19) I3-a 개선점 — 과제 복사
- 기존 과제를 다른 강좌로 복제해 제목 · 내용 · 배점을 재사용한다.
- 고려할 점
  - 기간은 새로 입력받는다 (B7 검증 적용).
  - 첨부파일을 함께 복사할지 정해야 한다.

## X14. 사이드바 메뉴 정리 (강좌 관리 / 과제 관리 중복 검토)
- 출처: [PR #19](https://github.com/daengsuk2/college_subject_site/pull/19) I3-a 개선점 — 메뉴 역할 중복
- 강좌 관리와 과제 관리가 같은 역할처럼 느껴져 하나로 합치거나 구분을 분명히 한다.
- 고려할 점
  - 과제는 강좌 상세에서도 관리되므로 메뉴를 줄일 수 있는지 검토한다.

## X15. 마감 임박 과제 표시
- 출처: [PR #21](https://github.com/daengsuk2/college_subject_site/pull/21) S2 개선점 — 마감 임박 표시 (S5)
- 마감이 가까운 과제를 눈에 띄게 표시한다. 요구사항 S5와 같은 기능이다.
- 고려할 점
  - 임박 기준(예: 24시간 이내)을 정해야 한다.
  - `Clock`과 종료일시로 계산하므로 컬럼 추가는 필요 없다.

## X16. 내 과제 필터 · 검색 (강좌별, 상태별)
- 출처: [PR #21](https://github.com/daengsuk2/college_subject_site/pull/21) S2 개선점 — 필터 · 검색
- 학생의 내 과제 목록을 강좌별 · 상태별로 걸러 보고 검색한다.
- 고려할 점
  - 제출 · 점수 보기(W3~)가 생기면 필터 항목에 제출 여부를 더할 수 있다.

## X17. 여러 파일 첨부
- 출처: [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) I3-b 개선점 — 여러 파일
- 과제 하나에 파일을 여러 개 첨부할 수 있게 한다.
- 고려할 점
  - 지금은 `assignment`에 파일 컬럼이 하나라 별도 첨부 테이블이 필요하다 (테이블 5개 제한을 강사님께 확인).

## X18. 첨부 삭제 버튼 (현재는 교체만 가능)
- 출처: [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) I3-b 개선점 — 첨부 삭제
- 지금은 새 파일로 교체만 가능하고 파일 없이 되돌리는 방법이 없다. 삭제 버튼을 추가한다.
- 고려할 점
  - 파일 삭제와 DB 값 비우기를 함께 처리해야 한다.

## X19. 파일 내용(시그니처) 검사
- 출처: [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) I3-b 개선점 — 파일 내용 검사
- 확장자만이 아니라 파일 앞부분(시그니처)도 확인해 이름만 바꾼 파일을 거른다.
- 고려할 점
  - 지금도 다운로드는 항상 내려받기로 응답해 파일이 실행되지는 않는다.

## X20. 바이러스 검사 · 이미지 미리보기
- 출처: [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) I3-b 개선점 — 검사 · 미리보기
- 업로드 파일의 바이러스 검사와 이미지 미리보기를 추가한다.
- 고려할 점
  - 검사 도구 연동이 필요하다.
  - 미리보기는 인라인 표시가 보안에 영향을 주므로 별도 점검이 필요하다.

## X21. 업로드 진행률 표시
- 출처: [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) I3-b 개선점 — 진행률
- 큰 파일을 올릴 때 진행률을 보여 준다.
- 고려할 점
  - 10MB 이하 제한이라 우선순위는 낮다.

## X22. DB 커밋 실패 시 삭제한 파일 복구
- 출처: [PR #23](https://github.com/daengsuk2/college_subject_site/pull/23) I3-b 개선점 — 삭제 복구
- 파일을 먼저 지운 뒤 DB 커밋이 실패하면 지운 파일을 되살릴 수 없다. 드문 경우다.
- 고려할 점
  - 삭제를 커밋 이후로 미루는 방식(트랜잭션 후처리)을 검토한다.
