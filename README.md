# 과제 관리 시스템 (자바스프링 중간고사 프로젝트)

강사가 강좌를 개설하고 과제를 등록하면, 학생이 참여코드로 수강 등록한 뒤 기간 내에 과제를 제출하고,
강사가 채점·피드백을 남기는 웹 서비스입니다. 영남이공대 자바스프링 중간고사 프로젝트로 제작했습니다.

## 📌 필독 — 설계 문서

> [!IMPORTANT]
> 코드를 보기 전에 아래 설계 문서를 먼저 읽어 주세요. 기능(U/I/S)과 규칙(B1~B8)의 기준, DB 구조, 화면 구성이 모두 여기에 있습니다.

| 문서 | 내용 |
|------|------|
| [요구사항 명세](docs/requirements.md) | 기능(U/I/S)별 입력 · 처리 · 인수 기준, 비즈니스 규칙 |
| [ERD](docs/erd.md) | 테이블 5개, 관계, 제약 조건, 규칙과 데이터의 연결 |
| [화면 설계](docs/screens.md) | 화면 목록 · URL, 화면별 구성, 화면 이동 흐름 |

함께 보면 좋은 문서: [시연 시나리오 (5분)](docs/demo.md) · [아이디어 · 추가 예정](docs/ideas.md)

## 🗓 주간 진행 보고

매주 끝에 그 주의 진행 결과를 정리해 `docs/`에 올립니다. (체크포인트 · 화면 · PR · 이슈 · 결정 사항 · 다음 주 계획)

| 주차 | 주제 | 범위 | 상태 | 보고서 |
|:----:|------|------|:----:|--------|
| W1 | 설계 + 기반 | U1~U4 | ✅ 완료 | [W1 보고서](docs/progress_w1.md) |
| W2 | 강좌 · 과제 등록 | I1~I3 · S1~S2 | ✅ 완료 | [W2 보고서](docs/progress_w2.md) |
| W3 | 제출 · 채점 | I4~I5 · S3~S4 | ✅ 완료 | [W3 보고서](docs/progress_w3.md) |
| W4 | 통계 + 마무리 | I6 · S5 | ✅ 완료 | [W4 보고서](docs/progress_w4.md) |

## 기술 스택

| 구분 | 내용 |
|------|------|
| 백엔드 | Spring Boot 3.5.6, Java 21, Spring Data JPA |
| DB | PostgreSQL |
| 인증 | Spring Security 6 (세션 기반 폼 로그인, BCrypt) |
| 화면 | Thymeleaf + Layout Dialect + thymeleaf-extras-springsecurity6 (서버 렌더링) |
| UI | SB Admin 2 v4.1.4 (Bootstrap 4.6, jQuery, Font Awesome 5) |
| 차트 · 표 | Chart.js, DataTables (SB Admin 2 내장) |
| 빌드 | Gradle |

## 실행 방법

### 1. 준비물
- JDK 21 (Gradle 툴체인이 21로 빌드하므로 없으면 설치 필요)
- Docker (PostgreSQL 실행용)

### 2. DB 실행 (Docker)
`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다. (`.env`는 git에 올라가지 않습니다.)
```bash
cp .env.example .env
docker compose up -d
```
컨테이너 이름은 `midterm-db`, 데이터베이스 `midterm`이 자동으로 만들어집니다.

### 3. `.env` 설정
앱은 프로젝트 루트의 `.env`를 **UTF-8로** 직접 읽습니다 (`DotenvEnvironmentPostProcessor`), 그래서 한글 값(예: 강사 이름)도 그대로 쓸 수 있습니다.
같은 이름의 환경변수가 있으면 환경변수가 우선합니다. 파일은 UTF-8로 저장하세요.

| 항목 | 설명 | 기본값 |
|------|------|--------|
| `DB_USERNAME` | DB 계정 (Docker 컨테이너 생성에도 사용) | `postgres` |
| `DB_PASSWORD` | DB 비밀번호 (Docker 컨테이너 생성에도 사용) | (빈 값) |
| `DB_URL` | DB 주소. 보통 바꿀 필요 없음 | `jdbc:postgresql://localhost:5432/midterm` |
| `INSTRUCTOR_EMAIL` | 강사 초기 계정 이메일 | (빈 값) |
| `INSTRUCTOR_NAME` | 강사 초기 계정 이름 (화면 상단에 표시) | `강사` |
| `INSTRUCTOR_PASSWORD` | 강사 초기 계정 비밀번호. 비어 있으면 계정을 만들지 않음 | (빈 값) |

