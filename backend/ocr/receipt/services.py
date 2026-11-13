import json
import logging
from datetime import date
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field

from django.conf import settings
from google import genai
from google.genai import types

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


_genai_client = None


def get_genai_client() -> genai.Client:
    global _genai_client
    if _genai_client is None:
        _genai_client = genai.Client(
            vertexai=True,
            project=settings.GCP_PROJECT_ID,
            location=settings.GCP_LOCATION,
        )
    return _genai_client


def analyze_receipt(
    image_bytes: bytes,
    mime_type: str = "image/jpeg",
    categories: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """
    Gemini 멀티모달 모델을 호출하여 영수증 이미지에서 결제 정보를 추출합니다.
    """
    if categories is None:
        categories = DEFAULT_CATEGORIES

    client = get_genai_client()

    image_part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)

    today_str = date.today().isoformat()
    prompt = (
        f"영수증에서 결제 정보를 추출해줘. (오늘 날짜: {today_str})\n"
        f"식별할 수 없는 값은 억지로 추측하지 말고 null을 반환해.\n"
        f"합계 금액은 식별할 수 없지만 상품 별 결제 금액은 가능하다면 합계 금액을 상품 별 결제 금액을 전부 더해서 계산해.\n"
        f"category는 반드시 아래 목록 중에서만 선택해:\n"
        f"{json.dumps(categories, ensure_ascii=False)}"
    )

    response = client.models.generate_content(
        model=settings.GEMINI_MODEL,
        contents=[image_part, prompt],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=ReceiptData,
        ),
    )

    parsed: ReceiptData = response.parsed
    data = parsed.model_dump()

    required_fields = ["amount", "transactionDate", "category", "memo"]
    data["missingFields"] = [field for field in required_fields if data.get(field) is None]

    return {
        "success": True,
        "data": data,
    }

