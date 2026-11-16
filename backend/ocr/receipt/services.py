import base64
import json
import logging
from datetime import date
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field

import anthropic
from django.conf import settings

logger = logging.getLogger(__name__)

# Spring WAS 시드 데이터와 일치하는 지출(EXPENSE) 카테고리 목록
DEFAULT_CATEGORIES = [
    {"id": 1, "name": "식비", "type": "EXPENSE"},
    {"id": 2, "name": "배달", "type": "EXPENSE"},
    {"id": 3, "name": "카페", "type": "EXPENSE"},
    {"id": 4, "name": "교통", "type": "EXPENSE"},
    {"id": 5, "name": "월세", "type": "EXPENSE"},
    {"id": 6, "name": "기타", "type": "EXPENSE"},
]


class Category(BaseModel):
    id: int
    name: str
    type: str


class ReceiptData(BaseModel):
    amount: Optional[int] = Field(None, description="할인 적용 후 최종 결제 금액(순수 정수). 식별 불가시 null")
    transactionDate: Optional[str] = Field(None, description="결제일 (YYYY-MM-DD 형식). 식별 불가시 null")
    category: Optional[Category] = Field(None, description="제공된 카테고리 목록 중 가장 알맞은 1개. 불가시 null")
    memo: Optional[str] = Field(None, description="상호명 또는 간단한 결제 요약 (20자 이내). 불가시 null")


_claude_client = None


def get_claude_client() -> anthropic.Anthropic:
    global _claude_client
    if _claude_client is None:
        # API 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다. 시간 상한은 WAS 쪽 ocr.timeout(20초)보다 짧게 둬서,
        # WAS 가 먼저 끊어 502 가 되는 대신 여기서 503 으로 끝나게 한다.
        _claude_client = anthropic.Anthropic(
            timeout=settings.CLAUDE_TIMEOUT_SECONDS,
            max_retries=settings.CLAUDE_MAX_RETRIES,
        )
    return _claude_client


def analyze_receipt(
    image_bytes: bytes,
    mime_type: str = "image/jpeg",
    categories: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """
    Claude 멀티모달 모델을 호출하여 영수증 이미지에서 결제 정보를 추출합니다.
    """
    if categories is None:
        categories = DEFAULT_CATEGORIES

    client = get_claude_client()

    today_str = date.today().isoformat()
    prompt = (
        f"영수증에서 결제 정보를 추출해줘. (오늘 날짜: {today_str})\n"
        f"식별할 수 없는 값은 억지로 추측하지 말고 null을 반환해.\n"
        f"합계 금액은 식별할 수 없지만 상품 별 결제 금액은 가능하다면 합계 금액을 상품 별 결제 금액을 전부 더해서 계산해.\n"
        f"category는 반드시 아래 목록 중에서만 선택해:\n"
        f"{json.dumps(categories, ensure_ascii=False)}"
    )

    # temperature 는 보내지 않는다 — 현재 Claude 모델은 sampling 파라미터를 400 으로 거절한다.
    # output_format 이 ReceiptData 의 JSON 스키마로 응답 모양을 강제한다(구조화 출력).
    response = client.messages.parse(
        model=settings.CLAUDE_MODEL,
        max_tokens=16000,
        output_config={"effort": settings.CLAUDE_EFFORT},
        output_format=ReceiptData,
        messages=[{
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": mime_type,
                        "data": base64.standard_b64encode(image_bytes).decode("utf-8"),
                    },
                },
                {"type": "text", "text": prompt},
            ],
        }],
    )

    if response.stop_reason in ("refusal", "max_tokens") or response.parsed_output is None:
        # 거절이면 스키마를 지키지 않을 수 있고, 토큰 상한이면 JSON 이 잘려 있다 → 뷰가 503 으로 바꾼다
        raise RuntimeError(f"영수증 분석 응답을 쓸 수 없습니다 (stop_reason={response.stop_reason})")

    parsed: ReceiptData = response.parsed_output
    data = parsed.model_dump()

    required_fields = ["amount", "transactionDate", "category", "memo"]
    data["missingFields"] = [field for field in required_fields if data.get(field) is None]

    return {
        "success": True,
        "data": data,
    }

