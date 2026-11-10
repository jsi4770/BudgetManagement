const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api/v1';

let accessToken = sessionStorage.getItem('accessToken');
let onAuthLost = () => {};

export function setAuthLostHandler(handler) {
  onAuthLost = handler;
}

export function getToken() {
  return accessToken;
}

export function setToken(token) {
  accessToken = token || null;
  if (accessToken) sessionStorage.setItem('accessToken', accessToken);
  else sessionStorage.removeItem('accessToken');
}

export class ApiError extends Error {
  constructor(message, extra = {}) {
    super(message);
    this.status = extra.status;
    this.code = extra.code;
    this.details = extra.details;
  }
}

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

async function request(path, { method = 'GET', body, auth = true, retry = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response;
  try {
    response = await fetch(BASE + path, {
      method,
      headers,
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError('서버에 연결하지 못했습니다. 백엔드가 켜져 있는지 확인해주세요.');
  }

  if (response.status === 401 && auth && retry && !path.startsWith('/auth/')) {
    const refreshed = await refresh();
    if (refreshed) return request(path, { method, body, auth, retry: false });
    setToken(null);
    onAuthLost();
  }

  if (response.status === 204) return null;

  const json = await response.json().catch(() => null);
  if (!response.ok || json?.success === false) {
    throw new ApiError(json?.error?.message || '요청에 실패했습니다.', {
      status: response.status,
      code: json?.error?.code,
      details: json?.error?.details,
    });
  }
  return json?.data ?? null;
}

async function refresh() {
  try {
    const data = await request('/auth/refresh', { method: 'POST', auth: false, retry: false });
    if (data?.accessToken) {
      setToken(data.accessToken);
      return true;
    }
  } catch {
    setToken(null);
  }
  return false;
}

export const api = {
  signup: (body) => request('/auth/signup', { method: 'POST', body, auth: false }),
  login: (body) => request('/auth/login', { method: 'POST', body, auth: false }),
  logout: () => request('/auth/logout', { method: 'POST', auth: false }),
  me: () => request('/users/me'),
  updateMe: (body) => request('/users/me', { method: 'PATCH', body }),
  budget: (yearMonth) => request(`/budgets/${yearMonth}`),
  saveBudget: (yearMonth, body) => request(`/budgets/${yearMonth}`, { method: 'PUT', body }),
  categories: (type) => request(`/categories${query({ type })}`),
  transactions: (params) => request(`/transactions${query(params)}`),
  calendar: (yearMonth) => request(`/transactions/calendar${query({ yearMonth })}`),
  createTransaction: (body) => request('/transactions', { method: 'POST', body }),
  updateTransaction: (id, body) => request(`/transactions/${id}`, { method: 'PATCH', body }),
  deleteTransaction: (id) => request(`/transactions/${id}`, { method: 'DELETE' }),
  parseTransaction: (text) => request('/transactions/parse', { method: 'POST', body: { text } }),
  monthly: (yearMonth) => request(`/analysis/monthly${query({ yearMonth })}`),
  weekly: (weeks = 4) => request(`/analysis/weekly${query({ weeks })}`),
  categoryComparison: (period) => request(`/analysis/categories${query({ period })}`),
  currentNudge: () => request('/nudges/current'),
  nudge: (id) => request(`/nudges/${id}`),
  nudgeStatus: (id, status) => request(`/nudges/${id}`, { method: 'PATCH', body: { status } }),
  startCoaching: (body) => request('/coachings', { method: 'POST', body: body ?? {} }),
  coaching: (id) => request(`/coachings/${id}`),
  sendMessage: (id, content) => request(`/coachings/${id}/messages`, { method: 'POST', body: { content } }),
  confirmGoal: (id, body) => request(`/coachings/${id}/confirm`, { method: 'POST', body }),
  challenges: (status) => request(`/challenges${query({ status })}`),
  report: (id) => request(`/challenges/${id}/report`),
};
