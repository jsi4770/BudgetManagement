#!/usr/bin/env python3
"""
지자체가 공개한 가맹점 엑셀·PDF → 앱이 읽는 CSV(src/main/resources/merchants/*.csv) 변환.

  입력: 아산페이 일반상품권 사용처(.xlsx), 천안사랑카드 가맹점(.xlsx), 천안 등록취소 명단(.pdf)
  실행: pip install openpyxl pypdf && python3 convert.py <아산.xlsx> <천안.xlsx> <천안취소.pdf>

출력 컬럼: name,address,emd,phone,group   (group = MerchantGroup 이름)
"""
import csv, os, re, sys
import openpyxl, pypdf

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "merchants")

ASAN_GROUP = {
    "음식점": "FOOD", "카페/베이커리": "CAFE", "편의점/슈퍼/마트": "GROCERY", "시장/거리": "GROCERY",
    "주유소": "TRANSPORT", "자동차/자전거": "TRANSPORT",
    "의류/잡화/안경": "SHOPPING", "주방/가정/인테리어": "SHOPPING", "가전/통신": "SHOPPING",
    "의료/보건": "HEALTH", "학원/교육": "EDU", "평생학습기관": "EDU",
    "스포츠/헬스": "LEISURE", "도서/문화/공연/오락": "LEISURE", "숙박업": "LEISURE",
    "미용/뷰티/위생": "LIFE", "산모/육아": "LIFE", "부동산": "ETC", "기타": "ETC",
}
CHEONAN_GROUP = {
    "한식": "FOOD", "일반한식": "FOOD", "일반음식점 기타": "FOOD", "중식": "FOOD", "일식/생선회집": "FOOD",
    "양식": "FOOD", "패스트푸드점": "FOOD", "일반주점": "FOOD",
    "휴게음식점": "CAFE", "제과점/아이스크림점": "CAFE",
    "편의점": "GROCERY", "슈퍼마켓": "GROCERY", "농 수 축산물점": "GROCERY", "정육점": "GROCERY",
    "기타 식품": "GROCERY", "미곡상": "GROCERY", "농 수 축협직판장": "GROCERY", "소비조합": "GROCERY",
    "일반잡화판매점": "SHOPPING", "미분류 기타1": "ETC",
}

def norm(s):  # 비교용: 공백·괄호·'㈜' 등 제거
    return re.sub(r"[\s()（）㈜\-_.,]", "", (s or "").replace("(주)", "").replace("주식회사", "")).lower()

def road_key(addr):  # '서부대로 737' 같은 도로명+번호만 — 상세주소·동 표기 차이를 무시하고 비교
    m = re.search(r"([가-힣0-9]+(?:로|길)\s?\d+(?:-\d+)?)", addr or "")
    return m.group(1).replace(" ", "") if m else norm(addr)

def clean_addr(a):
    a = re.sub(r"\s+", " ", (a or "").strip())
    a = a.replace("충남 천산시", "충남 천안시")
    if a.startswith("충남 ") and "천안시" not in a and ("서북구" in a or "동남구" in a):
        a = a.replace("충남 ", "충남 천안시 ", 1)
    return a

def emd_of(addr):
    m = re.search(r"\(([가-힣0-9]+동)[,)]", addr) or re.search(r"([가-힣]+[읍면])\s", addr) or re.search(r"([가-힣0-9]+동)\s*$", addr)
    return m.group(1) if m else ""

def write(name, rows):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["name", "address", "emd", "phone", "group"]); w.writerows(rows)

def asan(path):
    ws = openpyxl.load_workbook(path, read_only=True).worksheets[0]
    seen, rows, skipped = set(), [], 0
    for r in ws.iter_rows(min_row=4, values_only=True):
        if not r[1]: continue
        name, phone, addr, emd, cat = (r[1] or "").strip(), (r[2] or "").strip(), clean_addr(r[3]), (r[4] or "").strip(), (r[5] or "").strip()
        if "아산시" not in addr: skipped += 1; continue          # 타 지역 주소는 제외
        key = (norm(name), norm(addr))
        if key in seen: continue
        seen.add(key)
        rows.append([name, addr, emd or emd_of(addr), phone, ASAN_GROUP.get(cat, "ETC")])
    print(f"아산: {len(rows)}건 (타지역 주소 제외 {skipped})")
    return rows

def cheonan(path, pdf):
    cancelled = set()
    for line in pypdf.PdfReader(pdf).pages[0].extract_text().splitlines():
        m = re.match(r"^\d+\s+(.+?)\s{1,}(천안시 .+?)\s+\S+$", line.strip())
        if m: cancelled.add((norm(m.group(1)), road_key(m.group(2))))
    ws = openpyxl.load_workbook(path, read_only=True).worksheets[0]
    seen, rows, hit = set(), [], 0
    for r in ws.iter_rows(min_row=3, values_only=True):
        if not r[2]: continue
        name, cat, addr = r[2].strip(), (r[3] or "").strip(), clean_addr(r[4])
        key = (norm(name), norm(addr))
        if key in seen: continue
        seen.add(key)
        if (norm(name), road_key(addr)) in cancelled: hit += 1; continue
        rows.append([name, addr, emd_of(addr), "", CHEONAN_GROUP.get(cat, "ETC")])
    print(f"천안: {len(rows)}건 (등록취소 명단 {len(cancelled)}곳 중 {hit}곳 제외)")
    return rows

if __name__ == "__main__":
    a, c, p = sys.argv[1:4]
    write("asan.csv", asan(a)); write("cheonan.csv", cheonan(c, p))
