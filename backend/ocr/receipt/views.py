import json
import logging
from rest_framework import status
from rest_framework.parsers import MultiPartParser, FormParser
from rest_framework.response import Response
from rest_framework.views import APIView

from .services import analyze_receipt

logger = logging.getLogger(__name__)

MAX_FILE_SIZE = 10 * 1024 * 1024  # 10MB
ALLOWED_MIME_TYPES = {
    "image/jpeg": "image/jpeg",
    "image/jpg": "image/jpeg",
    "image/png": "image/png",
    "image/webp": "image/webp",
}
ALLOWED_EXTENSIONS = (".jpg", ".jpeg", ".png", ".webp")


class ReceiptOcrView(APIView):
    """
    RCPT-01 영수증 OCR 분석 API
    영수증 이미지에서 결제 정보를 추출하여 반환하며, 이미지나 결과는 서버에 저장하지 않습니다.
    """
    parser_classes = [MultiPartParser, FormParser]

    def post(self, request, *args, **kwargs):
        # 1. 이미지 필드 존재 여부 확인
        image_file = request.FILES.get("image")
        if not image_file:
            return Response(
                {
                    "success": False,
                    "error": {
                        "code": "VALIDATION_ERROR",
                        "message": "이미지 파일이 필요합니다.",
                        "details": [{"field": "image", "reason": "REQUIRED"}],
                    },
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        # 2. 파일 크기 검증 (최대 10MB)
        if image_file.size > MAX_FILE_SIZE:
            return Response(
                {
                    "success": False,
                    "error": {
                        "code": "FILE_TOO_LARGE",
                        "message": "파일 크기는 최대 10MB까지 허용됩니다.",
                    },
                },
                status=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            )

        # 3. 파일 형식(MIME 타입 및 확장자) 검증
        content_type = getattr(image_file, "content_type", "").lower()
        file_name = getattr(image_file, "name", "").lower()

        mime_type = ALLOWED_MIME_TYPES.get(content_type)
        if not mime_type:
            # 확장자로 2차 판정
            if file_name.endswith((".jpg", ".jpeg")):
                mime_type = "image/jpeg"
            elif file_name.endswith(".png"):
                mime_type = "image/png"
            elif file_name.endswith(".webp"):
                mime_type = "image/webp"

        if not mime_type:
            return Response(
                {
                    "success": False,
                    "error": {
                        "code": "UNSUPPORTED_FILE_TYPE",
                        "message": "지원하지 않는 파일 형식입니다. (jpg, png만 허용)",
                    },
                },
                status=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            )

        # 4. 선택적 사용자 지정 카테고리 파싱 (전달된 경우)
        categories = None
        raw_categories = request.data.get("categories")
        if raw_categories:
            try:
                if isinstance(raw_categories, str):
                    categories = json.loads(raw_categories)
                elif isinstance(raw_categories, list):
                    categories = raw_categories
            except Exception as e:
                logger.warning("카테고리 파싱 실패, 기본 카테고리 적용: %s", e)

        # 5. Gemini 멀티모달 분석 실행
        try:
            image_bytes = image_file.read()
            result = analyze_receipt(
                image_bytes=image_bytes,
                mime_type=mime_type,
                categories=categories,
            )
            return Response(result, status=status.HTTP_200_OK)

        except Exception as e:
            logger.error("영수증 분석 중 오류 발생: %s", str(e), exc_info=True)
            return Response(
                {
                    "success": False,
                    "error": {
                        "code": "AI_UNAVAILABLE",
                        "message": "AI 서비스에 일시적으로 접근할 수 없습니다.",
                    },
                },
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
