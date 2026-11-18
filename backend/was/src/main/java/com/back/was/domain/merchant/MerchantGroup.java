package com.back.was.domain.merchant;

/**
 * 가맹점 업종을 앱에서 쓰는 큰 묶음으로 줄인 값. 표준산업분류(KSIC)는 너무 잘게 나뉘어 있어서
 * 화면 필터와 코칭 추천에는 이 묶음을 쓴다.
 *
 * <p>{@code appCategoryName} 은 가계부 지출 카테고리(ERD 4장 시드)의 이름이다. 코칭이 "이번 달 식비가
 * 많다"를 보고 어떤 가맹점을 추천할지 정할 때 쓴다. 카테고리에는 쇼핑·병원 같은 항목이 없어서
 * 해당하는 묶음은 모두 "기타" 로 이어진다.
 */
public enum MerchantGroup {

    FOOD("식당·주점", "식비"),
    CAFE("카페", "카페"),
    GROCERY("마트·편의점", "식비"),
    SHOPPING("쇼핑", "기타"),
    TRANSPORT("교통·주유", "교통"),
    HEALTH("병원·약국", "기타"),
    EDU("교육·학원", "기타"),
    LEISURE("문화·여가", "기타"),
    LIFE("생활서비스", "기타"),
    ETC("기타", "기타");

    private final String label;
    private final String appCategoryName;

    MerchantGroup(String label, String appCategoryName) {
        this.label = label;
        this.appCategoryName = appCategoryName;
    }

    public String label() {
        return label;
    }

    public String appCategoryName() {
        return appCategoryName;
    }

    /**
     * 가계부 지출 카테고리 이름 → 그 소비를 받아 줄 수 있는 가맹점 묶음. {@link #appCategoryName} 의 역방향에
     * 배달만 예외를 둔다: 배달 지출이 많은 사람에게는 동네 식당·마트가 대안이다. 월세는 추천 대상이 아니다.
     */
    public static java.util.List<MerchantGroup> forCategoryName(String categoryName) {
        if (categoryName == null) {
            return java.util.List.of();
        }
        return switch (categoryName) {
            case "배달" -> java.util.List.of(FOOD, GROCERY);
            case "월세" -> java.util.List.of();
            default -> java.util.Arrays.stream(values())
                    .filter(g -> g != ETC && g.appCategoryName.equals(categoryName))
                    .toList();
        };
    }
}
