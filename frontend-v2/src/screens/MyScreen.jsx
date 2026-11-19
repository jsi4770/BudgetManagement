import { useEffect, useState } from 'react';
import { api, setToken } from '../api';
import { iconFor, FOCUSES, percent, SAVINGS, won, yearMonth } from '../format';
import { Icon } from '../icons';
import { categoryLabel, usePrefs } from '../prefs';

async function loadKpi() {
  try {
    const data = await api.kpi();
    if (data) return data;
  } catch {
    // 일반 계정은 관리자 API가 막혀 있다. 아래는 내 종료 챌린지로 같은 지표를 만든다.
  }
  const [success, failed] = await Promise.all([
    api.challenges('SUCCESS'),
    api.challenges('FAILED'),
  ]);
  const successCount = (success || []).length;
  const ended = successCount + (failed || []).length;
  const achievementRate = ended === 0 ? 0 : Math.round((successCount * 10000) / ended) / 100;
  return {
    endedChallengeCount: ended,
    successChallengeCount: successCount,
    achievementRate,
    target: 70,
    isAchieved: achievementRate >= 70,
  };
}

function KpiDetail({ kpi }) {
  const ended = Number(kpi.endedChallengeCount) || 0;
  const success = Number(kpi.successChallengeCount) || 0;
  const failed = Math.max(0, ended - success);
  const hasRate = kpi.achievementRate != null && Number.isFinite(Number(kpi.achievementRate));
  const rate = hasRate ? percent(kpi.achievementRate) : 0;
  const target = percent(kpi.target ?? 70);
  const gap = rate - target;
  const reached = Boolean(kpi.isAchieved);

  return (
    <>
      <div className="amount" style={{ color: reached ? 'var(--success)' : 'var(--text)' }}>
        {hasRate ? `${rate}%` : '—'}
      </div>
      <div className="progress">
        <div style={{ width: `${Math.min(100, rate)}%`, background: reached ? 'var(--success)' : 'var(--primary)' }} />
      </div>
      <div className="row t2">
        <span>성공 기준 {target}%</span>
        <span>{ended === 0 ? '종료된 챌린지 없음' : reached ? '기준 달성' : '기준 미달'}</span>
      </div>
      <div className="tx"><div style={{ flex: 1 }}>종료된 챌린지</div><b>{ended}건</b></div>
      <div className="tx"><div style={{ flex: 1 }}>목표 달성</div><b style={{ color: 'var(--success)' }}>{success}건</b></div>
      <div className="tx"><div style={{ flex: 1 }}>미달성</div><b>{failed}건</b></div>
      <div className="tx"><div style={{ flex: 1 }}>목표와의 차이</div><b>{hasRate ? `${gap > 0 ? '+' : ''}${gap}%p` : '—'}</b></div>
      <p className="t2" style={{ margin: '12px 0 0', lineHeight: 1.6 }}>
        달성률은 기간이 끝난 챌린지 중 목표를 달성한 비율입니다. 성공 기준은 {target}%입니다.
      </p>
    </>
  );
}

