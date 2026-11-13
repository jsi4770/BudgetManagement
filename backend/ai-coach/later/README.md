# 그려톤 3조 · AI 파트 (Spring Boot)

계획표(`그려톤_계획`)와 서비스 명세서 기준으로 AI·분석 쪽을 Spring 코드로 옮긴 것입니다.
Spring Boot 3.2 이상, Java 17. 추가 라이브러리는 없습니다 (`spring-boot-starter-web`, JPA 등 트랜잭션 포함 스타터, 테스트용 `spring-boot-starter-test`만 있으면 됨).

## 무엇이 들어 있나

| 패키지 | 명세 | 내용 |
|---|---|---|
| `ai.gemini` | 계획표 "Gemini 연결" | 키는 환경변수, Client Bean, JSON 스키마로 응답 강제, 재시도, 503/422 매핑, 목(mock) 모드 |
| `transaction.parse` | TX-07, SFR-007·008, 7.1 | 한 줄 입력 파싱. 작성일 기준 날짜(`저번주 수요일`), 6개 카테고리, 오타 허용(`뱌민 만삼처넌`), 단위 추론(`배민 3.4`=34,000) |
| `receipt` | RCPT-01, SFR-009 | 영수증 OCR. Django(CLOVA) → Gemini 정리. Django 미준비 시 Gemini가 이미지 직접 읽기. 413/415/502 |
| `analysis` | ANL-01·02·03, SFR-013~017, 7.2 | 월별 요약, 주간 추이, 카테고리 비교(WEEK/MONTH), 이상치 판정 함수 |
| `nudge` | SFR-018~020, NDG-01·02, 7.3 | TX-01/05/06 후처리 넛지 판정, AI 문구 비동기 생성 + 대체 문구 |
| `coaching` | SFR-022~026, 035, 7.3 | 코칭 대화 프롬프트, 금액 목표 제안·검증, 성장 리포트 피드백, 실패 시 대체 메시지 |
| `dev` | DEV-01 | 시연 계정 시드 데이터 (4개월+, 매월 급여·월세, 이번 주 배달만 급증) |
| `common` | 공통 에러 코드 | 명세 응답 형식, 예외 → 실패 응답, KST·월요일 시작 주 |
| `src/test` | 계획표 테스트 항목 | 배달만 이상치, 가입 2주, 월세 제외, MONTH 항상 false, 넛지 생성·갱신·삭제 등 |

---

## 1. 프로젝트에 넣기

1. `src/main/java/com/example/budget/` 아래 폴더들과 `src/test/...`를 프로젝트에 복사합니다.
2. 패키지 `com.back.was`을 팀 패키지로 바꿉니다. (이클립스: 패키지 우클릭 → Refactor → Rename, "Rename subpackages" 체크) `@SpringBootApplication` 클래스가 있는 패키지 **아래**여야 스프링이 찾습니다.
3. `application-ai-example.yml` 내용을 기존 `application.yml`에 합치고 예시 파일은 지웁니다.
4. `.github/workflows/ci.yml`은 저장소 **최상위** `.github/workflows/`에 둡니다.

## 2. 팀 코드와 연결해야 하는 곳 (TODO 5개)

DB 엔티티·인증 방식은 팀마다 달라서 "창구(인터페이스)"로 분리해 두었습니다. 아래 클래스 안의 TODO만 채우면 나머지는 그대로 동작합니다. 각 파일 주석에 JPA 예시를 적어 뒀어요.

| 파일 | 채울 내용 |
|---|---|
| `common/CurrentUserProviderImpl` | 로그인 사용자 id 꺼내기 (JWT) |
| `category/CategoryProviderImpl` | 카테고리 DB 조회 (지금은 6개+기타 하드코딩, **id가 DB와 같아야 함**) |
| `analysis/AnalysisDataPortAdapter` | 기간별 거래 조회, 가입일, 월 예산 |
| `nudge/NudgeStorePortAdapter` | 넛지 조회·저장·삭제, AI 문구 저장 |
| `dev/SeedDataPortAdapter` | 시연 계정 판별, 거래 삭제·저장, 가입일 변경 |

