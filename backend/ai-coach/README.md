> **⚠️ 이 폴더는 독립 실행되는 프로토타입입니다. 팀 백엔드(`backend/was`)에는 아직 합쳐지지 않았습니다.**
>
> - 한 줄 파싱(TX-07)·영수증(RCPT-01)·Gemini 클라이언트는 **`backend/was` 에 팀 구조(JPA·JWT·`ApiResponse`·`ErrorCode`)에 맞춰 이미 옮겨졌습니다.**
>   → `docs/AI_연동_가이드.md` 참고. 이 폴더의 같은 이름 패키지는 옛 버전이니 `backend/was` 쪽을 쓰세요.
> - **`analysis`(`AnomalyJudge` 등)는 쓰지 마세요.** 팀 명세(ANL-03)와 계산이 다릅니다 — 표본표준편차(n−1)가 아니라 모표준편차,
>   표준편차 0 이면 판정을 생략하지 않고 무한대로 처리, 데이터 충분성을 "가입 28일"로 판정. ANL-03 은 `backend/was` 의
>   `SpendingComparison`·`AnalysisPeriod` 가 명세대로 구현합니다.
> - 아직 합쳐지지 않은 것: **`coach`**(월간 집계·인사이트 카드·목표 현실성 판정·도구 호출 코치 대화), `nudge`, `coaching`. 거래 데이터를
>   `DemoData`(페르소나 JSON)가 아니라 DB 로 읽도록 바꿔야 합니다.

# ai-ledger-coach-java

`ai-ledger-coach`(Python 코치)를 Java로 이식해, 팀원이 넘겨준 `budget-ai-was`(한 줄 입력 파싱·영수증 OCR·주간 이상치·넛지)와 한 Spring Boot 앱으로 합친 프로젝트입니다.

## 구성
| 출처 | 패키지 | 내용 |
|---|---|---|
| budget-ai-was | `ai.gemini` `transaction.parse` `receipt` `analysis` `nudge` `coaching` `dev` `common` | 팀원 코드. 어댑터 5개를 메모리 구현으로 채워 단독 실행 가능 |
| ai-ledger-coach (Python→Java) | `coach` | 월간 집계·인사이트 카드·목표 현실성 판정·도구 호출 코치 대화 + 숫자 검증 |
| 공통 | `ai.gemini` | 두 쪽이 하나의 `GeminiClient`를 공유. 예비 모델 전환(429 즉시)·도구 호출(function calling)·mock 지연 추가 |

핵심 원칙은 동일합니다: **숫자는 코드가 계산하고, LLM은 해석만 한다 / LLM이 쓴 숫자는 코드가 다시 검증한다.**

## 실행
```
export GEMINI_API_KEY=...        # 없으면 GEMINI_MOCK=true
gradle bootRun                   # 처음 한 번 `gradle wrapper` 로 래퍼 생성 가능
# http://localhost:8080  (기존 Python UI 그대로)
```
- 코치 API: `/api/personas` `/api/summary/{p}` `/api/insights/{p}` `/api/goal/{p}` `/api/chat/{p}` (원본 FastAPI와 같은 경로·JSON)
- 팀원 API: `/transactions/parse` `/receipts/ocr` `/analysis/*` `/dev/seed`

## 테스트
```
gradle test    # 이식 정합성(Python 결과와 필드 단위 비교), 검증기, API 스모크 — 키 불필요
gradle perf    # 지연·처리량 (Gemini mock) → build/perf-report/*.md
gradle eval    # AI 품질: 검증기 탐지율(오프라인) + GEMINI_API_KEY 가 있으면 실제 호출 평가
EVAL_N=3 gradle eval   # 실제 호출 평가를 3회 반복 (무료 티어 한도 주의)
```

## 알려둘 점
- 거래 데이터 생성기(`mockgen.py`)는 이식하지 않고 생성된 JSON(`src/main/resources/demo`)을 그대로 씁니다 (Python `random` 시드는 Java에서 재현되지 않음).
- 두 쪽의 데이터는 아직 분리돼 있습니다: 코치는 페르소나 JSON, 팀원 쪽은 `InMemoryLedger`. 팀 DB에 붙일 때 `Aggregator`/`GoalService`가 읽는 `DemoData`를 거래 리포지토리로 바꾸면 통합됩니다.
- Spring Boot 4.0 (Jackson 3, `tools.jackson`) 기준입니다. 팀 프로젝트가 3.x라면 팀원 코드의 import 부터 맞춰야 합니다.
