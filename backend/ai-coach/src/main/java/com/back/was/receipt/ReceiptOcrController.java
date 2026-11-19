package com.back.was.receipt;

import com.back.was.common.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** RCPT-01 POST /receipts/ocr (multipart/form-data, 필드명 image) */
@RestController
@RequestMapping("/receipts")
public class ReceiptOcrController {

    private final ReceiptOcrService service;

    public ReceiptOcrController(ReceiptOcrService service) {
        this.service = service;
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> ocr(@RequestParam(value = "image", required = false) MultipartFile image) {
        return ApiResponses.ok(service.analyze(image));
    }
}
