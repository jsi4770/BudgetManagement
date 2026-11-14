# AI 기능 연동 가이드

`backend/was` 에 붙은 AI 기능(TX-07 한 줄 입력, RCPT-01 영수증, 넛지 문구·코칭·성장 리포트 피드백)의 구성·설정·남은 일을 정리한다.

## 1. 붙은 것

| 명세 | 엔드포인트 | 구현 | 비고 |
|---|---|---|---|
| TX-07 | `POST /transactions/parse` | `domain/ai/GeminiTransactionTextParser` (포트 `TransactionTextParser`) | 저장하지 않음. 결과는 `TransactionService#assemble` 이 조립 |
| RCPT-01 | `POST /receipts/ocr` | `domain/ocr/ReceiptOcrService` → 포트 `ReceiptOcrClient` | Django 또는 Gemini 직접 읽기 (아래) |
| ANL-03 | `GET /analysis/categories` | `domain/analysis/AnalysisService#getCategoryComparison` | 제거 전 팀 구현을 그대로 복원 |
| NDG-01~03 | `/nudges/**` | `nudge/NudgeService` + 거래 후처리 `NudgeJudgement` | 판정은 `SpendingComparison#isAnomaly` (ANL-03 과 같은 계산 함수). 문구는 `GeminiNudgeTextGenerator` |
| COACH-01~04 | `/coachings/**` | `coaching/CoachingService` | 첫 메시지 `GeminiCoachingOpeningGenerator`, 대화 `GeminiCoachingReplyGenerator`, 실패 시 `RuleBasedGoalProposer` |
| CHL-01~03 | `/challenges/**` | `challenge/ChallengeService`, `ChallengePerformance`, 00:05 `ChallengeTerminationJob` | 피드백 `GeminiGrowthFeedbackGenerator` |
| ADMIN-01 | `/admin/kpi` | `admin/AdminKpiService` | 챌린지 달성률 집계 |
| (공통) | — | `domain/ai/gemini/*` | Gemini REST 클라이언트(재시도·예비 모델·mock) |

거래 등록·수정·삭제(TX-01/05/06)는 같은 트랜잭션에서 **챌린지 실적 재계산 → 넛지 판정**을 돌리고, 넛지 AI 문구는 커밋 후
비동기로 채운다(`NudgeTextFiller`).

- **포트 계약은 제거 전과 같다.** 백엔드가 지키는 건 명세의 응답 필드와 실패 코드, AI 담당이 정하는 건 프롬프트·스키마다
  (서버_내부_처리 8장). 프롬프트는 `ParsePromptBuilder`, 구조화 출력 스키마는 `ParsedTxSchema` 한 곳에 있다.
- `missingFields` 는 모델·OCR 서버가 말한 목록이 아니라 **`null` 로부터 서비스가 만든다**
  (`TransactionService#assemble`). TX-07 과 RCPT-01 이 같은 메서드를 쓰므로 두 화면의 계약이 어긋나지 않는다.

## 2. 설정 (환경변수)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `GEMINI_API_KEY` | (없음) | 없어도 서버는 뜬다. AI 기능만 `503 AI_UNAVAILABLE` |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | 기본 모델 |
| `GEMINI_FALLBACK_MODEL` | `gemini-2.5-flash` | 기본 모델이 429·장애일 때 전환 |
| `GEMINI_MOCK` | `false` | `true` 면 실제 호출 없이 가짜 응답 (CI·키 없는 팀원) |
| `OCR_BASE_URL` | (없음) | Django OCR 서버 주소. **없으면 Gemini 가 이미지를 직접 읽는다** |
| `OCR_TIMEOUT` | `20s` | OCR 서버 응답 대기 |

`.env.example` 참고. 키는 코드·저장소에 두지 않는다.

## 3. 실패 코드 매핑

| 상황 | 코드 |
|---|---|
| 키 없음 · 한도 초과 · Gemini 장애/타임아웃 | `503 AI_UNAVAILABLE` |
| 거래 정보가 아니라는 판단, 필수 3필드(금액·날짜·카테고리) 모두 못 뽑음 | `422 AI_PARSE_FAILED` |
| OCR 서버 연결 실패·타임아웃·계약과 다른 응답 | `502 OCR_FAILED` |
| OCR 서버(Django)가 503 | `503 AI_UNAVAILABLE` (502 와 구분) |
| jpg·png 아님, 빈 파일 | `415 UNSUPPORTED_FILE_TYPE` |
| 10MB 초과 | `413 FILE_TOO_LARGE` |

사용자 입력·영수증 이미지·인식 원문은 **로그에 남기지 않는다** (SNFR-008).

### AI 포트별 실패 계약

