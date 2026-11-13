import io
from unittest.mock import patch, MagicMock
from django.test import TestCase
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework import status
from rest_framework.test import APIClient

from receipt.services import ReceiptData, Category


class ReceiptOcrApiTest(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.url = "/receipts/ocr"

    def test_missing_image_returns_400(self):
        """image 필드가 없으면 400 VALIDATION_ERROR를 반환한다."""
        response = self.client.post(self.url, {}, format="multipart")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        data = response.json()
        self.assertFalse(data["success"])
        self.assertEqual(data["error"]["code"], "VALIDATION_ERROR")
        self.assertEqual(data["error"]["details"][0]["field"], "image")
        self.assertEqual(data["error"]["details"][0]["reason"], "REQUIRED")

    def test_unsupported_file_type_returns_415(self):
        """지원하지 않는 파일(txt 등)을 업로드하면 415 UNSUPPORTED_FILE_TYPE을 반환한다."""
        file = SimpleUploadedFile("sample.txt", b"plain text", content_type="text/plain")
        response = self.client.post(self.url, {"image": file}, format="multipart")
        self.assertEqual(response.status_code, status.HTTP_415_UNSUPPORTED_MEDIA_TYPE)
        data = response.json()
        self.assertFalse(data["success"])
        self.assertEqual(data["error"]["code"], "UNSUPPORTED_FILE_TYPE")

    def test_file_too_large_returns_413(self):
        """10MB를 초과하는 파일을 업로드하면 413 FILE_TOO_LARGE를 반환한다."""
        # 10MB + 1 byte dummy file
        large_content = b"a" * (10 * 1024 * 1024 + 1)
        file = SimpleUploadedFile("huge.jpg", large_content, content_type="image/jpeg")
        response = self.client.post(self.url, {"image": file}, format="multipart")
        self.assertEqual(response.status_code, status.HTTP_413_REQUEST_ENTITY_TOO_LARGE)
        data = response.json()
        self.assertFalse(data["success"])
        self.assertEqual(data["error"]["code"], "FILE_TOO_LARGE")

    @patch("receipt.services.get_genai_client")
    def test_successful_ocr_analysis(self, mock_get_client):
        """정상적인 영수증 이미지 분석 성공 시 200 OK 및 명세 규격에 맞는 데이터를 반환한다."""
        mock_client = MagicMock()
        mock_response = MagicMock()
        mock_response.parsed = ReceiptData(
            amount=23500,
            transactionDate="2026-11-04",
            category=Category(id=2, name="배달", type="EXPENSE"),
            memo="배달의민족 주문",
        )
        mock_client.models.generate_content.return_value = mock_response
        mock_get_client.return_value = mock_client

        dummy_img = SimpleUploadedFile("receipt.jpg", b"dummy image bytes", content_type="image/jpeg")
        response = self.client.post(self.url, {"image": dummy_img}, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["data"]["amount"], 23500)
        self.assertEqual(data["data"]["transactionDate"], "2026-11-04")
        self.assertEqual(data["data"]["category"]["id"], 2)
        self.assertEqual(data["data"]["category"]["name"], "배달")
        self.assertEqual(data["data"]["category"]["type"], "EXPENSE")
        self.assertEqual(data["data"]["memo"], "배달의민족 주문")
        self.assertEqual(data["data"]["missingFields"], [])

    @patch("receipt.services.get_genai_client")
    def test_partial_fields_identifies_missing_fields(self, mock_get_client):
        """금액이나 일자를 식별하지 못한 경우 missingFields에 누락된 필드가 담긴다."""
        mock_client = MagicMock()
        mock_response = MagicMock()
        mock_response.parsed = ReceiptData(
            amount=None,
            transactionDate="2026-11-04",
            category=Category(id=1, name="식비", type="EXPENSE"),
            memo=None,
        )
        mock_client.models.generate_content.return_value = mock_response
        mock_get_client.return_value = mock_client

        dummy_img = SimpleUploadedFile("receipt.png", b"dummy image bytes", content_type="image/png")
        response = self.client.post(self.url, {"image": dummy_img}, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.json()
        self.assertTrue(data["success"])
        self.assertIsNone(data["data"]["amount"])
        self.assertIsNone(data["data"]["memo"])
        self.assertIn("amount", data["data"]["missingFields"])
        self.assertIn("memo", data["data"]["missingFields"])
        self.assertNotIn("transactionDate", data["data"]["missingFields"])
        self.assertNotIn("category", data["data"]["missingFields"])

    @patch("receipt.services.get_genai_client")
    def test_ai_unavailable_returns_503(self, mock_get_client):
        """Gemini API 호출 실패 시 503 AI_UNAVAILABLE을 반환한다."""
        mock_client = MagicMock()
        mock_client.models.generate_content.side_effect = Exception("Vertex AI connection failed")
        mock_get_client.return_value = mock_client

        dummy_img = SimpleUploadedFile("receipt.jpg", b"dummy image bytes", content_type="image/jpeg")
        response = self.client.post(self.url, {"image": dummy_img}, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        data = response.json()
        self.assertFalse(data["success"])
        self.assertEqual(data["error"]["code"], "AI_UNAVAILABLE")
