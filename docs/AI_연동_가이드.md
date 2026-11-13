# AI 기능 연동 가이드

`backend/was` 에 붙은 AI 기능(TX-07 한 줄 입력, RCPT-01 영수증)과 ANL-03 의 구성·설정·남은 일을 정리한다.

## 1. 붙은 것

| 명세 | 엔드포인트 | 구현 | 비고 |
|---|---|---|---|
| TX-07 | `POST /transactions/parse` | `domain/ai/GeminiTransactionTextParser` (포트 `TransactionTextParser`) | 저장하지 않음. 결과는 `TransactionService#assemble` 이 조립 |
| RCPT-01 | `POST /receipts/ocr` | `domain/ocr/ReceiptOcrService` → 포트 `ReceiptOcrClient` | Django 또는 Gemini 직접 읽기 (아래) |
| ANL-03 | `GET /analysis/categories` | `domain/analysis/AnalysisService#getCategoryComparison` | 제거 전 팀 구현을 그대로 복원 |
| (공통) | — | `domain/ai/gemini/*` | Gemini REST 클라이언트(재시도·예비 모델·mock) |

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

## 6. 아직 안 붙은 것 (다음 작업)

제거된 모듈 중 **엔티티(테이블)가 필요한 것**은 이번 범위에서 제외했다. 백엔드(박현호)가 의도적으로 뺀 코드라서,
되살릴지·새로 만들지 합의가 먼저 필요하다.

- 넛지 (NDG-01~03): `Nudge` 엔티티, 거래 등록·수정·삭제 후처리(`TransactionService` 의 TODO), AI 문구 비동기 생성.
  판정은 `SpendingComparison#isAnomaly` 를 그대로 써야 한다 (ANL-03 과 같은 계산 함수 — 서버_내부_처리 3장).
- AI 코칭 (COACH-xx): `Coaching`·`CoachingMessage` 엔티티, 대화 생성(`CoachingReplyGenerator` 포트), 목표 제안.
- 챌린지·성장 리포트·KPI: 코칭 확정이 챌린지를 만들기 때문에 같이 정해야 한다.
- `backend/ai-coach` 의 `coach` 패키지(월간 집계·인사이트·도구 호출 코치)를 DB 거래로 읽게 바꾸기.
- `gemini.timeout` 은 호출당 10초(서버_내부_처리 8장)지만 5xx 재시도·예비 모델이 직렬이라 최악의 경우 합계는 더 길다.
  사용자 대기 한도가 문제되면 `GEMINI_MAX_RETRIES` 를 0 으로 낮출 것.
