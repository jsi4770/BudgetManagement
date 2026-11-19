package com.back.was.domain.merchant;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자가 방금 보낸 말이 <b>우리 동네 지역화폐 가맹점을 묻는 것인지</b>, 묻는다면 어떤 업종인지 읽는다.
 *
 * <p>모델에 맡기지 않고 규칙으로 정한다. "묻는 말에만 카드가 나온다"는 약속이 모델의 기분에 흔들리면
 * 안 되고, 어떤 후보를 AI 에게 줄지도 AI 호출 전에 정해야 하기 때문이다.
 *
 * <ul>
 *   <li>가맹점을 직접 가리키는 말(가맹점·지역화폐·상품권·사용처 …)이 있으면 가맹점 질문이다.</li>
 *   <li>업종 말(카페·식당·마트 …)만 있으면, "어디·추천·근처" 같은 장소를 묻는 말이 함께 있을 때만 가맹점 질문이다.
 *       "카페 지출이 늘었어요" 같은 소비 이야기는 가맹점 질문이 아니다.</li>
 * </ul>
 *
 * <p>업종 말이 하나도 없는 가맹점 질문("가맹점 알려줘")은 {@code groups} 가 비어 있다 — 호출자가 사용자의
 * 지출 상위 카테고리 등으로 채운다.
 */
public final class MerchantIntent {

    /** 가맹점을 직접 가리키는 말. */
    private static final List<String> DIRECT = List.of(
            "가맹점", "지역화폐", "지역 화폐", "지역사랑", "상품권", "사용처", "쓸 수 있는 곳", "쓸수있는곳", "쓸 수 있는 데");

    /** 업종 말이 가맹점 질문이 되려면 함께 있어야 하는 물음. */
    private static final List<String> ASK = List.of(
            "어디", "추천", "찾아", "찾고", "근처", "가까운", "갈만", "갈 만", "갈까", "골라", "소개");

    private static final Map<MerchantGroup, List<String>> GROUP_WORDS = new LinkedHashMap<>();

    static {
        GROUP_WORDS.put(MerchantGroup.CAFE, List.of("카페", "커피", "디저트", "베이커리", "빵집", "제과"));
        GROUP_WORDS.put(MerchantGroup.FOOD, List.of("식당", "음식점", "밥집", "맛집", "밥 ", "밥을", "밥이", "점심", "저녁", "외식",
                "술집", "주점", "치킨", "피자", "고기", "한식", "중식", "분식"));
        GROUP_WORDS.put(MerchantGroup.GROCERY, List.of("마트", "편의점", "장보", "슈퍼", "식료품", "과일", "정육"));
        GROUP_WORDS.put(MerchantGroup.SHOPPING, List.of("쇼핑", "옷", "의류", "신발", "가방", "안경", "꽃집", "문구"));
        GROUP_WORDS.put(MerchantGroup.TRANSPORT, List.of("주유", "주유소", "택시", "정비", "세차", "교통"));
        GROUP_WORDS.put(MerchantGroup.HEALTH, List.of("병원", "약국", "의원", "치과", "한의원", "안과", "피부과", "정형외과"));
        GROUP_WORDS.put(MerchantGroup.EDU, List.of("학원", "교육", "서점", "독서실", "과외"));
        GROUP_WORDS.put(MerchantGroup.LEISURE, List.of("영화", "노래방", "pc방", "피시방", "헬스", "운동", "수영", "여가", "문화", "볼링", "당구"));
        GROUP_WORDS.put(MerchantGroup.LIFE, List.of("미용", "미용실", "이발", "세탁", "수리", "부동산", "생활서비스"));
    }

    private final Set<MerchantGroup> groups;

    private MerchantIntent(Set<MerchantGroup> groups) {
        this.groups = groups;
    }

    /** 질문에서 읽은 업종. 비어 있으면 "가맹점이 궁금하다"만 알 수 있는 경우다. */
    public Set<MerchantGroup> groups() {
        return groups;
    }

    /**
     * @return 가맹점을 묻는 말이면 그 의도, 아니면 비어 있음
     */
    public static Optional<MerchantIntent> detect(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String text = message.toLowerCase().replaceAll("\\s+", " ").strip();
        String compact = text.replace(" ", "");

        boolean direct = DIRECT.stream().anyMatch(w -> text.contains(w) || compact.contains(w.replace(" ", "")));

        Set<MerchantGroup> found = EnumSet.noneOf(MerchantGroup.class);
        for (Map.Entry<MerchantGroup, List<String>> e : GROUP_WORDS.entrySet()) {
            // "밥 " 처럼 뒤에 공백이 붙은 단어는 문장 끝("…밥")도 잡도록 끝에 공백을 하나 붙여 본다.
            if (e.getValue().stream().anyMatch(w -> (text + " ").contains(w))) {
                found.add(e.getKey());
            }
        }

        boolean asks = ASK.stream().anyMatch(text::contains);
        if (!direct && !(asks && !found.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(new MerchantIntent(found));
    }
}
