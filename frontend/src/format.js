export function won(value) {
  const amount = Number(value) || 0;
  const sign = amount < 0 ? '-' : '';
  return `${sign}${Math.abs(amount).toLocaleString('ko-KR')}원`;
}

export function yearMonth(date = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
  }).format(date).slice(0, 7);
}

export function todayISO(date = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

export function addDays(iso, days) {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function monthEnd(ym) {
  const [year, month] = ym.split('-').map(Number);
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return `${ym}-${String(last).padStart(2, '0')}`;
}

export function shiftMonth(ym, delta) {
  const [year, month] = ym.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1 + delta, 1));
  const nextMonth = String(date.getUTCMonth() + 1).padStart(2, '0');
  return `${date.getUTCFullYear()}-${nextMonth}`;
}

export function monthLabel(ym) {
  const [year, month] = ym.split('-');
  return `${year}년 ${Number(month)}월`;
}

export function greetingDate() {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date());
}

export function shortDate(iso) {
  if (!iso) return '';
  const today = todayISO();
  if (iso === today) return '오늘';
  if (iso === addDays(today, -1)) return '어제';
  const [, month, day] = iso.split('-');
  return `${Number(month)}.${Number(day)}`;
}

export function rangeLabel(start, end) {
  if (!start || !end) return '';
  return `${shortDate(start)} ~ ${shortDate(end)}`;
}

export function iconFor(name) {
  return {
    식비: '🍚',
    배달: '🛵',
    카페: '☕',
    교통: '🚌',
    월세: '🏠',
    급여: '💰',
    용돈: '🎁',
    기타: '📌',
  }[name] || '📌';
}

export const FOCUSES = [
  { id: 'DELIVERY', label: '배달비 줄이기' },
  { id: 'CAFE', label: '카페 줄이기' },
  { id: 'FIXED_COST', label: '고정비 점검' },
];

export const SAVINGS = [100000, 200000, 300000];

export function focusLabel(id) {
  return FOCUSES.find((item) => item.id === id)?.label || '목표 없음';
}

export function percent(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.round(number);
}

export function isIncome(category) {
  return category?.type === 'INCOME';
}

export function signedAmount(transaction) {
  const amount = transaction.amount || 0;
  return isIncome(transaction.category) ? amount : -amount;
}

export function calendarCells(ym) {
  const [year, month] = ym.split('-').map(Number);
  const first = new Date(Date.UTC(year, month - 1, 1));
  const count = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const cells = Array(first.getUTCDay()).fill(null);
  for (let day = 1; day <= count; day += 1) {
    cells.push(`${ym}-${String(day).padStart(2, '0')}`);
  }
  return cells;
}

export function goalTitle(categoryName, goalType, amount, count) {
  if (goalType === 'COUNT') return `${categoryName} ${Number(count).toLocaleString('ko-KR')}회 이하`;
  return `${categoryName} ${Number(amount).toLocaleString('ko-KR')}원 이내`;
}

const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$/;

export function signupError({ username, email, password, confirm }) {
  if (!username.trim()) return '이름을 입력해주세요.';
  if (username.trim().length > 50) return '이름은 50자까지 입력할 수 있어요.';
  if (!/^\S+@\S+\.\S+$/.test(email)) return '이메일 형식을 확인해주세요.';
  if (!PASSWORD.test(password)) return '비밀번호는 8~64자이며 영문, 숫자, 특수문자를 각각 포함해야 해요.';
  if (password !== confirm) return '비밀번호가 서로 달라요.';
  return '';
}
