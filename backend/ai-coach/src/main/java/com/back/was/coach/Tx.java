package com.back.was.coach;

/** 거래 한 건. type 은 "income" | "expense", date 는 "YYYY-MM-DD" */
public record Tx(String date, String type, String category, String merchant, long amount) {
    public String month() { return date.substring(0, 7); }
    public int day() { return Integer.parseInt(date.substring(8)); }
    public boolean isExpense() { return "expense".equals(type); }
    public boolean isIncome() { return "income".equals(type); }
}
