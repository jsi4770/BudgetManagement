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
let nextId = 1;

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
  } else {
    transactions.push(row('배달', 16200, 8, '저녁 배달'));
  }
}

export function enablePreview() {
  enabled = true;
  user = { id: 'preview', username: '미리보기', email: 'preview@local', role: 'USER' };
  budget = { monthlyBudget: 1200000, savingsGoal: 200000, spendingFocus: 'DELIVERY' };
  seed('NORMAL');
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
    budget: limit,
    totalIncome,
    totalExpense,
    remainingBudget: limit - totalExpense,
    budgetUsageRate: limit ? Math.round((totalExpense / limit) * 100) : 0,
  };
}

function weekly(weeks) {
  const count = Number(weeks) || 4;
  const result = [];
  for (let index = count - 1; index >= 0; index -= 1) {
    const weekEnd = addDays(todayISO(), -index * 7);
    const weekStart = addDays(weekEnd, -6);
    const items = transactions.filter((item) => item.date >= weekStart && item.date <= weekEnd);
    result.push({ weekStart, weekEnd, totalExpense: expenseSum(items, { skipRent: true }) });
  }
  return { weeks: result };
}

function comparison(period) {
  const end = todayISO();
  const start = period === 'WEEK' ? addDays(end, -6) : `${yearMonth()}-01`;
  const items = transactions.filter((item) => item.date >= start && item.date <= end);
  return {
    dataSufficient: true,
    items: CATEGORIES.filter((category) => category.type === 'EXPENSE').map((category) => {
      const amount = items
        .filter((item) => item.categoryId === category.id)
        .reduce((sum, item) => sum + item.amount, 0);
      const averageAmount = category.name === '월세' ? null : AVERAGES[category.name] ?? null;
      const growthRate = averageAmount
        ? Math.round(((amount - averageAmount) / averageAmount) * 100)
        : null;
      return { category, amount, averageAmount, growthRate };
    }),
  };
}

function calendar(ym) {
  const totals = new Map();
  monthItems(ym).forEach((item) => {
    if (categoryById(item.categoryId)?.type === 'INCOME') return;
    totals.set(item.date, (totals.get(item.date) || 0) + item.amount);
  });
  return {
    days: [...totals.entries()].map(([date, totalExpense]) => ({ date, totalExpense })),
  };
}

function challenges(status) {
  const today = todayISO();
  const list = [
    {
      id: 1,
      title: '이번 주 배달 2회 이하',
      goalType: 'COUNT',
      goalCount: 2,
      spendingCount: 3,
      status: 'IN_PROGRESS',
      startDate: addDays(today, -3),
      endDate: addDays(today, 3),
      daysLeft: 3,
    },
    {
      id: 2,
      title: '카페 주 3회 이하',
      goalType: 'COUNT',
      goalCount: 3,
      spendingCount: 4,
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
  if (name === '/transactions' && method === 'POST') return saveTransaction(body);
  const transactionId = name.match(/^\/transactions\/(\d+)$/);
  if (transactionId && method === 'PATCH') return saveTransaction(body, transactionId[1]);
  if (transactionId && method === 'DELETE') {
    transactions = transactions.filter((item) => item.id !== Number(transactionId[1]));
    return null;
  }
  if (name === '/transactions') {
    const items = listed(params);
    const size = Number(params.get('size')) || items.length;
    return { items: items.slice(0, size), totalElements: items.length };
  }
  if (name === '/analysis/monthly') return monthly(params.get('yearMonth') || yearMonth());
  if (name === '/analysis/weekly') return weekly(params.get('weeks'));
  if (name === '/analysis/categories') return comparison(params.get('period'));
  if (name === '/challenges') return challenges(params.get('status'));
  if (name === '/receipts/ocr') {
    return parseText('야식 치킨 18500원');
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
    return { transactionCount: transactions.length };
  }
  return null;
}
