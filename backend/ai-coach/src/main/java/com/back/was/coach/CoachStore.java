package com.back.was.coach;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 생성된 인사이트·목표 저장소 (메모리). 시작 시 원본 프로젝트의 저장 결과(classpath:demo/insights, demo/goals)를 불러온다.
 * 팀 DB 에 붙일 때는 이 클래스만 JPA 저장소로 바꾸면 된다.
 */
@Component
public class CoachStore {

    private final Map<String, JsonNode> insights = new ConcurrentHashMap<>();   // persona -> {report, problems}
    private final Map<String, JsonNode> goals = new ConcurrentHashMap<>();      // persona -> {goal, narrative, problems}

    public CoachStore(DemoData data, ObjectMapper om) {
        for (DemoData.Persona p : data.all()) {
            load(om, "demo/insights/" + p.id() + ".json", insights, p.id());
            load(om, "demo/goals/" + p.id() + ".json", goals, p.id());
        }
    }

    private static void load(ObjectMapper om, String path, Map<String, JsonNode> into, String id) {
        ClassPathResource r = new ClassPathResource(path);
        if (!r.exists()) return;
        try (InputStream in = r.getInputStream()) {
            into.put(id, om.readTree(in));
        } catch (Exception ignored) {
            // 저장본이 깨졌으면 없는 것으로 취급 → 새로 생성
        }
    }

    public JsonNode insight(String persona) { return insights.get(persona); }
    public void putInsight(String persona, JsonNode v) { insights.put(persona, v); }
    public JsonNode goal(String persona) { return goals.get(persona); }
    public void putGoal(String persona, JsonNode v) { goals.put(persona, v); }
    public boolean hasGoal(String persona) { return goals.containsKey(persona); }
}
