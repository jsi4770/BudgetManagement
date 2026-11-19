package com.back.was.domain.merchant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantClassifierTest {

    @Test
    @DisplayName("561 음식점은 식당·주점이다")
    void restaurant() {
        assertThat(MerchantClassifier.classify("561", "네네치킨성정점")).isEqualTo(MerchantGroup.FOOD);
    }

    @Test
    @DisplayName("562 는 상호에 카페 키워드가 있으면 카페, 없으면 식당·주점이다")
    void cafeIsSplitFromBarByName() {
        assertThat(MerchantClassifier.classify("562", "스타벅스 천안터미널점")).isEqualTo(MerchantGroup.CAFE);
        assertThat(MerchantClassifier.classify("562", "Blue COFFEE")).isEqualTo(MerchantGroup.CAFE);
        assertThat(MerchantClassifier.classify("562", "황소포차")).isEqualTo(MerchantGroup.FOOD);
    }

    @Test
    @DisplayName("소매업은 마트·편의점 / 주유 / 쇼핑으로 갈린다")
    void retail() {
        assertThat(MerchantClassifier.classify("471", "GS25")).isEqualTo(MerchantGroup.GROCERY);
        assertThat(MerchantClassifier.classify("472", "정육점")).isEqualTo(MerchantGroup.GROCERY);
        assertThat(MerchantClassifier.classify("473", "OO주유소")).isEqualTo(MerchantGroup.TRANSPORT);
        assertThat(MerchantClassifier.classify("477", "의류 매장")).isEqualTo(MerchantGroup.SHOPPING);
    }

    @Test
    @DisplayName("서비스업은 중분류로 묶인다")
    void services() {
        assertThat(MerchantClassifier.classify("961", "미용실")).isEqualTo(MerchantGroup.LIFE);
        assertThat(MerchantClassifier.classify("855", "OO학원")).isEqualTo(MerchantGroup.EDU);
        assertThat(MerchantClassifier.classify("862", "OO의원")).isEqualTo(MerchantGroup.HEALTH);
        assertThat(MerchantClassifier.classify("911", "스크린골프")).isEqualTo(MerchantGroup.LEISURE);
        assertThat(MerchantClassifier.classify("491", "택시")).isEqualTo(MerchantGroup.TRANSPORT);
    }

    @Test
    @DisplayName("모르는 코드·빈 값은 기타이고 예외를 던지지 않는다")
    void unknownFallsBackToEtc() {
        assertThat(MerchantClassifier.classify("751", "사무 지원")).isEqualTo(MerchantGroup.ETC);
        assertThat(MerchantClassifier.classify(null, null)).isEqualTo(MerchantGroup.ETC);
        assertThat(MerchantClassifier.classify("", "")).isEqualTo(MerchantGroup.ETC);
    }

    @Test
    @DisplayName("묶음은 가계부 지출 카테고리 이름으로 이어진다 — 쇼핑·병원 등은 기타")
    void appCategoryMapping() {
        assertThat(MerchantGroup.FOOD.appCategoryName()).isEqualTo("식비");
        assertThat(MerchantGroup.GROCERY.appCategoryName()).isEqualTo("식비");
        assertThat(MerchantGroup.CAFE.appCategoryName()).isEqualTo("카페");
        assertThat(MerchantGroup.TRANSPORT.appCategoryName()).isEqualTo("교통");
        assertThat(MerchantGroup.SHOPPING.appCategoryName()).isEqualTo("기타");
    }
}
