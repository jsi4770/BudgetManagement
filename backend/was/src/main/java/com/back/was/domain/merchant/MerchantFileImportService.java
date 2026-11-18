package com.back.was.domain.merchant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 지자체가 공개한 가맹점 목록(CSV) → {@code local_merchants} 적재.
 *
 * <p>공공 API(통합 가맹점정보)는 기준일이 2023-11 이라 낡았다. 아산은 2026-10 엑셀, 천안은 2024-12-19 엑셀에
 * 2026-07 등록취소 명단을 반영한 파일이 더 최신이라 그쪽을 쓴다. 엑셀·PDF 는 {@code tools/merchant-data/convert.py}
 * 로 {@code resources/merchants/*.csv} 로 바꿔 두었고, 이 서비스는 그 CSV 만 읽는다(엑셀 라이브러리 불필요).
 *
 * <p>고유 키는 지역+상호+주소의 해시다(파일에 가맹점 ID 가 없어서). 다시 적재하면 같은 행은 갱신, 파일에서
 * 빠진 행은 삭제한다. 한 번에 하나만 돈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantFileImportService {

    /** 지역별 원본 파일과 그 파일의 기준일(천안은 2026-07 등록취소 17곳을 뺐지만 신규 등록은 못 담은 2024-12 기준). 새 파일을 받으면 날짜도 바꾼다. */
    private record Source(String file, LocalDate baseDate) {}

    private static final Map<SupportedRegion, Source> SOURCES = Map.of(
            SupportedRegion.ASAN, new Source("merchants/asan.csv", LocalDate.of(2026, 10, 6)),
            SupportedRegion.CHEONAN, new Source("merchants/cheonan.csv", LocalDate.of(2024, 12, 19)));

    private static final int BATCH = 1000;

    private final LocalMerchantRepository repository;
    private final TransactionTemplate tx;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** @return 저장·갱신한 건수. 이미 도는 중이면 -1 */
    public int importRegion(SupportedRegion region) {
        if (!running.compareAndSet(false, true)) {
            log.info("가맹점 파일 적재가 이미 진행 중이라 {} 요청을 건너뜁니다", region.displayName());
            return -1;
        }
        try {
            return doImport(region);
        } finally {
            running.set(false);
        }
    }

    private int doImport(SupportedRegion region) {
        Source source = SOURCES.get(region);
        List<String[]> rows = readCsv(source.file());
        LocalDateTime startedAt = LocalDateTime.now();

        int saved = 0;
        for (int from = 0; from < rows.size(); from += BATCH) {
            List<String[]> chunk = rows.subList(from, Math.min(from + BATCH, rows.size()));
            saved += tx.execute(status -> upsert(region, source.baseDate(), chunk, startedAt));
        }
        int removed = saved > 0 ? tx.execute(status -> repository.deleteStale(region.code(), startedAt)) : 0;
        log.info("가맹점 파일 적재 완료 {} — 저장·갱신 {}건, 정리 {}건 (기준일 {})",
                region.displayName(), saved, removed, source.baseDate());
        return saved;
    }

    private int upsert(SupportedRegion region, LocalDate baseDate, List<String[]> chunk, LocalDateTime syncedAt) {
        Map<String, String[]> byKey = new LinkedHashMap<>();
        for (String[] r : chunk) {
            byKey.put(key(region, r[0], r[1]), r);
        }
        Map<String, LocalMerchant> existing = repository.findByAltTextIn(byKey.keySet()).stream()
                .collect(Collectors.toMap(LocalMerchant::getAltText, m -> m));

        List<LocalMerchant> toSave = byKey.entrySet().stream().map(e -> {
            String[] r = e.getValue();   // name, address, emd, phone, group
            LocalMerchant m = existing.getOrDefault(e.getKey(), LocalMerchant.create(e.getKey(), region.code()));
            m.refresh(r[0], r[2], r[1], blankToNull(r[3]), null, null, null, null,
                    parseGroup(r[4]), baseDate, syncedAt);
            return m;
        }).toList();
        repository.saveAll(toSave);
        return toSave.size();
    }

    private static MerchantGroup parseGroup(String s) {
        try {
            return MerchantGroup.valueOf(s.strip());
        } catch (IllegalArgumentException e) {
            return MerchantGroup.ETC;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static String key(SupportedRegion region, String name, String address) {
        try {
            String raw = region.code() + "|" + name.strip() + "|" + address.strip();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 은 모든 JVM 이 제공한다
        }
    }

    private static List<String[]> readCsv(String path) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String header = in.readLine();   // name,address,emd,phone,group
            if (header == null) {
                return rows;
            }
            String line;
            while ((line = in.readLine()) != null) {
                String[] cells = parseLine(line);
                if (cells.length >= 5 && !cells[0].isBlank()) {
                    rows.add(cells);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("가맹점 CSV 를 읽지 못했어요: " + path, e);
        }
    }

    /** 따옴표로 감싼 칸(쉼표·큰따옴표 두 개 이스케이프)을 처리하는 한 줄 CSV 파서. 칸 안 줄바꿈은 변환 단계에서 없앴다. */
    static String[] parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
