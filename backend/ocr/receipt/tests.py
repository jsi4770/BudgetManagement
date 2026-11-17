import io
from unittest.mock import patch, MagicMock
from django.test import TestCase
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework import status
from rest_framework.test import APIClient

from receipt.services import BankStatementData, TransactionItem, Category


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
        mock_response.parsed = BankStatementData(
            items=[
                TransactionItem(
                    amount=23500,
                    transactionDate="2026-11-04",
                    category=Category(id=2, name="배달", type="EXPENSE"),
                    memo="배달의민족 주문",
                )
            ]
        )
        mock_client.models.generate_content.return_value = mock_response
        mock_get_client.return_value = mock_client

        dummy_img = SimpleUploadedFile("receipt.jpg", b"dummy image bytes", content_type="image/jpeg")
        response = self.client.post(self.url, {"image": dummy_img}, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.json()
        self.assertTrue(data["success"])
        self.assertEqual(len(data["data"]["items"]), 1)
        item = data["data"]["items"][0]
        self.assertEqual(item["amount"], 23500)
        self.assertEqual(item["transactionDate"], "2026-11-04")
        self.assertEqual(item["category"]["id"], 2)
        self.assertEqual(item["category"]["name"], "배달")
        self.assertEqual(item["category"]["type"], "EXPENSE")
        self.assertEqual(item["memo"], "배달의민족 주문")
        self.assertEqual(item["missingFields"], [])

    @patch("receipt.services.get_genai_client")
    def test_bank_statement_multiple_transactions(self, mock_get_client):
        """금융 앱 거래내역 캡처 시 입금(INCOME)과 출금(EXPENSE) 다건을 반환한다."""
        mock_client = MagicMock()
        mock_response = MagicMock()
        mock_response.parsed = BankStatementData(
            items=[
                TransactionItem(
                    amount=500000,
                    transactionDate="2026-11-18",
                    category=Category(id=8, name="용돈", type="INCOME"),
                    memo="부모님 송금",
                ),
                TransactionItem(
                    amount=4500,
                    transactionDate="2026-11-18",
                    category=Category(id=3, name="카페", type="EXPENSE"),
                    memo="스타벅스",
                ),
            ]
        )
        mock_client.models.generate_content.return_value = mock_response
        mock_get_client.return_value = mock_client

        dummy_img = SimpleUploadedFile("statement.png", b"dummy image bytes", content_type="image/png")
        response = self.client.post(self.url, {"image": dummy_img}, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.json()
        self.assertTrue(data["success"])
        items = data["data"]["items"]
        self.assertEqual(len(items), 2)

        # 1번째: 입금 (INCOME)
        self.assertEqual(items[0]["amount"], 500000)
        self.assertEqual(items[0]["category"]["type"], "INCOME")
        self.assertEqual(items[0]["category"]["name"], "용돈")
        self.assertEqual(items[0]["memo"], "부모님 송금")
        self.assertEqual(items[0]["missingFields"], [])

        # 2번째: 출금 (EXPENSE)
        self.assertEqual(items[1]["amount"], 4500)
        self.assertEqual(items[1]["category"]["type"], "EXPENSE")
        self.assertEqual(items[1]["category"]["name"], "카페")
        self.assertEqual(items[1]["memo"], "스타벅스")
        self.assertEqual(items[1]["missingFields"], [])

    @patch("receipt.services.get_genai_client")
    def test_partial_fields_identifies_missing_fields(self, mock_get_client):
        """금액이나 일자를 식별하지 못한 경우 missingFields에 누락된 필드가 담긴다."""
        mock_client = MagicMock()
        mock_response = MagicMock()
        mock_response.parsed = BankStatementData(
            items=[
                TransactionItem(
                    amount=None,
                    transactionDate="2026-11-04",
                    category=Category(id=1, name="식비", type="EXPENSE"),
                    memo=None,
                )
            ]
        )
        mock_client.models.generate_content.return_value = mock_response
        mock_get_client.return_value = mock_client

        dummy_img = SimpleUploadedFile("receipt.png", b"dummy image bytes", content_type="image/png")
        response = self.client.post(self.url, {"image": dummy_img}, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.json()
        self.assertTrue(data["success"])
        item = data["data"]["items"][0]
        self.assertIsNone(item["amount"])
        self.assertIsNone(item["memo"])
        self.assertIn("amount", item["missingFields"])
        self.assertIn("memo", item["missingFields"])
        self.assertNotIn("transactionDate", item["missingFields"])
        self.assertNotIn("category", item["missingFields"])

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
