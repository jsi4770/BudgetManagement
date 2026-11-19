package com.back.was.coach;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 페르소나별 목업 거래 데이터(classpath:demo/*.json). 원본 Python 프로젝트의 data/*.json 과 동일 */
@Component
public class DemoData {

    public record Persona(String id, String name, List<Tx> txs) {}

    private static final List<String> IDS = List.of("college_delivery", "rookie_subscription", "frugal_saver");
    private final Map<String, Persona> personas = new LinkedHashMap<>();

    public DemoData(ObjectMapper om) throws IOException {
        for (String id : IDS) {
            try (InputStream in = new ClassPathResource("demo/" + id + ".json").getInputStream()) {
                JsonNode root = om.readTree(in);
                List<Tx> txs = new ArrayList<>();
                for (JsonNode t : root.path("transactions")) {
                    txs.add(new Tx(t.path("date").asString(), t.path("type").asString(),
                            t.path("category").asString(), t.path("merchant").asString(), t.path("amount").asLong()));
                }
                personas.put(id, new Persona(id, root.path("name").asString(), List.copyOf(txs)));
            }
        }
    }

    public boolean exists(String id) { return personas.containsKey(id); }

    public Persona get(String id) {
        Persona p = personas.get(id);
        if (p == null) throw new IllegalArgumentException("알 수 없는 페르소나: " + id);
        return p;
    }

    public List<Persona> all() { return List.copyOf(personas.values()); }
}
