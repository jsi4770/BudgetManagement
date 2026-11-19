package com.back.was.domain.user;

import com.back.was.domain.user.dto.MeResponse;
import com.back.was.domain.user.dto.OnboardingRequest;
import com.back.was.domain.user.dto.UpdateMeRequest;
import com.back.was.global.support.KstTime;
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
        if (request.hasRegionCode()) {
            user.changeRegionCode(request.regionCode());
        }
        if (request.hasAge()) {
            user.changeBirthYear(birthYearOf(request.age()));
        }
        if (request.hasGender()) {
            user.changeGender(request.gender());
        }

        return MeResponse.from(user);
    }

    /** 온보딩 — 지역·나이·성별을 한 번에 저장한다. 다시 호출하면 덮어쓴다(설정에서 고칠 수 있게). */
    @Transactional
    public MeResponse completeOnboarding(Long userId, OnboardingRequest request) {
        User user = findOrThrow(userId);
        user.changeRegionCode(request.regionCode());
        user.changeBirthYear(birthYearOf(request.age()));
        user.changeGender(request.gender());
        return MeResponse.from(user);
    }

    private static int birthYearOf(int age) {
        return KstTime.today().getYear() - age;
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
