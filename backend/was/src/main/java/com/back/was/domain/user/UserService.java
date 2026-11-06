package com.back.was.domain.user;

import com.back.was.domain.user.dto.MeResponse;
import com.back.was.domain.user.dto.UpdateMeRequest;
import com.back.was.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** USER-01 내 정보 조회, USER-02 내 정보 수정. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return MeResponse.from(findOrThrow(userId));
    }

    /** 명세 USER-02: 보낸 필드만 수정한다. 아무 필드도 없으면 현재 정보를 그대로 돌려준다. */
    @Transactional
    public MeResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = findOrThrow(userId);

        if (request.hasUsername()) {
            user.changeUsername(request.username());
        }

        return MeResponse.from(user);
    }

    /**
     * 토큰은 유효한데 행이 없다면 계정이 삭제된 경우다. 명세 0장 5)에 따라 404 로
     * 내린다 — 이 엔드포인트는 항상 '나'만 보므로 남의 리소스 문제는 생기지 않는다.
     */
    private User findOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(BusinessException::notFound);
    }
}
