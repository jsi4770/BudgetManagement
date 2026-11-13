# 영수증 OCR 서버 (Django)

Google Cloud Vertex AI의 Gemini 멀티모달 모델(`gemini-3.5-flash-lite`)을 활용하여 영수증 이미지에서 결제 정보(금액, 일자, 카테고리, 상호명)를 추출하는 마이크로서비스입니다.

---

## 1. 사전 준비

### (1) Google Cloud Service Account 키 준비
* 팀 공유 채널에서 전달받은 GCP Service Account JSON 키 파일을 `backend/ocr/` 디렉터리에 복사합니다.
* 파일명을 **`service-account.json`**으로 설정합니다.
> **보안 주의:** 키 파일은 `.gitignore`에 등록되어 있어 Git에 커밋되지 않습니다. 절대 Git 레포지토리에 푸시하지 마세요.

---

## 2. 환경 설정 및 실행

### (1) 가상환경 생성 및 활성화

```bash
cd backend/ocr

# 가상환경 생성
python -m venv venv

# 가상환경 활성화 (Windows PowerShell)
.\venv\Scripts\Activate.ps1

# 가상환경 활성화 (macOS / Linux)
source venv/bin/activate
```

### (2) 의존성 패키지 설치

```bash
pip install -r requirements.txt
```

### (3) 환경변수 설정 (`.env`)

`.env.example` 파일을 복사하여 `.env` 파일을 생성합니다

### (4) 서버 실행

```bash
python manage.py runserver 8000
```

서버가 실행되면 `http://127.0.0.1:8000/receipts/ocr` 엔드포인트로 요청을 보낼 수 있습니다.

---

## 3. 테스트 방법

### 단위 테스트 실행
Mock 기반의 단위 테스트(6개 케이스)를 실행하여 유효성 검사 및 에러 응답 처리를 검증합니다:

```bash
python manage.py test receipt
```

---

## 4. API 규격 요약

* **Method**: `POST`
* **Path**: `/receipts/ocr` (또는 `/api/v1/receipts/ocr`)
* **Content-Type**: `multipart/form-data`
* **Request Field**:
  * `image` (File, 필수): 영수증 이미지 파일 (jpg / png / webp, 최대 10MB)
* **Response (200 OK)**:
  ```json
  {
    "success": true,
    "data": {
      "amount": 23500,
      "transactionDate": "2026-11-04",
      "category": {
        "id": 2,
        "name": "배달",
        "type": "EXPENSE"
      },
      "memo": "배달의민족 주문",
      "missingFields": []
    }
  }
  ```

