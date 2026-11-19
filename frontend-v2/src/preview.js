import { addDays, advisePattern, todayISO, yearMonth } from './format';

const CATEGORIES = [
  { id: 1, name: '식비', type: 'EXPENSE' },
  { id: 2, name: '배달', type: 'EXPENSE' },
  { id: 3, name: '카페', type: 'EXPENSE' },
  { id: 4, name: '교통', type: 'EXPENSE' },
  { id: 5, name: '월세', type: 'EXPENSE' },
  { id: 6, name: '기타', type: 'EXPENSE' },
  { id: 7, name: '급여', type: 'INCOME' },
  { id: 8, name: '용돈', type: 'INCOME' },
];

const AVERAGES = { 식비: 80000, 배달: 40000, 카페: 20000, 교통: 25000, 기타: 15000 };

let enabled = false;
let user = null;
let budget = null;
let transactions = [];
let nudge = null;
let reports = {};
let nextId = 1;

function mondayOf(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  const weekday = date.getUTCDay();
  date.setUTCDate(date.getUTCDate() + (weekday === 0 ? -6 : 1 - weekday));
  return date.toISOString().slice(0, 10);
}

function makeNudge() {
  const end = todayISO();
  return {
    id: 1,
    category: { id: 2, name: '배달' },
    title: '배달 지출이 평소보다 늘었어요',
    analysisStartDate: mondayOf(end),
    analysisEndDate: end,
    spendingAmount: 91200,
    avgAmount: 40000,
    growthRate: 128,
    status: 'UNREAD',
    aiAnalysis: '이번 주 배달을 여러 번 시켰어요. 평소 이맘때보다 많이 쓴 셈이에요.',
    aiRecommendation: '이번 주 배달은 2회로 줄여 보는 건 어떨까요?',
    createdAt: `${end}T09:00:00+09:00`,
    updatedAt: `${end}T09:00:00+09:00`,
  };
}

function categoryByName(name) {
  return CATEGORIES.find((item) => item.name === name);
}

function categoryById(id) {
  return CATEGORIES.find((item) => item.id === Number(id)) || null;
}

function row(name, amount, daysAgo, memo) {
  return {
    id: nextId++,
    categoryId: categoryByName(name).id,
    amount,
    date: addDays(todayISO(), -daysAgo),
    memo,
  };
}

function seed(scenario) {
  nextId = 1;
  const salaryDate = `${yearMonth()}-01`;
  transactions = [
    { id: nextId++, categoryId: 7, amount: 2500000, date: salaryDate, memo: '월급' },
    { id: nextId++, categoryId: 5, amount: 550000, date: salaryDate, memo: '월세' },
    row('식비', 32800, 6, '마트 장보기'),
    row('카페', 5500, 4, '아메리카노'),
    row('교통', 1450, 3, '버스'),
    row('배달', 18500, 2, '야식 치킨'),
    row('식비', 12000, 9, '점심'),
    row('카페', 6100, 11, '라떼'),
    row('교통', 2800, 15, '지하철'),
    row('식비', 24600, 17, '장보기'),
    row('기타', 8900, 22, '생활용품'),
  ];
  if (scenario === 'DELIVERY_SPIKE') {
    transactions.push(
      row('배달', 23000, 1, '점심 배달'),
      row('배달', 27000, 1, '저녁 배달'),
      row('배달', 19800, 0, '야식 배달'),
      row('배달', 21400, 3, '배달 분식'),
    );
    nudge = makeNudge();
  } else {
    transactions.push(row('배달', 16200, 8, '저녁 배달'));
    nudge = null;
  }
  reports = {};
}

export function enablePreview() {
  enabled = true;
  user = { id: 'preview', username: '미리보기', email: 'preview@local', role: 'USER' };
  budget = { monthlyBudget: 1200000, savingsGoal: 200000, spendingFocus: 'DELIVERY' };
  seed('NORMAL');
  nudge = makeNudge();
  return { ...user };
}

export function disablePreview() {
  enabled = false;
}

export function isPreview() {
  return enabled;
}

function toTransaction(item) {
  return {
    id: item.id,
    amount: item.amount,
    transactionDate: item.date,
    memo: item.memo,
    category: categoryById(item.categoryId),
  };
}

function inRange(item, from, to) {
  if (from && item.date < from) return false;
  if (to && item.date > to) return false;
  return true;
}