채우기 전에 해당 API를 부르면 `UnsupportedOperationException("TODO: ...")`이 나니 어디를 안 채웠는지 바로 보입니다. 서버 실행 자체는 막지 않습니다.

## 3. 거래 서비스에 한 줄씩 추가 (넛지 판정)

```java
// TX-01 등록 저장 후
nudgeJudgeService.onCreated(userId, categoryId);
// TX-05 수정 저장 후 (카테고리 변경 시 이전·새 카테고리 모두 재판정)
nudgeJudgeService.onUpdated(userId, oldCategoryId, newCategoryId);
// TX-06 삭제 후
nudgeJudgeService.onDeleted(userId, categoryId);
```

AI 문구는 트랜잭션 커밋 후 별도 스레드에서 만들어져 `saveAiCopy`로 저장됩니다. 그 사이 NDG-02의 `aiAnalysis`가 null이면 프론트가 "AI가 분석 중이에요"를 띄우면 됩니다 (명세). 넛지 `title`은 저장하지 않고 NDG-01 응답 만들 때 `NudgeTitle.of(카테고리명)`을 쓰세요.

## 4. API 키 넣고 실행 (이클립스)

**키 발급:** Google AI Studio (aistudio.google.com) → Get API key → Create API key

**실행 설정:** 메인 클래스 우클릭 → Run As → Run Configurations… → Spring Boot App(또는 Java Application) → **Environment** 탭 → Add

```
GEMINI_API_KEY = 발급받은 키
SPRING_PROFILES_ACTIVE = local
```

Common 탭의 "Shared file"은 꺼둔 채로 두세요 (켜면 `.launch` 파일로 키가 커밋될 수 있음).

- 키 없는 팀원: `GEMINI_MOCK = true`로 실행하면 가짜 응답으로 나머지 작업 가능
- 배포 서버: 서버 환경변수로 `GEMINI_API_KEY` (Docker면 `-e GEMINI_API_KEY=...`). `SPRING_PROFILES_ACTIVE=local`은 넣지 않기
- 커밋 전: `git diff`에 키(보통 `AIza`로 시작)가 없는지 확인. 실수로 올렸으면 AI Studio에서 그 키 삭제 후 재발급

## 5. 동작 확인

콘솔에 `[Gemini] 연결 준비 완료 - model=...`이 보이면 키를 읽은 것입니다.

```
GET  /dev/ai/ping?text=배민 3.4                          → 연결 확인 (local 프로필만)
POST /transactions/parse   {"text": "뱌민 만삼처넌"}      → 오늘, 배달, 13000
POST /transactions/parse   {"text": "저번주 수요일 옷 만삼천원"} → 저번 주 수요일, 기타, 13000
POST /receipts/ocr         (form-data: image=영수증.jpg)
POST /dev/seed             → 시연 계정 시드 생성
GET  /analysis/categories?period=WEEK                    → 배달만 isAnomaly: true
```

401/403이 나오면 Spring Security가 막는 것이니 토큰을 붙여 호출하거나, `/dev/**`를 local에서만 열어 주세요. 컨트롤러 경로는 `/transactions`처럼 앞에 `/api/v1` 없이 썼습니다. 팀이 context-path로 `/api/v1`을 붙이면 그대로 두고, 컨트롤러마다 직접 붙이는 방식이면 맞춰 바꾸세요.

### 안 될 때 (콘솔의 `[Gemini] HTTP ...` 줄)

| 로그 | 원인 | 해결 |
|---|---|---|
| `GEMINI_API_KEY 환경변수가 없습니다` | 실행 설정에 키 없음 | 지금 Run 하는 설정의 Environment 탭 확인 후 재시작 |
| `HTTP 400 ... API key not valid` | 키 오타·일부만 복사 | 키 다시 복사 |
| `HTTP 403` | 키 차단 (유출 등) | 새 키 발급 |
| `HTTP 404` | 모델명 오류 | `GEMINI_MODEL` 확인 |
| `HTTP 429` | 무료 한도 초과 | 잠시 후 재시도, 시연 전 테스트 호출 줄이기 |

