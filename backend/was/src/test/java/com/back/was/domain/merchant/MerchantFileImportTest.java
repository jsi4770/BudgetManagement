package com.back.was.domain.merchant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 번들된 CSV 를 실제로 읽어 DB 에 넣어 본다. 트랜잭션은 서비스가 직접 커밋하므로 끝에 비운다. */
@SpringBootTest
class MerchantFileImportTest {

    @Autowired
    MerchantFileImportService importService;

    @Autowired
    LocalMerchantRepository repository;

    @Test
    @DisplayName("CSV 한 줄 파서: 따옴표 안의 쉼표와 \"\" 이스케이프를 처리한다")
    void parsesQuotedCells() {
        assertThat(MerchantFileImportService.parseLine("\"A,B\",\"그는 \"\"좋아\"\"\",x,,FOOD"))
                .containsExactly("A,B", "그는 \"좋아\"", "x", "", "FOOD");
    }

    @Test
    @DisplayName("두 지역 CSV 를 적재하면 지역별로 쌓이고, 다시 적재해도 건수가 같다(멱등)")
    void importsBothRegionsIdempotently() {
        try {
            int cheonan = importService.importRegion(SupportedRegion.CHEONAN);
            int asan = importService.importRegion(SupportedRegion.ASAN);

            assertThat(cheonan).isGreaterThan(9000);
            assertThat(asan).isGreaterThan(12000);
            assertThat(repository.countByRegionCode("44130")).isEqualTo(cheonan);
            assertThat(repository.countByRegionCode("44200")).isEqualTo(asan);

            importService.importRegion(SupportedRegion.ASAN);
            assertThat(repository.countByRegionCode("44200")).isEqualTo(asan);

            var cafes = repository.findByRegionCodeAndMerchantGroupIn("44200", java.util.List.of(MerchantGroup.CAFE), PageRequest.of(0, 5));
            assertThat(cafes).isNotEmpty();
            assertThat(cafes.get(0).getBaseDate()).hasYear(2026);
        } finally {
            repository.deleteAll();
        }
    }
}