export function MyPage({ c }) {
  const { prefs, update } = usePrefs();
  const [username, setUsername] = useState(c.user?.username || '');
  const [budget, setBudget] = useState('');
  const [savings, setSavings] = useState(200000);
  const [focus, setFocus] = useState('DELIVERY');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [categories, setCategories] = useState([]);
  const [spent, setSpent] = useState({});
  const [editingId, setEditingId] = useState(null);
  const [alias, setAlias] = useState('');
  const [cap, setCap] = useState('');
  const [fixedDraft, setFixedDraft] = useState({ name: '', amount: '', day: '' });
  const [fixedEdit, setFixedEdit] = useState(null);
  const [kpi, setKpi] = useState(null);
  const [kpiError, setKpiError] = useState('');

  useEffect(() => {
    let cancel = false;
    api.budget(yearMonth()).then((data) => {
      if (cancel || !data) return;
      setBudget(String(data.monthlyBudget));
      setSavings(data.savingsGoal);
      if (data.spendingFocus) setFocus(data.spendingFocus);
    }).catch(() => {});
    api.categories('EXPENSE').then((list) => { if (!cancel) setCategories(list || []); }).catch(() => {});
    api.categoryComparison('MONTH').then((data) => {
      if (cancel) return;
      const amounts = {};
      (data?.items || []).forEach((item) => { amounts[item.category.id] = item.amount; });
      setSpent(amounts);
    }).catch(() => {});
    loadKpi().then((data) => { if (!cancel) setKpi(data); }).catch((err) => { if (!cancel) setKpiError(err.message); });
    return () => { cancel = true; };
  }, []);

  const resetData = async (scenario) => {
    setBusy(true);
    setError('');
    try {
      const data = await api.seed(scenario);
      setKpi(await loadKpi());
      c.say(scenario === 'NORMAL'
        ? `정상 소비 상황으로 초기화했어요${data?.transactionCount != null ? ` · ${data.transactionCount}건` : ''}`
        : `배달비 증가 상황으로 초기화했어요${data?.transactionCount != null ? ` · ${data.transactionCount}건` : ''}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const startCategory = (category) => {
    setEditingId(category.id);
    setAlias(categoryLabel(category, prefs.aliases));
    setCap(prefs.caps?.[category.id] || prefs.caps?.[String(category.id)] ? String(prefs.caps[category.id] ?? prefs.caps[String(category.id)]) : '');
  };

  const saveCategory = (category) => {
    const nextName = alias.trim();
    const nextCap = Number(cap);
    update((current) => {
      const aliases = { ...current.aliases };
      const caps = { ...current.caps };
      if (!nextName || nextName === category.name) delete aliases[category.id];
      else aliases[category.id] = nextName;
      if (!nextCap) delete caps[category.id];
      else caps[category.id] = nextCap;
      return { ...current, aliases, caps };
    });
    setEditingId(null);
  };

  const addFixed = () => {
    if (!fixedDraft.name.trim() || !Number(fixedDraft.amount)) return;
    const day = Number(fixedDraft.day);
    update((current) => ({
      ...current,
      fixed: [...(current.fixed || []), {
        id: Date.now(),
        name: fixedDraft.name.trim(),
        amount: Number(fixedDraft.amount),
        day: day >= 1 && day <= 31 ? day : null,
      }],
    }));
    setFixedDraft({ name: '', amount: '', day: '' });
  };

  const saveFixed = () => {
    if (!fixedEdit?.name.trim() || !Number(fixedEdit.amount)) return;
    const day = Number(fixedEdit.day);
    update((current) => ({
      ...current,
      fixed: (current.fixed || []).map((item) => (item.id === fixedEdit.id ? {
        ...item,
        name: fixedEdit.name.trim(),
        amount: Number(fixedEdit.amount),
        day: day >= 1 && day <= 31 ? day : null,
      } : item)),
    }));
    setFixedEdit(null);
  };

  const removeFixed = (id) => {
    update((current) => ({ ...current, fixed: (current.fixed || []).filter((item) => item.id !== id) }));
  };

  const fixedTotal = (prefs.fixed || []).reduce((sum, item) => sum + (Number(item.amount) || 0), 0);

  const save = async () => {
    if (!username.trim()) return setError('이름을 입력해주세요.');
    if (!Number(budget)) return setError('월 생활비를 입력해주세요.');
    setBusy(true);
    setError('');
    try {
      const me = await api.updateMe({ username: username.trim() });
      await api.saveBudget(yearMonth(), {
        monthlyBudget: Number(budget),
        savingsGoal: savings,
        spendingFocus: focus,
      });
      c.setUser(me);
      c.say('저장했어요');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    try { await api.logout(); } catch { /* 쿠키가 없어도 화면의 로그인 상태는 지운다. */ }
    setToken(null);
    c.setUser(null);
    c.setCoachingId(null);
    c.setNudgeId(null);
    c.go('S003');
  };

  return (
    <div className="screen">
      <h1 className="h1" style={{ marginBottom: 16 }}>마이페이지</h1>
      <div className="card">
        <h2 className="h2" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="shield-check" size={18} /> KPI 달성률
        </h2>
        {kpiError && <div className="err">{kpiError}</div>}
        {!kpi && !kpiError && <div className="t2" style={{ marginTop: 8 }}>불러오는 중…</div>}
        {kpi && <KpiDetail kpi={kpi} />}
      </div>
      <div className="card row">
        <div className="row" style={{ justifyContent: 'flex-start' }}>
          <div className="ico"><Icon name="user-avatar" /></div>
          <div><b>{c.user?.username}</b><div className="t2">{c.user?.email}</div></div>
        </div>
      </div>
      <div className="card">
        <div className="row">
          <h2 className="h2">내 정보</h2>
          <button type="button" className="t2" onClick={() => c.go('S018')}>수정</button>
        </div>
        <div className="row" style={{ marginTop: 12 }}><span className="t2">사는 지역</span><b>{c.user?.regionName || '미설정'}</b></div>
        <div className="row" style={{ marginTop: 8 }}><span className="t2">만 나이</span><b>{c.user?.age ? `${c.user.age}세` : '미설정'}</b></div>
        <div className="row" style={{ marginTop: 8 }}><span className="t2">성별</span><b>{{ FEMALE: '여성', MALE: '남성', NONE: '선택 안 함' }[c.user?.gender] || '미설정'}</b></div>
      </div>
      <button type="button" className="card row" style={{ width: '100%', textAlign: 'left' }} onClick={() => c.go('S017')}>
        <div className="row" style={{ justifyContent: 'flex-start' }}>
          <div className="ico"><Icon name="location" /></div>
          <div>
            <b>우리 동네 가맹점</b>
            <div className="t2">{c.user?.regionName ? `${c.user.regionName} 지역화폐 사용처 보기` : '지역화폐 사용처 보기'}</div>
          </div>
        </div>
        <span className="t2">›</span>
      </button>
      <div className="card">
        <div className="fld">
          <label>이름</label>
          <input className="input" value={username} maxLength={50} onChange={(event) => setUsername(event.target.value)} />
        </div>
        <div className="fld">
          <label>월 생활비 예산(원)</label>
          <input className="input" inputMode="numeric" value={budget} onChange={(event) => setBudget(event.target.value.replace(/\D/g, ''))} />
        </div>
        <div className="fld">
          <label>저축 목표</label>
          <div className="chips">
            {SAVINGS.map((amount) => (
              <button type="button" key={amount} className={savings === amount ? 'on' : ''} onClick={() => setSavings(amount)}>{won(amount)}</button>
            ))}
          </div>
        </div>
        <div className="fld">
          <label>소비 관리 목표</label>
          <div className="chips">
            {FOCUSES.map((item) => (
              <button type="button" key={item.id} className={focus === item.id ? 'on' : ''} onClick={() => setFocus(item.id)}>{item.label}</button>
            ))}
          </div>
        </div>
        {error && <div className="err">{error}</div>}
        <button type="button" className="btn" disabled={busy} onClick={save}>저장</button>
      </div>

      <div className="card">
        <div className="row">
          <h2 className="h2" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Icon name="category" size={18} /> 소비 카테고리</h2>
        </div>
        <div className="t2" style={{ margin: '8px 0 12px' }}>이번 달 지출을 한눈에 보고, 이름과 한도를 수정할 수 있어요.</div>
        {categories.map((category) => {
          const limit = Number(prefs.caps?.[category.id] ?? prefs.caps?.[String(category.id)]) || 0;
          const amount = spent[category.id] || 0;
          return (
            <div
              key={category.id}
              className="tx"
              style={{ cursor: editingId === category.id ? 'default' : 'pointer' }}
              onClick={() => { if (editingId !== category.id) c.openCategory(category, 'S015'); }}
            >
              <div className="ico"><Icon name={iconFor(category.name)} size={18} /></div>
              <div style={{ flex: 1 }}>
                {editingId === category.id ? (
                  <>
                    <input className="input" value={alias} maxLength={20} onChange={(event) => setAlias(event.target.value)} />
                    <input className="input" style={{ marginTop: 8 }} inputMode="numeric" placeholder="월 한도(원)" value={cap} onChange={(event) => setCap(event.target.value.replace(/\D/g, ''))} />
                    <div className="row" style={{ marginTop: 8 }}>
                      <button type="button" className="btn sm" onClick={() => saveCategory(category)}>저장</button>
                      <button type="button" className="btn sm sub" onClick={() => setEditingId(null)}>취소</button>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="row">
                      <b>{categoryLabel(category, prefs.aliases)}</b>
                      <button type="button" className="t2" onClick={(event) => { event.stopPropagation(); startCategory(category); }}>수정</button>
                    </div>
                    <div className="t2">이번 달 {won(amount)}{limit ? ` · 한도 ${won(limit)}` : ''}</div>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <div className="card">
        <div className="row">
          <h2 className="h2" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Icon name="bank-card" size={18} /> 고정지출</h2>
          <b>{won(fixedTotal)}</b>
        </div>
        {(prefs.fixed || []).length === 0 && <div className="t2" style={{ marginTop: 12 }}>매달 나가는 월세, 구독료를 적어 두세요.</div>}
        {(prefs.fixed || []).map((item) => (
          <div key={item.id} className="tx">
            <div className="ico"><Icon name="wallet" size={18} /></div>
            <div style={{ flex: 1 }}>
              {fixedEdit?.id === item.id ? (
                <>
                  <input className="input" value={fixedEdit.name} onChange={(event) => setFixedEdit({ ...fixedEdit, name: event.target.value })} />
                  <div className="row" style={{ marginTop: 8 }}>
                    <input className="input" inputMode="numeric" value={fixedEdit.amount} onChange={(event) => setFixedEdit({ ...fixedEdit, amount: event.target.value.replace(/\D/g, '') })} />
                    <input className="input" inputMode="numeric" placeholder="매월 일" value={fixedEdit.day || ''} onChange={(event) => setFixedEdit({ ...fixedEdit, day: event.target.value.replace(/\D/g, '').slice(0, 2) })} />
                  </div>
                  <div className="row" style={{ marginTop: 8 }}>
                    <button type="button" className="btn sm" onClick={saveFixed}>저장</button>
                    <button type="button" className="btn sm sub" onClick={() => setFixedEdit(null)}>취소</button>
                  </div>
                </>
              ) : (
                <div className="row">
                  <div>
                    <b>{item.name}</b>
                    <div className="t2">{won(item.amount)}{item.day ? ` · 매월 ${item.day}일` : ''}</div>
                  </div>
                  <div className="t2">
                    <button type="button" onClick={() => setFixedEdit({ ...item, amount: String(item.amount), day: item.day ? String(item.day) : '' })}>수정</button>
                    {' · '}
                    <button type="button" onClick={() => removeFixed(item.id)}>삭제</button>
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}
        <div className="fld" style={{ marginTop: 12 }}>
          <label>항목</label>
          <input className="input" placeholder="월세, 구독" value={fixedDraft.name} onChange={(event) => setFixedDraft({ ...fixedDraft, name: event.target.value })} />
        </div>
        <div className="row">
          <input className="input" inputMode="numeric" placeholder="금액" value={fixedDraft.amount} onChange={(event) => setFixedDraft({ ...fixedDraft, amount: event.target.value.replace(/\D/g, '') })} />
          <input className="input" inputMode="numeric" placeholder="매월 일" value={fixedDraft.day} onChange={(event) => setFixedDraft({ ...fixedDraft, day: event.target.value.replace(/\D/g, '').slice(0, 2) })} />
        </div>
        <button type="button" className="btn sm" onClick={addFixed}>고정지출 추가</button>
      </div>

      <div className="card">
        <h2 className="h2">테스트 데이터 초기화</h2>
        <div className="t2" style={{ margin: '8px 0 4px' }}>내 거래와 예산, 챌린지를 지우고 시나리오 데이터로 다시 채워요.</div>
        {error && <div className="err">{error}</div>}
        <button type="button" className="btn sm" disabled={busy} onClick={() => resetData('NORMAL')}>정상 소비 상황으로 초기화</button>
        <button type="button" className="btn sm sub" disabled={busy} onClick={() => resetData('DELIVERY_SPIKE')}>배달비 증가 상황으로 초기화</button>
      </div>
      <button type="button" className="btn sub" onClick={logout}>로그아웃</button>
      <div className="pad" />
    </div>
  );
}
