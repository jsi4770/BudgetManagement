package com.back.was.domain.merchant;

import com.back.was.domain.merchant.dto.MerchantResponse;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocalMerchantService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final LocalMerchantRepository merchantRepository;
    private final UserRepository userRepository;

    /**
     * 내 지역(없으면 {@code regionCode} 파라미터)의 가맹점을 상호순으로 돌려준다.
     *
     * @param regionCode 선택. 주면 내 설정보다 우선한다 (지역을 아직 안 정한 사용자의 둘러보기용)
     * @param group      선택. null 이면 전체
     * @param q          선택. 상호 부분 일치(대소문자 무시)
     * @param page       0부터
     */
    @Transactional(readOnly = true)
    public PageResponse<MerchantResponse> search(Long userId, String regionCode, MerchantGroup group,
                                                 String q, int page, int size) {
        String region = resolveRegion(userId, regionCode);
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("name").and(Sort.by("id")));
        String keyword = q == null ? "" : q.strip();

        Page<LocalMerchant> result = group == null
                ? merchantRepository.findByRegionCodeAndNameContainingIgnoreCase(region, keyword, pageable)
                : merchantRepository.findByRegionCodeAndMerchantGroupAndNameContainingIgnoreCase(
                        region, group, keyword, pageable);

        return PageResponse.of(
                result.getContent().stream().map(MerchantResponse::from).toList(),
                safePage, safeSize, result.getTotalElements());
    }

    private String resolveRegion(Long userId, String regionCode) {
        if (regionCode != null && !regionCode.isBlank()) {
            return SupportedRegion.fromCode(regionCode.strip())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 지역이에요."))
                    .code();
        }
        User user = userRepository.findById(userId).orElseThrow(BusinessException::notFound);
        if (user.getRegionCode() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "내 지역을 먼저 설정해 주세요.");
        }
        return user.getRegionCode();
    }
}