Gemini가 실패해도 서비스는 멈추지 않습니다: 파싱·영수증은 503/422 → 프론트가 직접 입력 화면으로, 넛지는 템플릿 문구, 코칭은 200 + 대체 메시지 (명세 7.3-4, SNFR-010).

## 6. 코칭 붙이는 법

명세서에 COACH API의 요청·응답 상세가 없어서 **AI 부분(`CoachingAiService`)만** 만들었습니다. 코칭 컨트롤러를 만드는 쪽에서 이렇게 호출하면 됩니다.

```java
// COACH-01 코칭 시작: 컨텍스트 1번 만들어 코칭 세션에 저장
String ctx = coachingAi.buildContext(
        comparisonService.compareWeek(userId, TimeUtil.today()),
        nudge.getAiAnalysis(),                                   // 넛지에서 시작했으면
        monthlySummaryService.summary(userId, YearMonth.now(TimeUtil.KST)));

// COACH-03 메시지: 이전 대화는 user/model 번갈아, 오래된 순
CoachReply r = coachingAi.reply(ctx, history, userMessage);
// r.reply() → 화면에 표시, r.proposedGoal() → 목표 카드 (COACH-04 확정 전엔 저장 X)

// CHL-03 성장 리포트
String feedback = coachingAi.growthFeedback(new ChallengeResultFacts(...));
```

목표는 금액 목표만(팀 결정), 월세 제외, 1,000원 단위, 기간 1~31일로 서버가 다시 검증합니다.

## 7. 테스트와 GitHub Actions

`src/test`에 계획표 테스트 항목을 JUnit으로 옮겼습니다. Gemini 없이 돌아갑니다.

- 시드 데이터에서 배달만 이상치 / 가입 2주 계정은 `dataSufficient: false` / 월세는 주간 집계에서 제외 / MONTH는 항상 `isAnomaly: false`
- 넛지: 생성 → 같은 주 재등록 시 갱신·상태 유지 → 조건 미달 시 UNREAD 삭제 → 가입 2주면 생성 안 됨
- 파싱 후처리: 금액 없으면 `missingFields: ["amount"]`, 목록 밖 카테고리 → 기타, 거래 정보 없으면 422, 날짜표의 "저번 주 수요일"

**GitHub Actions**는 push나 PR을 올리면 깃허브 서버가 위 테스트를 자동으로 돌려주는 기능입니다. `ci.yml`이 `GEMINI_MOCK=true`로 돌기 때문에 깃허브에 키를 등록할 필요가 없습니다. 결과는 저장소 상단 **Actions** 탭에서 초록 체크 / 빨간 X로 보입니다. 처음 실패는 보통 `gradlew` 권한, Java 버전, 백엔드 폴더 위치(파일 안 주석 참고) 문제입니다. Maven 프로젝트면 파일 안 주석대로 명령만 바꾸세요.

## 8. 팀과 맞춰야 할 것

- **Django OCR 내부 API 모양** (준식님): 지금은 `POST multipart image` → `{"text": "..."}`로 가정 (`DjangoOcrClient` 주석)
- **COACH API 필드**: 위 6번
- **카테고리 id**: `CategoryProviderImpl`을 DB 조회로 바꾸면 해결
- **Z-score 표준편차**: 모표준편차(÷4)로 구현. 표본(÷3)으로 하기로 했으면 `AnomalyJudge` 한 줄 수정
- **기존 코드와 겹치는 것**: 이미 만든 분석 API나 `@RestControllerAdvice`, `@EnableAsync`가 있으면 이쪽 컨트롤러/파일은 빼고 Service만 쓰세요 (같은 경로·같은 예외를 두 번 정의하면 서버가 안 뜸)
