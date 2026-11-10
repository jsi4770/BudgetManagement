import { useEffect, useState } from 'react';
import { api, getToken, setAuthLostHandler, setToken } from './api';
import { todayISO } from './format';
import { Auth, Onboarding, Setup } from './screens/AuthScreens';
import { Challenge, Coaching, Report } from './screens/CoachingScreens';
import { Home } from './screens/HomeScreen';
import { Analysis, NudgeDetail } from './screens/InsightScreens';
import { AiInput, Ledger, ManualEntry, ReceiptOcr } from './screens/LedgerScreens';
import { MyPage } from './screens/MyScreen';

const NAV = [
  ['S005', '🏠', '홈'],
  ['S006', '📒', '가계부'],
  ['S010', '📊', '분석'],
  ['S013', '🎯', '챌린지'],
  ['S015', '👤', '마이'],
];

export default function App() {
  const [screen, setScreen] = useState('S001');
  const [user, setUser] = useState(null);
  const [draft, setDraftState] = useState(null);
  const [nudgeId, setNudgeId] = useState(null);
  const [coachingId, setCoachingId] = useState(null);
  const [challengeId, setChallengeId] = useState(null);
  const [toast, setToast] = useState('');
  const [sheet, setSheet] = useState(false);
  const [ready, setReady] = useState(false);

  const say = (message) => {
    setToast(message);
    setTimeout(() => setToast(''), 2200);
  };
  const go = (next) => {
    setSheet(false);
    setScreen(next);
  };
  const setDraft = (next) => {
    setDraftState((current) => (typeof next === 'function' ? next(current) : next));
  };

  useEffect(() => {
    setAuthLostHandler(() => {
      setUser(null);
      setScreen('S003');
      say('로그인이 만료됐어요. 다시 로그인해주세요.');
    });
    let cancel = false;
    (async () => {
      if (!getToken()) {
        setReady(true);
        return;
      }
      try {
        const me = await api.me();
        if (cancel) return;
        setUser(me);
        setScreen('S005');
      } catch {
        setToken(null);
      } finally {
        if (!cancel) setReady(true);
      }
    })();
    return () => { cancel = true; };
  }, []);

  const c = {
    user, setUser, draft, setDraft, nudgeId, setNudgeId,
    coachingId, setCoachingId, challengeId, setChallengeId, go, say,
  };

  const page = {
    S001: <Onboarding c={c} />,
    S002: <Auth c={c} mode="up" />,
    S003: <Auth c={c} mode="in" />,
    S004: <Setup c={c} />,
    S005: <Home c={c} />,
    S006: <Ledger c={c} />,
    S007: <ManualEntry c={c} />,
    S008: <AiInput c={c} />,
    S009: <ReceiptOcr c={c} />,
    S010: <Analysis c={c} />,
    S011: <NudgeDetail c={c} />,
    S012: <Coaching c={c} />,
    S013: <Challenge c={c} />,
    S014: <Report c={c} />,
    S015: <MyPage c={c} />,
  }[screen];

  if (!ready) {
    return <div className="phone"><div className="screen center"><div className="t2">불러오는 중…</div></div></div>;
  }

  return (
    <div className="phone">
      {page}
      {screen === 'S005' && <button type="button" className="fab" onClick={() => setSheet(true)}>＋ 지출 기록</button>}
      {NAV.some(([id]) => id === screen) && (
        <nav className="nav">
          {NAV.map(([id, icon, label]) => (
            <button type="button" key={id} className={screen === id ? 'on' : ''} onClick={() => go(id)}>
              <span>{icon}</span>{label}
            </button>
          ))}
        </nav>
      )}
      {sheet && (
        <div className="sheet-bg" onClick={() => setSheet(false)}>
          <div className="sheet" onClick={(event) => event.stopPropagation()}>
            <h2 className="h2">어떻게 기록할까요?</h2>
            {[
              ['✍️', '한 줄 입력', '말하듯 적으면 AI가 정리해요', 'S008'],
              ['📅', '캘린더 입력', '날짜를 골라 입력해요', 'S006'],
              ['🧾', '영수증 촬영', 'OCR 연동 전이에요', 'S009'],
              ['🔢', '직접 입력', '금액과 카테고리를 입력해요', 'S007'],
            ].map(([icon, title, desc, target]) => (
              <button
                type="button"
                key={title}
                className="opt"
                onClick={() => {
                  if (target === 'S007') setDraft({ type: 'EXPENSE', amount: '', date: todayISO(), memo: '' });
                  go(target);
                }}
              >
                <span className="ico">{icon}</span>
                <div><b>{title}</b><div className="t2">{desc}</div></div>
              </button>
            ))}
          </div>
        </div>
      )}
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