`DB_PASSWORD`는 DB가 **처음 만들어질 때만** 적용되고, `INSTRUCTOR_*`도 강사 계정이 **처음 생성될 때만** 적용됩니다.
값을 바꾸려면 볼륨(`docker compose down -v`)이나 해당 계정을 지우고 다시 만들어야 합니다.

### 4. 실행
```bash
./gradlew bootRun
```
Windows에서는 `gradlew.bat bootRun`. 실행 후 http://localhost:8080 에 접속합니다.

### 5. 테스트
테스트는 개발 DB와 분리된 `midterm_test` DB를 사용합니다. 처음 한 번만 만들어 줍니다.
```bash
docker exec midterm-db psql -U postgres -c "CREATE DATABASE midterm_test"
```
```bash
./gradlew test
```
테스트도 `.env`의 `DB_USERNAME`, `DB_PASSWORD`를 사용하며, 테스트가 끝나면 테이블은 자동으로 삭제됩니다.
테스트에서는 강사 초기 계정을 자동 생성하지 않습니다.

## 프로젝트 구조

```
src/main/java/kr/ac/ync/midterm/
├─ MidtermApplication.java
├─ global/                           # 공통
│  ├─ config/SecurityConfig.java     # 폼 로그인, 접근 규칙, BCrypt
│  └─ exception/                     # CustomException, ForbiddenException, GlobalExceptionHandler
├─ user/                             # 기능별 패키지 (아래 하위 패키지 구성은 모두 동일)
│  ├─ controller/  AuthController (/login, /signup)
│  ├─ domain/      User, Role
│  ├─ dto/request, dto/response
│  ├─ exception/   DuplicateEmailException
│  ├─ repository/  UserRepository
│  └─ service/     UserService, UserServiceImpl
├─ course/         강좌 개설 · 수강 등록 · 수강생 목록 (Course, Enrollment)
├─ assignment/     과제 등록 · 수정 · 삭제, 첨부파일, 내 과제 (Assignment, AssignmentStatus)
├─ submission/     제출 · 재제출, 제출 파일, 제출 현황, 채점, 내 제출 (Submission, SubmissionStatus)
├─ stats/          강사 통계 대시보드 (/instructor/stats)
├─ dashboard/      역할별 대시보드 집계 (학생 S5, 강사)
└─ home/controller/HomeController.java  # 대시보드 (/)

src/test/java/kr/ac/ync/midterm/       # main과 같은 패키지 구조로 테스트 작성
├─ support/BaseController.java         # MockMvc + Security 공통 설정
└─ user/  repository, service, controller 테스트

src/main/resources/
├─ application.yml
├─ static/                           # SB Admin 2 (css, js, vendor, img)
└─ templates/
   ├─ layout/layout.html             # 공통 레이아웃
   ├─ fragments/                     # sidebar(역할별), topbar, footer
   ├─ auth/                          # login, signup
   ├─ error/                         # 403, 404, 공통 오류
   └─ home.html
```

각 화면은 `layout:decorate="~{layout/layout}"` 와 `layout:fragment="content"` 만 작성하면
사이드바·상단바가 자동으로 붙습니다. 사이드바는 `sec:authorize`로 강사/학생 메뉴를 분리합니다.

## 기능 구현 현황

커밋 메시지와 프롬프트 로그의 ID와 같은 기준입니다.