function listed(params) {
  return transactions
    .filter((item) => inRange(item, params.get('from'), params.get('to')))
    .filter((item) => !params.get('categoryId') || String(item.categoryId) === params.get('categoryId'))
    .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id)
    .map(toTransaction);
}

function monthItems(ym) {
  return transactions.filter((item) => item.date.startsWith(ym));
}

function expenseSum(items, { skipRent = false } = {}) {
  return items.reduce((sum, item) => {
    const category = categoryById(item.categoryId);
    if (category?.type === 'INCOME') return sum;
    if (skipRent && category?.name === '월세') return sum;
    return sum + item.amount;
  }, 0);
}

function incomeSum(items) {
  return items.reduce((sum, item) => (
    categoryById(item.categoryId)?.type === 'INCOME' ? sum + item.amount : sum
  ), 0);
}

function monthly(ym) {
  const items = monthItems(ym);
  const totalExpense = expenseSum(items);
  const totalIncome = incomeSum(items);
  const limit = budget.monthlyBudget;
  return {
    yearMonth: ym,
    budget: limit,
    totalIncome,
    totalExpense,
    balance: totalIncome - totalExpense,
    remainingBudget: limit - totalExpense,
    budgetUsageRate: limit ? Math.round((totalExpense / limit) * 10000) / 100 : 0,
  };
}

function weekly(weeks) {
  const count = Number(weeks) || 4;
  const today = todayISO();
  const thisMonday = mondayOf(today);
  const result = [];
  for (let index = count - 1; index >= 0; index -= 1) {
    const weekStart = addDays(thisMonday, -index * 7);
    const weekEnd = index === 0 ? today : addDays(weekStart, 6);
    const items = transactions.filter((item) => item.date >= weekStart && item.date <= weekEnd);
    result.push({ weekStart, weekEnd, totalExpense: expenseSum(items, { skipRent: true }) });
  }
  return { weeks: result };
}

function comparison(period) {
  const end = todayISO();
  const start = period === 'MONTH' ? `${yearMonth()}-01` : mondayOf(end);
  const items = transactions.filter((item) => item.date >= start && item.date <= end);
  const rows = CATEGORIES.filter((category) => category.type === 'EXPENSE').map((category) => {
    const amount = items
      .filter((item) => item.categoryId === category.id)
      .reduce((sum, item) => sum + item.amount, 0);
    const averageAmount = category.name === '월세' ? null : AVERAGES[category.name] ?? null;
    const growthRate = averageAmount
      ? Math.round(((amount - averageAmount) / averageAmount) * 10000) / 100
      : null;
    const isAnomaly = period !== 'MONTH'
      && category.name !== '월세'
      && averageAmount != null
      && amount - averageAmount >= 30000
      && growthRate >= 30;
    return {
      category: { id: category.id, name: category.name },
      amount,
      averageAmount,
      growthRate,
      isAnomaly,
    };
  });
  return {
    period: period === 'MONTH' ? 'MONTH' : 'WEEK',
    startDate: start,
    endDate: end,
    dataSufficient: true,
    totalAmount: rows.reduce((sum, item) => sum + (item.category.name === '월세' && period !== 'MONTH' ? 0 : item.amount), 0),
    items: rows,
  };
}

function calendar(ym) {
  const totals = new Map();
  monthItems(ym).forEach((item) => {
    const income = categoryById(item.categoryId)?.type === 'INCOME';
    const current = totals.get(item.date) || { totalIncome: 0, totalExpense: 0 };
    if (income) current.totalIncome += item.amount;
    else current.totalExpense += item.amount;
    totals.set(item.date, current);
  });
  return {
    yearMonth: ym,
    days: [...totals.entries()].map(([date, sums]) => ({ date, ...sums })),
  };
}

function challenges(status) {
  const today = todayISO();
  const list = [
    {
      id: 1,
      title: '이번 주 배달 2회 이하',
      category: { id: 2, name: '배달' },
      goalType: 'COUNT',
      goalCount: 2,
      goalAmount: null,
      spendingCount: 3,
      spendingAmount: 18500,
      achievementRate: 150,
      status: 'IN_PROGRESS',
      startDate: addDays(today, -3),
      endDate: addDays(today, 3),
      daysLeft: 3,
    },
    {
      id: 2,
      title: '카페 주 3회 이하',
      category: { id: 3, name: '카페' },
      goalType: 'COUNT',
      goalCount: 3,
      goalAmount: null,
      spendingCount: 4,
      spendingAmount: 11600,
      achievementRate: 133.33,
      status: 'FAILED',
      startDate: addDays(today, -20),
      endDate: addDays(today, -14),
      daysLeft: 0,
    },
  ];
  return status ? list.filter((item) => item.status === status) : list;
}