| 포트 | Gemini 실패 시 | 근거 |
|---|---|---|
| `TransactionTextParser` (TX-07) | 503 / 422 로 **드러낸다** | 해석 못 한 문장은 저장할 값이 없다 |
| `ReceiptOcrClient` (RCPT-01) | 502 / 503 / 422 로 **드러낸다** | 사용자가 사진을 다시 찍거나 직접 입력해야 한다 |
| `NudgeTextGenerator` | 템플릿 문구로 **대체** (던지지 않음) | 문구가 없어도 카드는 성립한다 |
| `CoachingOpeningGenerator` | 템플릿 문구로 **대체** | COACH-01 은 에러를 정의하지 않는다 |
| `GrowthFeedbackGenerator` | 템플릿 문구로 **대체** | ERD 10장 "실패 시 결과별 템플릿" |
| `CoachingReplyGenerator` (COACH-03) | **던진다** → 서비스가 200 + `isFallback: true` + 규칙 기반 제안 | 프론트가 "AI 연결이 원활하지 않아요"를 띄워야 한다 |

넛지 문구·첫 메시지·피드백·코칭 답변은 **서버가 준 적 없는 숫자(환각)가 끼면 쓰지 않는다**(`NumberGuard`) — 앞의 셋은 템플릿으로
대체하고, 코칭 답변은 한 번 다시 시킨 뒤에도 남으면 던져서 대체 응답으로 간다.

## 4. 영수증 경로

```
OCR_BASE_URL 있음  →  DjangoReceiptOcrClient  →  backend/ocr (Vertex AI 멀티모달)
OCR_BASE_URL 없음  →  GeminiReceiptOcrClient  →  Gemini 가 이미지를 직접 읽음 (개발·시연용)
```

- Django 에는 이미지와 함께 **DB 카테고리 목록(`categories`)을 보낸다.** Django 의 기본 목록은 id 가 하드코딩돼 있어서,
  시드 순서가 바뀌면 틀린 카테고리가 나온다.
- Django 응답의 `missingFields` 는 쓰지 않는다 (Django 는 `memo` 까지 필수로 세지만 명세의 필수는 3개).
- Django 가 안 떠 있을 때 Gemini 로 **조용히 우회하지 않는다** — 502 `OCR_FAILED` 가 나가야 장애를 알아챈다.

## 5. 이번에 확인된 불일치 (AI 프로토타입 ↔ 팀 명세)

`backend/ai-coach` 프로토타입의 `AnomalyJudge`·`CategoryComparisonService` 는 팀 명세(ANL-03·서버_내부_처리 3장)와 달랐다.
그래서 ANL-03 은 프로토타입을 옮기지 않고 제거 전 팀 구현(`SpendingComparison` 등)을 복원했다.

| | 프로토타입 | 팀 명세 |
|---|---|---|
| 표준편차 | 모표준편차 (n) | 표본표준편차 (n−1) |
| 표준편차 0 | Z=∞ 로 보고 이상치 가능 | Z=null, 판정 생략 |
| 데이터 충분성 | 가입 28일 이상 | 가입일 ≤ 가장 오래된 표본 구간 시작일 |
| 응답 항목 | 거래가 있는 카테고리만 | 지출 카테고리 전부(0원 포함), ID 순 |

## 6. 남은 일

- `backend/ai-coach` 의 `coach` 패키지(월간 집계·인사이트 카드·도구 호출 코치)는 아직 거래 DB 가 아니라 데모 JSON 을 읽는다.
  쓰려면 거래 리포지토리로 바꿔야 한다.
- 코칭 대화(COACH-03)의 Gemini 프롬프트는 대화에 나온 숫자만 근거로 삼는다(포트가 DB 를 모르는 구조). 첫 메시지에 넛지의 금액이
  들어가므로 대화에서는 충분하지만, 더 풍부한 소비 근거(직전 4주 카테고리별 추이 등)를 주려면 `CoachingReplyCommand` 에
  맥락 필드를 추가해야 한다.
- 실제 Gemini 호출 검증: 키 없이는 mock·stub 으로만 확인했다. 키를 넣고 아래를 한 번씩 눌러 볼 것 —
  TX-07, RCPT-01, 넛지 생성 후 NDG-02 의 `aiAnalysis`, COACH-01·03.
- 넛지 문구 비동기 생성은 `AsyncConfig` 의 스레드풀을 쓴다. Gemini 지연이 길어지면(호출당 10초 + 재시도) 풀이 막힐 수 있으니
  시연 전에 부하를 한 번 확인할 것.
- `gemini.timeout` 은 호출당 10초(서버_내부_처리 8장)지만 5xx 재시도·예비 모델이 직렬이라 최악의 경우 합계는 더 길다.
  사용자 대기 한도가 문제되면 `GEMINI_MAX_RETRIES` 를 0 으로 낮출 것.