### 공통
| ID | 기능 | 상태 |
|----|------|------|
| U1 | 학생 회원가입 (학번 · 이름 · 이메일 · 비밀번호) | 완료 (테스트 통과, 로그인 연동은 U2) |
| U2 | 로그인 · 로그아웃 (Spring Security 폼 로그인) | 완료 (DB 사용자 로그인, 테스트 통과) |
| U3 | 역할별 메뉴 (강사 / 학생 사이드바 분리) | 완료 (사이드바 분리 + `/instructor/**` · `/student/**` 서버 접근 제한 403, 테스트 통과) |
| U4 | 강사 계정 (초기 데이터로 생성, 가입 불가) | 완료 (`.env`의 `INSTRUCTOR_*`로 생성) |
| X1 | 마이페이지 (이름 · 비밀번호 변경, 선택 기능) | 완료 (`/mypage`, DB 변경 없음) |
| X3 | 내 수강 강좌 보기 (강좌 · 담당 강사 · 등록일 · 시작된 과제 수 · 제출한 과제 수, 선택 기능) | 완료 (`/student/courses`, DB 변경 없음) |
| X18 · X23 | 첨부 파일 삭제 (강사의 과제 첨부, 학생의 제출 파일, 선택 기능) | 완료 (DB 변경이 확정된 뒤 서버 파일 삭제, B1 · B2 · B4 · B5 적용) |
| X8 · X32 | 표 내보내기 CSV (수강생 · 제출 현황 · 통계, 선택 기능) | 완료 (UTF-8 BOM, 엑셀 수식 주입 방지, 본인 강좌만) |
| X7 · X12 · X16 | 표 검색 · 정렬 (수강생 · 과제 · 강좌 · 제출 현황 · 내 과제 · 내 제출, 내 과제는 강좌 · 상태 필터, 선택 기능) | 완료 (DataTables, 브라우저에서만 처리) |

### 강사
| ID | 기능 | 상태 |
|----|------|------|
| I1 | 강좌 개설 (참여코드 자동 발급) | 완료 |
| I2 | 수강생 목록 | 완료 |
| I3 | 과제 등록 · 수정 · 삭제 | 완료 |
| I4 | 제출 현황 (제출 / 미제출) | 완료 |
| I5 | 채점 · 피드백 | 완료 |
| I6 | 통계 대시보드 | 완료 |

### 학생
| ID | 기능 | 상태 |
|----|------|------|
| S1 | 수강 등록 (참여코드) | 완료 |
| S2 | 내 과제 목록 (예정 · 진행중 · 마감 배지) | 완료 |
| S3 | 과제 제출 · 재제출 (내용 + 첨부파일) | 완료 |
| S4 | 결과 확인 (점수 · 피드백) | 완료 |
| S5 | 내 통계 | 완료 |

## DB 설계 (테이블 5개)

| 테이블 | 주요 컬럼 |
|--------|-----------|
| users | id, email(UNIQUE), password(BCrypt), name, student_no(nullable), role(INSTRUCTOR/STUDENT) |
| course | id, instructor_id(FK users), name, join_code(UNIQUE), created_at |
| enrollment | id, course_id(FK), student_id(FK users), joined_at — UNIQUE(course_id, student_id) |
| assignment | id, course_id(FK), title, content, start_at, end_at, max_score, file_path(nullable), created_at |
| submission | id, assignment_id(FK), student_id(FK users), content, file_path(nullable), submitted_at, status(SUBMITTED/GRADED), score, feedback, graded_at — UNIQUE(assignment_id, student_id) |

관계: users(강사) 1:N course, course 1:N assignment, course N:M users(학생)은 enrollment로 연결,
assignment 1:N submission, users(학생) 1:N submission.

### 추가한 컬럼과 이유
| 테이블.컬럼 | 이유 | 추가한 기능 |
|-------------|------|-------------|
| assignment.original_filename (nullable) | 첨부파일은 서버에 UUID 이름으로 저장하므로(`file_path`), 다운로드 때 사용자가 올린 원래 파일명을 보여 주기 위해 | I3-b 과제 첨부파일 |
| submission.original_filename (nullable) | 제출 파일도 서버에 UUID 이름으로 저장하므로(`file_path`), 다운로드 때 학생이 올린 원래 파일명을 보여 주기 위해 | S3-b 제출 첨부파일 |

컬럼을 더 추가하면 여기에 이유와 함께 기록합니다.

## 비즈니스 규칙

모든 규칙은 화면이 아니라 서버(Service)에서 검사합니다.

