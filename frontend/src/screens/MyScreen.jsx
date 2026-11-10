import { useEffect, useState } from 'react';
import { api, setToken } from '../api';
import { FOCUSES, SAVINGS, won, yearMonth } from '../format';

export function MyPage({ c }) {
  const [username, setUsername] = useState(c.user?.username || '');
  const [budget, setBudget] = useState('');
  const [savings, setSavings] = useState(200000);
  const [focus, setFocus] = useState('DELIVERY');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancel = false;
    api.budget(yearMonth()).then((data) => {
      if (cancel || !data) return;
      setBudget(String(data.monthlyBudget));
      setSavings(data.savingsGoal);
      if (data.spendingFocus) setFocus(data.spendingFocus);
    }).catch(() => {});
    return () => { cancel = true; };
  }, []);

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
      <div className="card row">
        <div className="row" style={{ justifyContent: 'flex-start' }}>
          <div className="ico">🙂</div>
          <div><b>{c.user?.username}</b><div className="t2">{c.user?.email}</div></div>
        </div>
      </div>
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
      <button type="button" className="btn sub" onClick={logout}>로그아웃</button>
      <div className="pad" />
    </div>
  );
}
