package com.back.was.coach;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 프롬프트는 resources/prompts/*.txt 에 둔다 (원본 Python 프롬프트와 동일, 코드 수정 없이 튜닝 가능) */
final class Prompts {
    private Prompts() {}

    static String load(String name) {
        try (var in = new ClassPathResource("prompts/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