| # | 규칙 | 위반 시 처리 | 상태 |
|---|------|--------------|------|
| B1 | 제출은 시작일시 ≤ 현재 ≤ 종료일시 일 때만 가능 | "제출 기간이 아닙니다" 알림 | 완료 |
| B2 | 수강 등록된 학생만 과제 열람 · 제출 | 403 페이지 | 완료 |
| B3 | 과제당 학생 1건, 기간 내 재제출은 덮어쓰기 | 기존 제출 수정 | 완료 |
| B4 | 채점 완료(GRADED)된 제출은 재제출 불가 | "채점 완료된 과제" 알림 | 완료 |
| B5 | 강사는 본인 강좌의 과제만 수정 · 삭제 · 채점 | 403 페이지 | 완료 |
| B6 | 점수는 0 이상 배점(max_score) 이하 | 폼 에러 메시지 | 완료 |
| B7 | 종료일시는 시작일시보다 뒤 | 폼 에러 메시지 | 완료 |
| B8 | 제출이 1건이라도 있는 과제는 삭제 불가 | "제출물이 있어 삭제 불가" 알림 | 완료 |

## 화면 · URL

| 역할 | URL | 화면 |
|------|-----|------|
| 공통 | `/login` · `/signup` | 로그인 · 학생 회원가입 |
| 공통 | `/` | 역할별 대시보드 |
| 공통 | `/mypage` | 마이페이지 (이름 · 비밀번호 변경) |
| 강사 | `/instructor/courses` · `/{id}` | 강좌 목록 · 상세 (참여코드, 수강생) |
| 강사 | `/instructor/assignments/new` · `/{id}/edit` | 과제 등록 · 수정 |
| 강사 | `/instructor/assignments/{id}/submissions` | 제출 현황 + 미제출자 |
| 강사 | `/instructor/submissions/{id}/grade` | 채점 · 피드백 |
| 강사 | `/instructor/stats` | 통계 대시보드 |
| 강사 | `/instructor/courses/{id}/students.csv` · `/instructor/assignments/{id}/submissions.csv` · `/instructor/stats/assignments.csv` · `/instructor/stats/missing.csv` | CSV 내려받기 |
| 학생 | `/student/courses` | 내 수강 강좌 (수강 등록 후 이동) |
| 학생 | `/student/courses/join` | 참여코드 입력 |
| 학생 | `/student/assignments` · `/{id}` | 내 과제 목록 · 상세 + 제출 |
| 학생 | `/student/submissions` | 내 제출 · 점수 · 피드백 |
| 공통 | `/error/403` · `/error/404` | 권한 없음 · 없는 페이지 |

## 초기 계정

강사 계정은 가입할 수 없고, 앱 시작 시 `.env`의 `INSTRUCTOR_EMAIL` · `INSTRUCTOR_NAME` · `INSTRUCTOR_PASSWORD`로 만들어집니다.
(비밀번호가 비어 있으면 만들지 않고, 이미 있으면 건너뜁니다.) 비밀번호는 저장소에 기록하지 않으니 시연 환경의 `.env`를 확인하세요.
마이페이지(`/mypage`)에서 강사가 비밀번호를 바꾸면 DB의 값만 바뀌고 `.env`는 그대로입니다. 이후 `.env`의 값은 실제 비밀번호와 달라지지만, 앱은 `.env`로 기존 계정을 덮어쓰지 않습니다.

학생 계정은 `/signup`에서 가입합니다.

## 시연

규칙 B1~B8을 직접 시험하는 5분 시연 시나리오(시연 전 준비 데이터, 시간표, 규칙별 확인 방법, 개발자도구로 강제 요청을 보내는 방법)는 [docs/demo.md](docs/demo.md)에 있습니다.
시연용 데이터는 가짜 이름 · 학번 · 이메일만 사용합니다. 저장소가 공개이므로 실제 학생 정보는 올리지 않습니다.

## 알려진 한계 · 남은 과제

- 제출 파일과 과제 첨부파일은 확장자만 검사합니다. 다운로드는 항상 내려받기로만 응답합니다.
- 다른 강사의 강좌에 접근하는 403(B5)은 강사 계정이 하나뿐인 환경에서는 테스트로 확인했습니다. 자세한 대체 방법은 [시연 문서](docs/demo.md)에 있습니다.
- 선택 기능과 PR의 개선점은 [아이디어 · 추가 예정](docs/ideas.md)에 정리되어 있습니다.