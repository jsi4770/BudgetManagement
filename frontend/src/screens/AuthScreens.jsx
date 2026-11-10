import { useState } from 'react';
import { api, setToken } from '../api';
import { Hd, Keypad } from '../components/common';
import { FOCUSES, SAVINGS, signupError, won, yearMonth } from '../format';

export function Onboarding({ c }) {
  return (
    <div className="screen center" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
      <div className="big">💡</div>
      <h1 className="h1" style={{ margin: '12px 0' }}>넛지 가계부</h1>
      <p className="t2" style={{ fontSize: 15, lineHeight: 1.6 }}>
        기록은 한 줄로 가볍게,<br />소비 습관은 AI 코치와 함께 차근차근.<br />자취 생활, 부담 없이 시작해요.
      </p>
      <button type="button" className="btn" style={{ marginTop: 40 }} onClick={() => c.go('S002')}>시작하기</button>
      <button type="button" className="btn sub" onClick={() => c.go('S003')}>이미 계정이 있어요</button>
    </div>
  );
}

export function Auth({ c, mode }) {
  const signup = mode === 'up';
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (signup) {
      const message = signupError({ username, email, password, confirm });
      if (message) return setError(message);
    } else if (!email.trim() || !password) {
      return setError('이메일과 비밀번호를 입력해주세요.');
    }
    setBusy(true);
    setError('');
    try {
      const data = signup
        ? await api.signup({ username: username.trim(), email: email.trim(), password })
        : await api.login({ email: email.trim(), password });
      setToken(data.accessToken);
      c.setUser(data.user);
      c.go(signup || data.hasBudget === false ? 'S004' : 'S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="screen">
      <Hd title={signup ? '회원가입' : '로그인'} onBack={() => c.go('S001')} />
      {signup && (
        <div className="fld">
          <label>이름</label>
          <input className="input" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="홍길동" />
        </div>
      )}
      <div className="fld">
        <label>이메일</label>
        <input className="input" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="hong@email.com" />
      </div>
      <div className="fld">
        <label>비밀번호</label>
        <input className="input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
      </div>
      {signup && (
        <div className="fld">
          <label>비밀번호 확인</label>
          <input className="input" type="password" value={confirm} onChange={(event) => setConfirm(event.target.value)} />
          <div className="t2" style={{ marginTop: 6 }}>8~64자, 영문·숫자·특수문자를 각각 포함해주세요.</div>
        </div>
      )}
      {error && <div className="err">{error}</div>}
      <button type="button" className="btn" disabled={busy} onClick={submit}>{signup ? '회원가입' : '로그인'}</button>
      <button type="button" className="btn sub" onClick={() => c.go(signup ? 'S003' : 'S002')}>
        {signup ? '로그인으로 이동' : '회원가입으로 이동'}
      </button>
    </div>
  );
}

export function Setup({ c }) {
  const [step, setStep] = useState(0);
  const [budget, setBudget] = useState('');
  const [savings, setSavings] = useState(200000);
  const [focus, setFocus] = useState('DELIVERY');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const save = async () => {
    setBusy(true);
    setError('');
    try {
      await api.saveBudget(yearMonth(), {
        monthlyBudget: Number(budget),
        savingsGoal: savings,
        spendingFocus: focus,
      });
      c.say('설정 완료! 이제 기록해볼까요?');
      c.go('S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  if (step === 0) {
    return (
      <div className="screen">
        <Hd title="초기 설정 1/2" onBack={() => c.go('S002')} />
        <div className="t2">한 달에 쓸 생활비는 얼마예요?</div>
        <div className="amount" style={{ margin: '8px 0' }}>{budget ? Number(budget).toLocaleString('ko-KR') : '0'}원</div>
        <Keypad value={budget} onChange={setBudget} />
        <button type="button" className="btn" disabled={!Number(budget)} onClick={() => setStep(1)}>다음</button>
      </div>
    );
  }

  return (
    <div className="screen">
      <Hd title="초기 설정 2/2" onBack={() => setStep(0)} />
      <div className="card">
        <b>월 저축 목표</b>
        <div className="chips" style={{ marginTop: 12 }}>
          {SAVINGS.map((amount) => (
            <button type="button" key={amount} className={savings === amount ? 'on' : ''} onClick={() => setSavings(amount)}>{won(amount)}</button>
          ))}
        </div>
      </div>
      <div className="card">
        <b>소비 관리 목표</b>
        <div className="chips" style={{ marginTop: 12 }}>
          {FOCUSES.map((item) => (
            <button type="button" key={item.id} className={focus === item.id ? 'on' : ''} onClick={() => setFocus(item.id)}>{item.label}</button>
          ))}
        </div>
      </div>
      {error && <div className="err">{error}</div>}
      <button type="button" className="btn" disabled={busy} onClick={save}>완료하고 시작</button>
    </div>
  );
}
