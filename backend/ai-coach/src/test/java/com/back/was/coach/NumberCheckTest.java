package com.back.was.coach;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NumberCheckTest {
    private final NumberCheck nc = new NumberCheck(TestSupport.OM);

    @Test void manWonNotationIsAccepted() {
        Set<Double> allowed = nc.allowed(Map.of("amount", 346700));
        assertTrue(nc.unknownNumbers(List.of("배달이 약 34.7만 원이에요"), allowed).isEmpty());
        assertTrue(nc.unknownNumbers(List.of("346,700원 썼어요"), allowed).isEmpty());
    }

    @Test void splitManWonIsNormalized() {
        assertEquals("1054600원", NumberCheck.normalize("105만 4,600원"));
        Set<Double> allowed = nc.allowed(Map.of("v", 1054600));
        assertTrue(nc.unknownNumbers(List.of("105만 4,600원"), allowed).isEmpty());
    }

    @Test void inventedNumberIsFlaggedButSmallIntegersAndDatesAreNot() {
        Set<Double> allowed = nc.allowed(Map.of("v", 100000));
        assertEquals(1, nc.unknownNumbers(List.of("777,000원"), allowed).size());
        assertTrue(nc.unknownNumbers(List.of("2026-11 기준 3번, 12개월"), allowed).isEmpty());
    }
}
