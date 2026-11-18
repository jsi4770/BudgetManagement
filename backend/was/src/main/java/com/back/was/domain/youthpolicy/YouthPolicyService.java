package com.back.was.domain.youthpolicy;

import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.domain.youthpolicy.dto.YouthPolicyResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.support.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YouthPolicyService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final YouthPolicyRepository repository;
    private final UserRepository userRepository;

    /**
     * 홈 롤링용 — 내 지역이 대상이고, 내 나이가 맞고, 아직 신청할 수 있는 정책.
     *
     * <p>나이를 모르면(온보딩 전) 나이는 거르지 않는다. 정렬: 대상 시군이 적은(지역 맞춤) 정책 → 마감 임박 순
     * → 상시 정책 → 이름. 지역이 없으면 맞출 기준이 없어 400 이다.
     */
    @Transactional(readOnly = true)
    public List<YouthPolicyResponse> home(Long userId, Integer limit) {
        User user = userRepository.findById(userId).orElseThrow(BusinessException::notFound);
        if (user.getRegionCode() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "내 지역을 먼저 설정해 주세요.");
        }
        LocalDate today = KstTime.today();
        Integer age = user.ageIn(today.getYear());
        int size = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        return repository.findOpenForRegion(user.getRegionCode(), today).stream()
                .filter(p -> age == null || p.fitsAge(age))
                .sorted(Comparator
                        .comparing((YouthPolicy p) -> p.getZipCount() > 3)
                        .thenComparing(YouthPolicy::getApplyEnd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(YouthPolicy::getName)
                        .thenComparing(YouthPolicy::getId))
                .limit(size)
                .map(p -> YouthPolicyResponse.of(p, today))
                .toList();
    }
}