function guessCategory(text) {
  if (/배달|치킨|야식|배민/.test(text)) return categoryByName('배달');
  if (/카페|커피|아메리카노|라떼/.test(text)) return categoryByName('카페');
  if (/버스|지하철|택시|교통/.test(text)) return categoryByName('교통');
  if (/월급|급여/.test(text)) return categoryByName('급여');
  if (/마트|장보|식비|점심|저녁/.test(text)) return categoryByName('식비');
  return categoryByName('기타');
}

function parseText(text) {
  const amountMatch = String(text).replace(/,/g, '').match(/(\d{3,})/);
  const category = guessCategory(text);
  return {
    amount: amountMatch ? Number(amountMatch[1]) : null,
    transactionDate: todayISO(),
    category,
    memo: String(text).slice(0, 40),
    missingFields: amountMatch ? [] : ['금액'],
  };
}

function saveTransaction(body, id) {
  const current = id ? transactions.find((item) => item.id === Number(id)) : null;
  const next = {
    id: current?.id ?? nextId++,
    categoryId: Number(body.categoryId || current?.categoryId),
    amount: Number(body.amount ?? current?.amount),
    date: body.transactionDate || current?.date || todayISO(),
    memo: body.memo ?? current?.memo ?? '',
  };
  if (current) transactions = transactions.map((item) => (item.id === current.id ? next : item));
  else transactions = [next, ...transactions];
  return toTransaction(next);
}

export function previewRequest(path, { method = 'GET', body } = {}) {
  const url = new URL(path, 'http://preview.local');
  const name = url.pathname;
  const params = url.searchParams;

  if (name === '/auth/logout') {
    disablePreview();
    return null;
  }
  if (name === '/users/me' && method === 'PATCH') {
    user = { ...user, ...body };
    return { ...user };
  }
  if (name === '/users/me') return { ...user };
  if (name.startsWith('/budgets/') && method === 'PUT') {
    budget = {
      monthlyBudget: body.monthlyBudget,
      savingsGoal: body.savingsGoal,
      spendingFocus: body.spendingFocus,
    };
    return { ...budget };
  }
  if (name.startsWith('/budgets/')) return { ...budget };
  if (name === '/categories') {
    const type = params.get('type');
    return CATEGORIES.filter((item) => !type || item.type === type);
  }
  if (name === '/transactions/calendar') return calendar(params.get('yearMonth') || yearMonth());
  if (name === '/transactions/parse') return parseText(body?.text || '');
  if (name === '/transactions' && method === 'POST') {
    const list = Array.isArray(body?.transactions) ? body.transactions : [];
    return list.map((item) => saveTransaction(item));
  }
  const transactionId = name.match(/^\/transactions\/(\d+)$/);
  if (transactionId && method === 'PATCH') return saveTransaction(body, transactionId[1]);
  if (transactionId && method === 'DELETE') {
    transactions = transactions.filter((item) => item.id !== Number(transactionId[1]));
    return null;
  }
  if (name === '/transactions') {
    const items = listed(params);
    const size = Number(params.get('size')) || items.length;
    return { items: items.slice(0, size), page: 0, size, totalCount: items.length, hasNext: items.length > size };
  }
  if (name === '/analysis/monthly') return monthly(params.get('yearMonth') || yearMonth());
  if (name === '/analysis/weekly') return weekly(params.get('weeks'));
  if (name === '/analysis/categories') return comparison(params.get('period'));
  const reportMatch = name.match(/^\/challenges\/(\d+)\/report$/);
  if (reportMatch) return reports[reportMatch[1]] || fallbackReport(Number(reportMatch[1]));
  const completeMatch = name.match(/^\/dev\/challenges\/(\d+)\/complete$/);
  if (completeMatch && method === 'POST') return finishChallenge(Number(completeMatch[1]), body?.scenario ?? null);
  if (name === '/challenges') return challenges(params.get('status'));
  if (name === '/nudges/current') return nudge?.status === 'UNREAD' ? nudge : null;
  const nudgeMatch = name.match(/^\/nudges\/(\d+)$/);
  if (nudgeMatch && method === 'PATCH' && nudge && String(nudge.id) === nudgeMatch[1]) {
    nudge = { ...nudge, status: body?.status || nudge.status, updatedAt: `${todayISO()}T12:00:00+09:00` };
    return nudge;
  }
  if (nudgeMatch) return nudge && String(nudge.id) === nudgeMatch[1] ? nudge : null;
  if (name === '/receipts/ocr') {
    return [
      { amount: 500000, transactionDate: todayISO(), category: categoryByName('용돈'), memo: '부모님 송금', missingFields: [] },
      { amount: 4500, transactionDate: todayISO(), category: categoryByName('카페'), memo: '스타벅스', missingFields: [] },
    ];
  }
  if (name === '/coachings' && method === 'POST') {
    return {
      coachingId: 1,
      status: 'IN_PROGRESS',
      messages: [{ id: 1, role: 'AI', content: '예시 데이터로 보는 중이에요. 줄이고 싶은 소비를 적어 보세요.' }],
      proposal: {
        category: categoryByName('배달'),
        title: '배달 2회 이하',
        goalType: 'COUNT',
        goalCount: 2,
        periodDays: 7,
      },
    };
  }
  if (/^\/coachings\/\d+\/confirm$/.test(name)) {
    return {
      challengeId: 1,
      coaching: { id: 1, status: 'COMPLETED' },
      challengeCreatedAt: `${todayISO()}T20:05:00+09:00`,
    };
  }
  if (/^\/coachings\/\d+\/messages$/.test(name)) {
    const content = body?.content || '';
    return {
      userMessage: { id: nextId++, role: 'USER', content },
      aiMessage: { id: nextId++, role: 'AI', content: advisePattern(content).message },
      proposal: null,
    };
  }
  if (name === '/dev/seed') {
    seed(body?.scenario === 'DELIVERY_SPIKE' ? 'DELIVERY_SPIKE' : 'NORMAL');
    return {
      scenario: body?.scenario || 'NORMAL',
      transactionCount: transactions.length,
      nudgeCreated: nudge?.status === 'UNREAD',
    };
  }
  return null;
}

