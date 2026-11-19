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

export function dayHeading(iso) {
  if (!iso) return '';
  const [year, month, day] = iso.split('-').map(Number);
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'UTC',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date(Date.UTC(year, month - 1, day)));
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
    식비: 'shopping',
    배달: 'delivery',
    카페: 'cafe',
    교통: 'transport',
    월세: 'rent',
    급여: 'wallet',
    용돈: 'gift',
    기타: 'category',
  }[name] || 'category';
}

export function weekSpan(start, end) {
  if (!start || !end) return '';
  const [, startMonth, startDay] = start.split('-').map(Number);
  const [, endMonth, endDay] = end.split('-').map(Number);
  return `${startMonth}.${startDay}–${endMonth}.${endDay}`;
}

export function advisePattern(text) {
  const raw = String(text || '').trim();
  let focus = null;
  if (/배달|야식|시켜|배민|쿠팡잇츠/.test(raw)) focus = 'DELIVERY';
  else if (/카페|커피|음료/.test(raw)) focus = 'CAFE';
  else if (/고정|월세|구독|통신|보험/.test(raw)) focus = 'FIXED_COST';
  else if (/택시|교통/.test(raw)) focus = 'FIXED_COST';
  const tips = {
    DELIVERY: '배달이 반복되고 있어요. 이번 주는 횟수를 2회 이하로 정해 두면 금액이 같이 줄어요.',
    CAFE: '카페 지출은 주중 횟수를 줄이는 쪽이 효과적이에요. 주 3회 이하로 시작해 보세요.',
    FIXED_COST: '반복되는 지출을 먼저 목록으로 나눠 보세요. 월세·구독처럼 매달 나가는 항목을 고정지출에 적어두면 생활비가 분명해져요.',
  };
  if (!raw) return { focus: null, message: '소비 패턴을 직접 적거나, 아래 항목을 골라 주세요.' };
  if (!focus) {
    return { focus: null, message: '적은 패턴을 봤어요. 배달, 카페, 고정비 중에서 가장 먼저 줄이고 싶은 목표를 골라 두면 그 항목을 우선 살펴볼게요.' };
  }
  return { focus, message: tips[focus] };
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
  return Math.round(number * 100) / 100;
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
