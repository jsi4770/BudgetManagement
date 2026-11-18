package com.back.was.ai.llm;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Function;

/** 모델이 부를 수 있는 도구 하나. parameters 는 LlmSchema.object(...) (인자가 없으면 null) */
public record LlmTool(String name, String description, Map<String, Object> parameters,
                      Function<JsonNode, Object> handler) {}
