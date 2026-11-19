package com.back.was.domain.merchant;

import com.back.was.domain.ai.MerchantCandidate;
import com.back.was.domain.ai.SpendingFact;
import com.back.was.domain.merchant.dto.MerchantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 코칭 대화에 쓸 지역 가맹점 후보를 고르고, AI 가 고른 ID 를 다시 검증해 응답으로 바꾼다.
 *
 * <p>지출이 큰 카테고리 순으로 그 소비를 받아 줄 가맹점 묶음({@link MerchantGroup#forCategoryName})에서
 * 후보를 뽑는다. 정렬은 {@code id} 라서 같은 사용자에게는 같은 후보가 나온다(대화 도중 후보가 바뀌지 않는다).
 */
@Component
@RequiredArgsConstructor
public class MerchantRecommender {

    /** 지출 상위 몇 개 카테고리에서 후보를 뽑는지. */
    static final int TOP_CATEGORIES = 2;
    /** 카테고리당 후보 수 — 프롬프트를 짧게 유지한다. */
    static final int PER_CATEGORY = 4;

    private final LocalMerchantRepository repository;

    /**
     * @param regionCode 사용자 지역 코드. 없거나 지원 지역이 아니면 빈 목록
     * @param topSpending 금액 큰 순의 지출 사실
     */
    public List<MerchantCandidate> candidates(String regionCode, List<SpendingFact> topSpending) {
        if (regionCode == null || SupportedRegion.fromCode(regionCode).isEmpty()) {
            return List.of();
        }
        List<MerchantCandidate> out = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (SpendingFact fact : topSpending.stream().limit(TOP_CATEGORIES).toList()) {
            List<MerchantGroup> groups = MerchantGroup.forCategoryName(fact.categoryName());
            if (groups.isEmpty()) {
                continue;
            }
            for (LocalMerchant m : repository.findByRegionCodeAndMerchantGroupIn(
                    regionCode, groups, PageRequest.of(0, PER_CATEGORY, Sort.by("id")))) {
                if (seen.add(m.getId())) {
                    out.add(new MerchantCandidate(m.getId(), m.getName(), m.getMerchantGroup().label(), fact.categoryName()));
                }
            }
        }
        return out;
    }

    /** 질문에 업종이 없을 때 쓰는 기본 업종 — 자주 쓰는 동네 가게부터. */
    private static final List<MerchantGroup> DEFAULT_GROUPS = List.of(MerchantGroup.FOOD, MerchantGroup.CAFE, MerchantGroup.GROCERY);
    /** 사용자가 직접 묻는 경우 한 업종에서 뽑는 후보 수. */
    static final int PER_ASKED_GROUP = 5;
    /** 한 번에 다루는 업종 수 상한. */
    static final int MAX_ASKED_GROUPS = 3;

    /**
     * 사용자가 <b>가맹점을 직접 물었을 때</b>의 후보. 물은 업종이 있으면 그 업종에서, 없으면("가맹점 알려줘")
     * 지출 상위 카테고리에 이어지는 업종에서, 그것도 없으면 식당·카페·마트에서 뽑는다.
     *
     * @param regionCode 사용자 지역 코드. 없거나 지원 지역이 아니면 빈 목록
     */
    public List<MerchantCandidate> candidatesFor(String regionCode, MerchantIntent intent, List<SpendingFact> topSpending) {
        if (regionCode == null || SupportedRegion.fromCode(regionCode).isEmpty()) {
            return List.of();
        }
        List<MerchantGroup> groups = new ArrayList<>(intent.groups());
        if (groups.isEmpty()) {
            for (SpendingFact fact : topSpending.stream().limit(TOP_CATEGORIES).toList()) {
                for (MerchantGroup g : MerchantGroup.forCategoryName(fact.categoryName())) {
                    if (!groups.contains(g)) {
                        groups.add(g);
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            groups.addAll(DEFAULT_GROUPS);
        }
        List<MerchantCandidate> out = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (MerchantGroup group : groups.stream().limit(MAX_ASKED_GROUPS).toList()) {
            for (LocalMerchant m : repository.findByRegionCodeAndMerchantGroupIn(
                    regionCode, List.of(group), PageRequest.of(0, PER_ASKED_GROUP, Sort.by("id")))) {
                if (seen.add(m.getId())) {
                    out.add(new MerchantCandidate(m.getId(), m.getName(), m.getMerchantGroup().label(), group.appCategoryName()));
                }
            }
        }
        return out;
    }

    /**
     * 사용자가 가맹점을 물었는데 AI 가 하나도 못 골랐을 때(실패·대체 응답 포함) 서버가 대신 내려 주는 최대 2곳.
     * 업종이 여러 개면 업종마다 하나씩 먼저 고른다. 후보 순서(id 순)가 그대로 유지돼 같은 질문엔 같은 답이 나온다.
     */
    public List<Long> fallbackPicks(List<MerchantCandidate> candidates) {
        List<Long> picks = new ArrayList<>();
        Set<String> groups = new java.util.HashSet<>();
        for (MerchantCandidate c : candidates) {
            if (picks.size() < 2 && groups.add(c.groupLabel())) {
                picks.add(c.id());
            }
        }
        for (MerchantCandidate c : candidates) {
            if (picks.size() < 2 && !picks.contains(c.id())) {
                picks.add(c.id());
            }
        }
        return picks;
    }

    /** AI 가 고른 ID 중 <b>후보에 있던 것</b>만 가맹점 응답으로 바꾼다. */
    public List<MerchantResponse> resolve(List<Long> chosenIds, List<MerchantCandidate> candidates) {
        Set<Long> allowed = candidates.stream().map(MerchantCandidate::id).collect(Collectors.toSet());
        List<Long> ids = chosenIds.stream().filter(allowed::contains).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        var byId = repository.findAllById(ids).stream().collect(Collectors.toMap(LocalMerchant::getId, m -> m));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).map(MerchantResponse::from).toList();
    }
}
