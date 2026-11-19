from django.contrib import admin
from django.urls import path
from receipt.views import ReceiptOcrView

urlpatterns = [
    path('admin/', admin.site.urls),
    path('receipts/ocr', ReceiptOcrView.as_view(), name='receipt-ocr'),
    path('api/v1/receipts/ocr', ReceiptOcrView.as_view(), name='receipt-ocr-v1'),
]
