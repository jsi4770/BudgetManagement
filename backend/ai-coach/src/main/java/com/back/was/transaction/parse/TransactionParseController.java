package com.back.was.transaction.parse;

import com.back.was.common.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * TX-07 POST /transactions/parse
 * ※ 팀 컨트롤러들이 /api/v1 을 직접 붙이는 방식이면 @RequestMapping 을 맞춰 주세요.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionParseController {

    private final TransactionParseService service;

    public TransactionParseController(TransactionParseService service) {
        this.service = service;
    }

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody ParseRequest request) {
        return ApiResponses.ok(service.parse(request.text()));
    }
}
