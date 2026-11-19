package com.back.was.dev;

import com.back.was.common.ApiResponses;
import com.back.was.common.CurrentUserProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** DEV-01 POST /dev/seed (시연 계정 전용 테스트 데이터 초기화) */
@RestController
@RequestMapping("/dev")
public class DevSeedController {

    private final DevSeedService service;
    private final CurrentUserProvider currentUser;

    public DevSeedController(DevSeedService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return ApiResponses.ok(service.seed(currentUser.currentUserId()));
    }
}
