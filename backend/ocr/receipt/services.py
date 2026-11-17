import json
import logging
from datetime import date
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field

from django.conf import settings
from google import genai
from google.genai import types

logger = logging.getLogger(__name__)

# Spring WAS 시드 데이터와 일치하는 지출(EXPENSE) 및 수입(INCOME) 카테고리 목록
DEFAULT_CATEGORIES = [
    {"id": 1, "name": "식비", "type": "EXPENSE"},
    {"id": 2, "name": "배달", "type": "EXPENSE"},
    {"id": 3, "name": "카페", "type": "EXPENSE"},
    {"id": 4, "name": "교통", "type": "EXPENSE"},
    {"id": 5, "name": "월세", "type": "EXPENSE"},
    {"id": 6, "name": "기타", "type": "EXPENSE"},
    {"id": 7, "name": "급여", "type": "INCOME"},
    {"id": 8, "name": "용돈", "type": "INCOME"},
    {"id": 9, "name": "기타", "type": "INCOME"},
]


class Category(BaseModel):
    id: int
    name: str
    type: str


class TransactionItem(BaseModel):
    amount: Optional[int] = Field(None, description="결제 또는 입출금 금액(순수 양수 정수). 식별 불가시 null")
    transactionDate: Optional[str] = Field(None, description="거래일자 (YYYY-MM-DD 형식). 식별 불가시 null")
    category: Optional[Category] = Field(None, description="제공된 카테고리 목록 중 가장 알맞은 1개. 입금은 INCOME, 출금은 EXPENSE 타입에서 선택. 불가시 null")
    memo: Optional[str] = Field(None, description="상호명, 거래처, 적요 또는 송금인/수취인 요약 (20자 이내). 불가시 null")


# 하위 호환성을 위한 alias
ReceiptData = TransactionItem


class BankStatementData(BaseModel):
    items: List[TransactionItem] = Field(default_factory=list, description="식별된 거래 내역 목록. 여러 건일 경우 시간순 또는 위에서 아래 순서로 나열")


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
    Gemini 멀티모달 모델을 호출하여 금융 앱 거래내역 캡처 또는 영수증 이미지에서 입출금 거래 목록을 추출합니다.
    """
    if categories is None:
        categories = DEFAULT_CATEGORIES

    client = get_genai_client()

    image_part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)

    today_str = date.today().isoformat()
    prompt = (
        f"금융 앱(은행 앱, 토스 등)의 거래내역 캡처 이미지 또는 영수증 이미지에서 입출금 거래 내역을 추출해줘. (기준 오늘 날짜: {today_str})\n"
        f"이미지 속의 모든 개별 입금/출금 거래 내역들을 찾아서 items 목록으로 정리해줘.\n"
        f"- 거래내역 목록 캡처인 경우, 화면에 보이는 각 거래 행을 순서대로 빠짐없이 모두 추출해.\n"
        f"- 단일 영수증인 경우 1건의 거래로 추출해.\n"
        f"- amount: 부호(+, -)나 '원' 단위를 제외한 순수 양수 정수 금액.\n"
        f"- transactionDate: YYYY-MM-DD 형식. 연도가 표시되지 않고 월/일만 표기된 경우 기준 오늘 날짜의 연도를 조합하여 작성.\n"
        f"- memo: 거래를 식별할 수 있는 짧은 요약 (20자 이내).\n"
        f"- 식별할 수 없는 필드는 억지로 지어내지 말고 null을 반환해.\n"
        f"입금(수입, +)인 경우 type이 INCOME인 카테고리 중에서 선택하고, "
        f"출금(지출, -) 또는 일반 결제인 경우 type이 EXPENSE인 카테고리 중에서 선택해. "
        f"어느 쪽에도 뚜렷하게 맞지 않으면 해당 type의 '기타'를 선택해.\n"
        f"- category: 반드시 아래 카테고리 목록 중에서만 선택해. "
        f"category 목록:\n"
        f"{json.dumps(categories, ensure_ascii=False)}"
    )

    response = client.models.generate_content(
        model=settings.GEMINI_MODEL,
        contents=[image_part, prompt],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=BankStatementData,
        ),
    )

    parsed: BankStatementData = response.parsed
    data = parsed.model_dump()

    required_fields = ["amount", "transactionDate", "category", "memo"]
    for item in data.get("items", []):
        item["missingFields"] = [field for field in required_fields if item.get(field) is None]

    return {
        "success": True,
        "data": data,
    }

