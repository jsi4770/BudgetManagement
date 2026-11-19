import { useState } from 'react';
import { api, setToken } from '../api';
import { Hd, Keypad } from '../components/common';
import { advisePattern, focusLabel, FOCUSES, SAVINGS, signupError, won, yearMonth } from '../format';
import { PigLogo } from '../icons';

const PATTERNS = [
  { label: '야식 배달', text: '퇴근하고 야식 배달을 자주 시켜요' },
  { label: '카페 음료', text: '카페 음료를 거의 매일 사요' },
  { label: '구독·고정비', text: '월세와 구독료 같은 고정지출이 커요' },
  { label: '택시·교통', text: '늦은 시간 택시비가 자주 나가요' },
];

export function Onboarding({ c }) {
  return (
    <div className="screen center" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
      <div className="brand" style={{ paddingBottom: 4 }}><PigLogo size={128} /></div>
      <h1 className="h1" style={{ margin: '12px 0' }}>돈 워리</h1>
      <p className="t2" style={{ fontSize: 15, lineHeight: 1.6 }}>
        기록은 한 줄로 가볍게,<br />소비 습관은 AI 코치와 함께 차근차근.<br />자취 생활, 부담 없이 시작해요.
      </p>
      <button type="button" className="btn" style={{ marginTop: 40 }} onClick={() => c.go('S002')}>시작하기</button>
      <button type="button" className="btn sub" onClick={() => c.go('S003')}>이미 계정이 있어요</button>
      <button type="button" className="btn sub" onClick={() => c.preview()}>서버 없이 둘러보기</button>
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
      {!signup && <div className="brand" style={{ padding: '4px 0 12px' }}><PigLogo size={96} /></div>}
      {signup && (
        <div className="fld">
          <label>이름</label>
          <input className="input" value={username} onChange={(event) => setUsername(event.target.value)} />
        </div>
      )}
      <div className="fld">
        <label>이메일</label>
        <input className="input" value={email} onChange={(event) => setEmail(event.target.value)} />
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
  const [pattern, setPattern] = useState('');
  const [picked, setPicked] = useState([]);
  const [advice, setAdvice] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);

  const composed = [pattern.trim(), ...PATTERNS.filter((item) => picked.includes(item.label)).map((item) => item.text)]
    .filter(Boolean)
    .join('\n');

  const analyze = async () => {
    const local = advisePattern(composed || focusLabel(focus));
    if (local.focus) setFocus(local.focus);
    setAdvice(local.message);
    if (!composed) return;
    setAnalyzing(true);
    try {
      const session = await api.startCoaching({});
      const result = await api.sendMessage(session.coachingId, composed);
      if (result.aiMessage?.content) setAdvice(result.aiMessage.content);
      const name = result.proposal?.category?.name;
      if (name === '배달') setFocus('DELIVERY');
      else if (name === '카페') setFocus('CAFE');
      else if (name === '월세' || name === '교통') setFocus('FIXED_COST');
    } catch {
      /* 코칭 API가 없어도 입력한 패턴으로 고른 목표와 안내를 보여 준다. */
    } finally {
      setAnalyzing(false);
    }
  };

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
        <div className="t2" style={{ margin: '8px 0' }}>요즘 소비 패턴을 적거나 골라 주세요. AI가 줄일 목표를 골라 드려요.</div>
        <textarea className="input" maxLength={300} placeholder="예: 퇴근하면 배달을 자주 시켜요" value={pattern} onChange={(event) => setPattern(event.target.value)} />
        <div className="chips" style={{ marginTop: 12, flexWrap: 'wrap' }}>
          {PATTERNS.map((item) => (
            <button
              type="button"
              key={item.label}
              className={picked.includes(item.label) ? 'on' : ''}
              onClick={() => setPicked((current) => (
                current.includes(item.label) ? current.filter((label) => label !== item.label) : [...current, item.label]
              ))}
            >
              {item.label}
            </button>
          ))}
        </div>
        <button type="button" className="btn sm" disabled={analyzing} onClick={analyze}>{analyzing ? '분석 중…' : 'AI에게 분석받기'}</button>
        {advice && (
          <div className="card" style={{ margin: '12px 0 0', background: 'var(--primary-light)' }}>
            <div className="row" style={{ justifyContent: 'flex-start', gap: 8 }}>
              <PigLogo size={32} />
              <b>AI 안내</b>
            </div>
            <p className="t2" style={{ fontSize: 14, lineHeight: 1.6, margin: '8px 0 0' }}>{advice}</p>
            <div className="t2" style={{ marginTop: 6 }}>선택한 목표: {focusLabel(focus)}</div>
          </div>
        )}
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
