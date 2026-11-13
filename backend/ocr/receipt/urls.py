from django.urls import path
from .views import ReceiptOcrView

app_name = "receipt"

urlpatterns = [
    path("receipts/ocr", ReceiptOcrView.as_view(), name="receipt-ocr"),
]

