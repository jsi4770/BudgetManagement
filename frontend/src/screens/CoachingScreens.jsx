import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { Hd, Loading, Usage } from '../components/common';
import { addDays, goalTitle, percent, rangeLabel, todayISO, won } from '../format';

const QUICK = ['퇴근 후 배달이 잦아요', '야식을 자주 시켜요', '잘 모르겠어요'];

export function Coaching({ c }) {
  const [session, setSession] = useState(null);
  const [proposal, setProposal] = useState(null);
  const [amount, setAmount] = useState(10000);
  const [count, setCount] = useState(2);
  const [days, setDays] = useState(7);
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const started = useRef(false);

  const applyProposal = (next) => {
    if (!next) return;
    setProposal(next);
    if (next.goalAmount) setAmount(next.goalAmount);
    if (next.goalCount) setCount(next.goalCount);
    if (next.periodDays && [7, 14, 30].includes(next.periodDays)) setDays(next.periodDays);
  };

  useEffect(() => {
    if (started.current) return undefined;
    started.current = true;
    let cancel = false;
    (async () => {
      try {
        const data = c.coachingId
          ? await api.coaching(c.coachingId)
          : await api.startCoaching(c.nudgeId ? { nudgeId: c.nudgeId } : {});
        if (cancel) return;
        c.setCoachingId(data.coachingId);
        setSession(data);
        applyProposal(data.proposal);
      } catch (err) {
        if (!cancel) setError(err.message);
      }
    })();
    return () => { cancel = true; };
  }, []);

  const send = async (content) => {
    const message = content.trim();
    if (!message || !session || busy) return;
    setBusy(true);
    setError('');
    try {
      const result = await api.sendMessage(session.coachingId, message);
      setSession((current) => ({
        ...current,
        messages: [...current.messages, result.userMessage, result.aiMessage],
      }));
      if (result.proposal) applyProposal(result.proposal);
      setText('');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    if (!proposal) return;
    const startDate = todayISO();
    setBusy(true);
    setError('');
    try {
      const result = await api.confirmGoal(session.coachingId, {
        categoryId: proposal.category.id,
        title: goalTitle(proposal.category.name, proposal.goalType, amount, count),
        goalType: proposal.goalType,
        goalAmount: proposal.goalType === 'AMOUNT' ? amount : null,
        goalCount: proposal.goalType === 'COUNT' ? count : null,
        startDate,
        endDate: addDays(startDate, days - 1),
      });
      c.setCoachingId(null);
      c.setNudgeId(null);
      c.setChallengeId(result.challengeId);
      c.say('챌린지가 등록됐어요. 지출을 자동으로 추적할게요.');
      c.go('S013');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const open = session?.status === 'IN_PROGRESS';

  return (
    <div className="screen">
      <Hd title="AI 재무 코치" onBack={() => c.go(c.nudgeId ? 'S011' : 'S005')} />
      {error && <div className="err">{error}</div>}
      {!session && !error && <Loading text="코치를 불러오는 중…" />}
      {session?.messages?.map((message) => (
        <div key={message.id} className={`bub ${message.role === 'USER' ? 'me' : 'ai'}`}>
          {message.content}
          {message.isFallback && <div className="t2">임시 응답이에요.</div>}
        </div>
      ))}
      {open && session?.messages?.length === 1 && (
        <div className="chips" style={{ flexWrap: 'wrap' }}>
          {QUICK.map((reply) => (
            <button type="button" key={reply} onClick={() => send(reply)}>{reply}</button>
          ))}
        </div>
      )}
      {open && proposal && (
        <div className="card" style={{ border: '1.5px solid var(--primary)' }}>
          <span className="chip">🎯 AI 절약 목표 제안</span>
          <div className="t2" style={{ margin: '12px 0 4px' }}>{proposal.title}</div>
          {proposal.goalType === 'COUNT' ? (
            <div className="step">
              <button type="button" onClick={() => setCount(Math.max(1, count - 1))}>−</button>
              <b className="h2">{count}회</b>
              <button type="button" onClick={() => setCount(count + 1)}>＋</button>
            </div>
          ) : (
            <div className="step">
              <button type="button" onClick={() => setAmount(Math.max(1000, amount - 5000))}>−</button>
              <b className="h2">{won(amount)}</b>
              <button type="button" onClick={() => setAmount(amount + 5000)}>＋</button>
            </div>
          )}
          <div className="t2" style={{ margin: '12px 0 6px' }}>기간</div>
          <div className="chips">
            {[7, 14, 30].map((period) => (
              <button type="button" key={period} className={days === period ? 'on' : ''} onClick={() => setDays(period)}>{period}일</button>
            ))}
          </div>
          <button type="button" className="btn" disabled={busy} onClick={confirm}>목표 확정</button>
        </div>
      )}
      {open && (
        <div className="row">
          <input className="input" maxLength={500} placeholder="편하게 이야기해 주세요" value={text} onChange={(event) => setText(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') send(text); }} />
          <button type="button" className="btn sm" style={{ width: 88 }} disabled={busy || !text.trim()} onClick={() => send(text)}>전송</button>
        </div>
      )}
    </div>
  );
}

function goalValue(challenge) {
  return challenge.goalType === 'COUNT' ? `${challenge.goalCount}회` : won(challenge.goalAmount);
}

function spentValue(challenge) {
  return challenge.goalType === 'COUNT' ? `${challenge.spendingCount}회` : won(challenge.spendingAmount);
}

export function Challenge({ c }) {
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancel = false;
    api.challenges().then((list) => { if (!cancel) setItems(list || []); }).catch((err) => { if (!cancel) setError(err.message); });
    return () => { cancel = true; };
  }, []);

  const active = items?.find((item) => item.status === 'IN_PROGRESS');
  const done = (items || []).filter((item) => item.status !== 'IN_PROGRESS');
  const rate = active ? percent(active.achievementRate) : 0;

  return (
    <div className="screen">
      <h1 className="h1" style={{ marginBottom: 16 }}>소비 챌린지</h1>
      {error && <div className="err">{error}</div>}
      {items == null && !error && <Loading />}
      {items && !active && (
        <div className="card center">
          <div className="big">🎯</div>
          <div className="t2" style={{ margin: '8px 0' }}>진행 중인 챌린지가 없어요</div>
          <button type="button" className="btn sm" onClick={() => { c.setNudgeId(null); c.setCoachingId(null); c.go('S012'); }}>AI 코칭으로 챌린지 만들기</button>
        </div>
      )}
      {active && (
        <div className="card">
          <div className="row"><b>{active.title}</b><span className="chip">진행 중</span></div>
          <div className="t2">{rangeLabel(active.startDate, active.endDate)} · {active.daysLeft}일 남음</div>
          <div className="row" style={{ marginTop: 12 }}>
            <div><div className="t2">실제 소비</div><b className="h2">{spentValue(active)}</b></div>
            <div style={{ textAlign: 'right' }}><div className="t2">목표</div><b className="h2">{goalValue(active)}</b></div>
          </div>
          <Usage rate={rate} />
          <div className="t2">목표 대비 {rate}% 사용</div>
          <div className="t2" style={{ marginTop: 8 }}>기간이 끝나면 서버가 달성 여부를 계산하고 성장 리포트를 만들어요.</div>
        </div>
      )}
      {done.length > 0 && (
        <div className="card">
          <h2 className="h2">완료된 챌린지</h2>
          {done.map((item) => (
            <div className="tx" key={item.id}>
              <div className="ico">{item.status === 'SUCCESS' ? '🏆' : '🌱'}</div>
              <div style={{ flex: 1 }}>{item.title}<div className="t2">실제 {spentValue(item)}</div></div>
              <button type="button" className="chip" onClick={() => { c.setChallengeId(item.id); c.go('S014'); }}>
                {item.status === 'SUCCESS' ? '달성' : '미달성'}
              </button>
            </div>
          ))}
        </div>
      )}
      <div className="pad" />
    </div>
  );
}

export function Report({ c }) {
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!c.challengeId) {
      setError('리포트를 열 챌린지가 없어요.');
      return undefined;
    }
    let cancel = false;
    api.report(c.challengeId).then((data) => { if (!cancel) setReport(data); }).catch((err) => { if (!cancel) setError(err.message); });
    return () => { cancel = true; };
  }, [c.challengeId]);

  if (error) {
    return (
      <div className="screen">
        <Hd title="성장 리포트" onBack={() => c.go('S013')} />
        <div className="err">{error}</div>
      </div>
    );
  }
  if (!report) return <div className="screen"><Loading /></div>;

  const success = report.status === 'SUCCESS';
  const countGoal = report.goalType === 'COUNT';
  const goal = countGoal ? report.goalCount : report.goalAmount;
  const spent = countGoal ? report.during.count : report.spendingAmount;
  const unit = (value) => (countGoal ? `${value}회` : won(value));
  const diff = Math.abs(report.difference);

  return (
    <div className="screen">
      <Hd title="성장 리포트" onBack={() => c.go('S013')} />
      <div className="card center">
        <div className="big">{success ? '🎉' : '🌱'}</div>
        <h2 className="h1" style={{ margin: '8px 0' }}>{success ? '챌린지 달성!' : '아쉽지만 괜찮아요'}</h2>
        <div className="t2">{report.title}</div>
        <div className="t2" style={{ marginTop: 6 }}>
          {report.difference === 0 ? '목표와 같아요' : success ? `목표보다 ${unit(diff)} 덜 썼어요` : `목표보다 ${unit(diff)} 더 썼어요`}
        </div>
      </div>
      <div className="card">
        <b>목표 대비 실제 소비</b>
        <div className="row" style={{ margin: '10px 0 4px' }}><span className="t2">목표</span><b>{unit(goal)}</b></div>
        <div className="bar"><i style={{ width: '100%', background: 'var(--primary-light)' }} /></div>
        <div className="row" style={{ margin: '10px 0 4px' }}><span className="t2">실제</span><b>{unit(spent)}</b></div>
        <div className="bar"><i style={{ width: `${Math.min(100, goal ? spent / Math.max(goal, spent) * 100 : 0)}%`, background: success ? 'var(--success)' : 'var(--warning)' }} /></div>
        <div className="t2" style={{ marginTop: 8 }}>달성률 {percent(report.achievementRate)}%</div>
      </div>
      <div className="card">
        <b>챌린지 전 → 후</b>
        <div className="row" style={{ marginTop: 10 }}>
          <div><div className="t2">챌린지 전</div><b>{unit(countGoal ? report.before.count : report.before.amount)}</b></div>
          <span>→</span>
          <div style={{ textAlign: 'right' }}><div className="t2">챌린지 중</div><b>{unit(countGoal ? report.during.count : report.during.amount)}</b></div>
        </div>
      </div>
      <div className="card">
        <b>AI 피드백</b>
        <p className="t2" style={{ fontSize: 14, lineHeight: 1.6 }}>{report.feedback}</p>
      </div>
      <button type="button" className="btn" onClick={() => c.go('S005')}>홈으로 돌아가기</button>
      {!success && (
        <button type="button" className="btn sub" onClick={() => { c.setNudgeId(null); c.setCoachingId(null); c.go('S012'); }}>다음 목표로 코칭 받기</button>
      )}
    </div>
  );
}
