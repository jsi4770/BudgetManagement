package com.back.was.domain.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ERD 4장 "시드 데이터 (서비스 시작 전에 INSERT)" 를 부팅 시점에 넣는다.
 *
 * <p>Flyway/Liquibase 가 아직 없어서({@code ddl-auto} 가 스키마를 만든다) 시드도 코드에
 * 둔다. 마이그레이션 도구를 도입하면 이 클래스 대신 마이그레이션 스크립트로 옮기는 게
 * 맞다.
 *
 * <p><b>표의 순서가 곧 ID 다.</b> 빈 테이블에 이 순서로 넣어야 ERD 의 ID 1~9 와 일치하고,
 * 특히 월세는 ERD 4장이 "ID 5" 를 코드 상수로 참조하라고 못박았다(월간 분석·넛지에서
 * 제외 대상). 그래서 이미 있는 행은 건드리지 않고 <b>없는 것만</b> 추가한다 — 재기동
 * 때마다 지우고 다시 넣으면 거래가 참조하는 FK 가 깨지고 ID 도 밀린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    /** ERD 4장 시드 표 그대로. 순서를 바꾸면 새 DB 에서 ID 가 달라진다. */
    private static final List<Seed> SEEDS = List.of(
            new Seed("식비", CategoryType.EXPENSE),
            new Seed("배달", CategoryType.EXPENSE),
            new Seed("카페", CategoryType.EXPENSE),
            new Seed("교통", CategoryType.EXPENSE),
            new Seed("월세", CategoryType.EXPENSE),
            new Seed("기타", CategoryType.EXPENSE),
            new Seed("급여", CategoryType.INCOME),
            new Seed("용돈", CategoryType.INCOME),
            new Seed("기타", CategoryType.INCOME)
    );

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int inserted = 0;
        for (Seed seed : SEEDS) {
            if (!categoryRepository.existsByNameAndType(seed.name(), seed.type())) {
                categoryRepository.save(Category.of(seed.name(), seed.type()));
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("카테고리 시드 {}건 추가", inserted);
        }
    }

    private record Seed(String name, CategoryType type) {
    }
}