function fallbackReport(id) {
  const item = challenges().find((challenge) => challenge.id === id);
  if (!item || item.status === 'IN_PROGRESS') return null;
  return {
    challengeId: item.id,
    title: item.title,
    status: item.status,
    goalType: item.goalType,
    goalAmount: item.goalAmount,
    goalCount: item.goalCount,
    spendingAmount: item.spendingAmount,
    difference: (item.goalCount ?? item.goalAmount ?? 0) - (item.spendingCount ?? item.spendingAmount ?? 0),
    achievementRate: item.achievementRate,
    before: { amount: 20000, count: 2 },
    during: { amount: item.spendingAmount, count: item.spendingCount },
    feedback: item.status === 'SUCCESS' ? '목표를 지켰어요.' : '이번엔 목표보다 조금 더 썼어요.',
    createdAt: `${todayISO()}T00:05:00+09:00`,
  };
}

function finishChallenge(id, scenario) {
  const item = challenges().find((challenge) => challenge.id === id) || challenges()[0];
  const success = scenario === 'FAILED' ? false : scenario === 'SUCCESS' || item.spendingCount <= (item.goalCount || 0);
  const report = {
    challengeId: item.id,
    title: item.title,
    status: success ? 'SUCCESS' : 'FAILED',
    goalType: item.goalType,
    goalAmount: item.goalAmount,
    goalCount: item.goalCount,
    spendingAmount: success ? 1 : 4,
    difference: success ? 1 : -1,
    achievementRate: success ? 50 : 200,
    before: { amount: 60000, count: 4 },
    during: { amount: success ? 18000 : 70000, count: success ? 1 : 4 },
    feedback: success
      ? '정말 잘했어요! 횟수를 스스로 조절한 게 눈에 띄어요.'
      : '이번엔 목표보다 조금 더 썼어요. 다음엔 횟수를 먼저 정해 보면 좋아요.',
    createdAt: `${todayISO()}T20:05:00+09:00`,
  };
  reports[String(item.id)] = report;
  item.status = report.status;
  return report;
}
