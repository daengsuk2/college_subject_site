# 과제 관리 시스템 (자바스프링 중간고사 프로젝트)

강사가 강좌를 개설하고 과제를 등록하면, 학생이 참여코드로 수강 등록한 뒤 기간 내에 과제를 제출하고,
강사가 채점·피드백을 남기는 웹 서비스입니다. 영남이공대 자바스프링 중간고사 프로젝트로 제작했습니다.

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

## 설계 문서

- [요구사항 명세](docs/requirements.md)
- [ERD](docs/erd.md)
- [화면 설계](docs/screens.md)
- [주간 진행 보고](docs/progress_w1.md)
- [아이디어 · 추가 예정](docs/ideas.md)

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
├─ course/         domain, repository   (Course, Enrollment)
├─ assignment/     domain, repository   (Assignment)
├─ submission/     domain, repository   (Submission, SubmissionStatus)
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

### 강사
| ID | 기능 | 상태 |
|----|------|------|
| I1 | 강좌 개설 (참여코드 자동 발급) | 예정 |
| I2 | 수강생 목록 | 예정 |
| I3 | 과제 등록 · 수정 · 삭제 | 예정 |
| I4 | 제출 현황 (제출 / 미제출) | 예정 |
| I5 | 채점 · 피드백 | 예정 |
| I6 | 통계 대시보드 | 예정 |

### 학생
| ID | 기능 | 상태 |
|----|------|------|
| S1 | 수강 등록 (참여코드) | 예정 |
| S2 | 내 과제 목록 (예정 · 진행중 · 마감 배지) | 예정 |
| S3 | 과제 제출 · 재제출 | 예정 |
| S4 | 결과 확인 (점수 · 피드백) | 예정 |
| S5 | 내 통계 | 예정 |

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
현재 없음. 컬럼을 추가하면 여기에 이유와 함께 기록합니다.

## 비즈니스 규칙

모든 규칙은 화면이 아니라 서버(Service)에서 검사합니다.

| # | 규칙 | 위반 시 처리 | 상태 |
|---|------|--------------|------|
| B1 | 제출은 시작일시 ≤ 현재 ≤ 종료일시 일 때만 가능 | "제출 기간이 아닙니다" 알림 | 예정 |
| B2 | 수강 등록된 학생만 과제 열람 · 제출 | 403 페이지 | 예정 |
| B3 | 과제당 학생 1건, 기간 내 재제출은 덮어쓰기 | 기존 제출 수정 | 예정 |
| B4 | 채점 완료(GRADED)된 제출은 재제출 불가 | "채점 완료된 과제" 알림 | 예정 |
| B5 | 강사는 본인 강좌의 과제만 수정 · 삭제 · 채점 | 403 페이지 | 예정 |
| B6 | 점수는 0 이상 배점(max_score) 이하 | 폼 에러 메시지 | 예정 |
| B7 | 종료일시는 시작일시보다 뒤 | 폼 에러 메시지 | 예정 |
| B8 | 제출이 1건이라도 있는 과제는 삭제 불가 | "제출물이 있어 삭제 불가" 알림 | 예정 |

## 화면 · URL

| 역할 | URL | 화면 |
|------|-----|------|
| 공통 | `/login` · `/signup` | 로그인 · 학생 회원가입 |
| 공통 | `/` | 역할별 대시보드 |
| 강사 | `/instructor/courses` · `/{id}` | 강좌 목록 · 상세 (참여코드, 수강생) |
| 강사 | `/instructor/assignments/new` · `/{id}/edit` | 과제 등록 · 수정 |
| 강사 | `/instructor/assignments/{id}/submissions` | 제출 현황 + 미제출자 |
| 강사 | `/instructor/submissions/{id}/grade` | 채점 · 피드백 |
| 강사 | `/instructor/stats` | 통계 대시보드 |
| 학생 | `/student/courses/join` | 참여코드 입력 |
| 학생 | `/student/assignments` · `/{id}` | 내 과제 목록 · 상세 + 제출 |
| 학생 | `/student/submissions` | 내 제출 · 점수 · 피드백 |
| 공통 | `/error/403` · `/error/404` | 권한 없음 · 없는 페이지 |

## 초기 계정

강사 계정은 가입할 수 없고, 앱 시작 시 `.env`의 `INSTRUCTOR_EMAIL` · `INSTRUCTOR_NAME` · `INSTRUCTOR_PASSWORD`로 만들어집니다.
(비밀번호가 비어 있으면 만들지 않고, 이미 있으면 건너뜁니다.) 비밀번호는 저장소에 기록하지 않으니 시연 환경의 `.env`를 확인하세요.

학생 계정은 `/signup`에서 가입합니다.

## 진행 현황 (4주 로드맵)

- **W1 설계 + 기반**: 설계 문서, 엔티티 5개, SB Admin 2 레이아웃, U1 회원가입, U2 로그인, U3 역할별 메뉴·URL 접근 제한, U4 강사 초기 계정 완료 (U1~U4 완료)
- **W2 강좌 · 과제 등록**: 예정
- **W3 제출 · 채점**: 예정
- **W4 통계 + 마무리**: 예정