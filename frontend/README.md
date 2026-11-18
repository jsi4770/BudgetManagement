# 넛지 가계부 프론트엔드

React 18 + Vite + TypeScript. 모바일 우선(최대 폭 480px), 하단 탭 5개.

| 화면 | 경로 | 쓰는 API |
|---|---|---|
| 로그인·가입 | `/login` `/signup` | AUTH-01~04 |
| 온보딩(지역·나이·성별) | `/onboarding` | `GET /regions`, `PUT /users/me/onboarding` |
| 홈 (상단 청년정책 롤링·요약·넛지·챌린지) | `/` | `/youth-policies/home`, `/analysis/monthly`, `/nudges/current`, `/challenges` |
| 내역 (자연어 입력) | `/transactions` | `/transactions`, `/transactions/parse` |
| 코칭 (가맹점 추천·목표 확정) | `/coaching`, `/coaching/:id` | `/coachings/*` |
| 우리 동네 가맹점 | `/merchants` | `/local-merchants` |
| 마이 | `/me` | `/users/me`, `/challenges` |

## 실행

```bash
cd frontend
cp .env.example .env     # 필요하면 VITE_API_URL 수정
npm install
npm run dev              # http://localhost:5173  (백엔드 CORS 기본 허용 origin 과 같다. 127.0.0.1 로 열면 막힌다)
npm run build
```

- 백엔드는 `backend/was` 를 `SPRING_PROFILES_ACTIVE=dev` 로 띄운다. 첫 가맹점·정책 적재는 `MERCHANT_SYNC_ON_START=true`, `YOUTH_POLICY_SYNC_ON_START=true`.
- `VITE_DEMO_TOOLS=true` 면 마이 탭에 시연 데이터 버튼이 나온다(백엔드 dev 프로필 전용).
- 배포 시 백엔드의 `FRONT_URL` 에 프론트 주소를 넣고, 서로 다른 사이트면 `COOKIE_SAME_SITE=None` 이 필요하다.

## 알려 둔 한계
- 코칭에서 AI 가 추천한 가맹점은 그 턴의 응답으로만 오기 때문에, 페이지를 새로고침하면 가맹점 카드는 사라지고 대화 문장만 남는다.
- 코칭 목록 API 가 없어서 마지막 대화 ID 를 브라우저(localStorage)에 기억한다.
