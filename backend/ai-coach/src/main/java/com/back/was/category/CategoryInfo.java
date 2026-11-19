package com.back.was.category;

/** 카테고리 한 개 (CAT-01 응답과 같은 모양). type: "EXPENSE" | "INCOME" */
public record CategoryInfo(Long id, String name, String type) {

    public boolean isExpense() {
        return "EXPENSE".equals(type);
    }
}
