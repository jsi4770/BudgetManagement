package com.back.was.common;

import com.back.was.dev.InMemoryLedger;
import org.springframework.stereotype.Component;

/** 단독 실행용: 항상 시연 계정. 팀 프로젝트에 붙일 때 JWT 의 사용자 id 를 꺼내도록 교체 */
@Component
public class CurrentUserProviderImpl implements CurrentUserProvider {

    @Override
    public Long currentUserId() {
        return InMemoryLedger.DEMO_USER_ID;
    }
}
