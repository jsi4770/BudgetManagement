import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { Hd, Loading, Usage } from '../components/common';
import { advisePattern, iconFor, percent, rangeLabel, weekSpan, won, yearMonth } from '../format';
import { Icon, PigLogo } from '../icons';
import { categoryLabel, usePrefs } from '../prefs';

export function Analysis({ c }) {
  const { prefs } = usePrefs();
  const [tab, setTab] = useState('WEEK');
  const [weekly, setWeekly] = useState([]);
  const [monthly, setMonthly] = useState(null);
  const [comparison, setComparison] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancel = false;
    (async () => {
      setLoading(true);
      setError('');
      try {
        const [weeks, summary, categories] = await Promise.all([
          api.weekly(4),
          api.monthly(yearMonth()),
          api.categoryComparison(tab),
        ]);
        if (cancel) return;
        setWeekly(weeks?.weeks || []);
        setMonthly(summary);
        setComparison(categories);
      } catch (err) {
        if (!cancel) setError(err.message);
      } finally {
        if (!cancel) setLoading(false);
      }
    })();
    return () => { cancel = true; };
  }, [tab]);

  const max = Math.max(1, ...weekly.map((week) => week.totalExpense));

  return (
    <div className="screen">
      <h1 className="h1" style={{ marginBottom: 16 }}>소비 분석</h1>
      <div className="tabs">
        <button type="button" className={tab === 'WEEK' ? 'on' : ''} onClick={() => setTab('WEEK')}>주간</button>
        <button type="button" className={tab === 'MONTH' ? 'on' : ''} onClick={() => setTab('MONTH')}>월간</button>
      </div>
      {error && <div className="err">{error}</div>}
      {loading && <Loading />}
      {!loading && (
        <>
          <div className="card">
            <div className="t2">{tab === 'WEEK' ? '이번 주 지출(월세 제외)' : '이번 달 총지출'}</div>
            <div className="amount">{won(tab === 'WEEK' ? weekly.at(-1)?.totalExpense : monthly?.totalExpense)}</div>
            {tab === 'WEEK' ? (
              <div style={{ marginTop: 12 }}>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 88 }}>
                  {weekly.map((week, index) => (
                    <div key={week.weekStart} style={{ flex: 1, height: Math.max(4, (week.totalExpense / max) * 80), background: index === weekly.length - 1 ? 'var(--primary)' : 'var(--primary-light)', borderRadius: 8 }} />
                  ))}
                </div>
                <div className="week-row">
                  {weekly.map((week) => (
                    <span key={week.weekStart}>{weekSpan(week.weekStart, week.weekEnd)}</span>
                  ))}
                </div>
              </div>
            ) : (
              <>
                <Usage rate={percent(monthly?.budgetUsageRate)} />
                <div className="t2">예산 사용률 {percent(monthly?.budgetUsageRate)}%</div>
              </>
            )}
          </div>
          <div className="card">
            <h2 className="h2" style={{ marginBottom: 12 }}>카테고리별 · 평소 평균 비교</h2>
            {comparison?.dataSufficient === false && <div className="t2" style={{ marginBottom: 8 }}>기록이 더 쌓이면 정확해져요.</div>}
            {[...(comparison?.items || [])]
              .filter((item) => item.amount > 0)
              .sort((a, b) => b.amount - a.amount)
              .map((item) => (
              <button type="button" key={item.category.id} onClick={() => c.openCategory(item.category, 'S010')} style={{ display: 'block', width: '100%', textAlign: 'left', marginBottom: 14 }}>
                <div className="row">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <Icon name={iconFor(item.category.name)} size={16} /> {categoryLabel(item.category, prefs.aliases)}
                  </span>
                  <b>{won(item.amount)}</b>
                </div>
                <div className="bar" style={{ marginTop: 6 }}>
                  <i style={{ width: `${Math.min(100, item.averageAmount ? item.amount / Math.max(item.amount, item.averageAmount) * 100 : 100)}%`, background: 'var(--primary)' }} />
                </div>
                <div className="t2">
                  {item.category.name === '월세' && item.averageAmount == null
                    ? '비교는 월세 제외'
                    : `평소 이맘때 ${item.averageAmount == null ? '비교 없음' : won(item.averageAmount)}`}
                  {item.growthRate != null && ` · ${percent(item.growthRate) > 0 ? '+' : ''}${percent(item.growthRate)}%`}
                  {item.isAnomaly && ' · 평소보다 높아요'}
                </div>
              </button>
            ))}
            {(comparison?.items || []).every((item) => item.amount === 0) && <div className="t2">이 기간의 지출이 없어요.</div>}
          </div>
        </>
      )}
      <div className="pad" />
    </div>
  );
}

export function NudgeDetail({ c }) {
  const [nudge, setNudge] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!c.nudgeId) return undefined;
    let cancel = false;
    let timer;
    const load = async () => {
      try {
        const data = await api.nudge(c.nudgeId);
        if (cancel) return;
        setNudge(data);
        if (!data.aiAnalysis || !data.aiRecommendation) timer = setTimeout(load, 2000);
      } catch (err) {
        if (!cancel) setError(err.message);
      }
    };
    load();
    return () => { cancel = true; clearTimeout(timer); };
  }, [c.nudgeId]);

  return (
    <div className="screen">
      <Hd title="넛지 상세" onBack={() => c.go('S005')} />
      {error && <div className="err">{error}</div>}
      {!nudge && !error && <Loading />}
      {nudge && (
        <>
          <div className="card nudge">
            <span className="chip" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Icon name="bell" size={14} /> 소비 증가 항목</span>
            <h2 className="h1" style={{ margin: '12px 0 4px', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name={iconFor(nudge.category?.name)} /> {nudge.category?.name}
            </h2>
            <div className="amount">{won(nudge.spendingAmount)}</div>
            <div className="t2">
              평소 이맘때 평균 {won(nudge.avgAmount)} 대비 <b style={{ color: 'var(--error)' }}>{percent(nudge.growthRate) > 0 ? '+' : ''}{percent(nudge.growthRate)}%</b>
            </div>
            <div className="t2" style={{ marginTop: 6 }}>{rangeLabel(nudge.analysisStartDate, nudge.analysisEndDate)}</div>
          </div>
          <div className="card">
            <b>AI 분석</b>
            <p className="t2" style={{ fontSize: 14, lineHeight: 1.6 }}>{nudge.aiAnalysis || 'AI가 분석 중이에요. 잠시 후 다시 보여드릴게요.'}</p>
            <b>AI 제안</b>
            <p className="t2" style={{ fontSize: 14, lineHeight: 1.6 }}>{nudge.aiRecommendation || '제안을 준비하고 있어요.'}</p>
          </div>
          <button type="button" className="btn" onClick={() => { c.setCoachingId(null); c.go('S012'); }}>AI 코칭 시작하기</button>
          <button type="button" className="btn sub" onClick={async () => {
            try { await api.nudgeStatus(c.nudgeId, 'VIEWED'); } catch { /* 상태 변경이 실패해도 분석 화면으로 이동한다. */ }
            c.go('S010');
          }}>소비 현황 확인</button>
        </>
      )}
    </div>
  );
}

/** AI 가 추천한 우리 동네 가맹점 한 곳. 전화번호를 누르면 전화가 걸린다. */
function ChatMerchant({ merchant }) {
  return (
    <div className="m-card">
      <div className="row">
        <b>{merchant.name}</b>
        {merchant.groupLabel && <span className="chip">{merchant.groupLabel}</span>}
      </div>
      {merchant.address && <div className="t2">{merchant.address}</div>}
      {merchant.phone && <a className="t2 tel" href={`tel:${merchant.phone}`}>{merchant.phone}</a>}
    </div>
  );
}

export function AnalysisChat({ c }) {
  const [open, setOpen] = useState(false);
  const [coachingId, setCoachingId] = useState(null);
  const [messages, setMessages] = useState([
    { role: 'AI', content: '이번 소비에서 궁금한 점을 물어보세요.' },
  ]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const logRef = useRef(null);

  // 새 메시지가 오면 따라간다. AI 답변은 가맹점 카드가 길어도 답변 첫 줄부터 보이게 그 시작 위치로 맞춘다.
  useEffect(() => {
    const log = logRef.current;
    const last = log?.lastElementChild;
    if (!log || !last) return;
    const isAi = messages[messages.length - 1]?.role === 'AI';
    log.scrollTop = isAi ? Math.max(0, last.offsetTop - log.offsetTop - 8) : log.scrollHeight;
  }, [messages, open]);

  const send = async () => {
    const message = text.trim();
    if (!message || busy) return;
    setMessages((current) => [...current, { role: 'USER', content: message }]);
    setText('');
    setBusy(true);
    try {
      let id = coachingId;
      if (!id) {
        const session = await api.startCoaching({});
        id = session.coachingId;
        setCoachingId(id);
      }
      const result = await api.sendMessage(id, message);
      const reply = result.aiMessage?.content || advisePattern(message).message;
      // 서버가 후보 목록에서 검증해서 돌려준 가맹점만 답변 아래에 붙인다.
      const merchants = Array.isArray(result.recommendedMerchants) ? result.recommendedMerchants : [];
      setMessages((current) => [...current, { role: 'AI', content: reply, merchants }]);
    } catch {
      setMessages((current) => [...current, { role: 'AI', content: advisePattern(message).message }]);
    } finally {
      setBusy(false);
    }
  };

  if (!open) {
    return (
      <button type="button" className="chat-fab" aria-label="AI 챗봇" onClick={() => setOpen(true)}>
        <Icon name="ai-coach" size={32} />
      </button>
    );
  }

  return (
    <>
      <div className="chat-panel">
        <div className="row" style={{ padding: '12px 14px', borderBottom: '1px solid var(--line)' }}>
          <b style={{ display: 'flex', alignItems: 'center', gap: 8 }}><PigLogo size={28} /> AI 코치</b>
          <button type="button" className="t2" onClick={() => setOpen(false)}>닫기</button>
        </div>
        <div className="chat-log" ref={logRef}>
          {messages.map((message, index) => (
            <div key={`${message.role}-${index}`} className={message.merchants?.length ? 'bub-group' : undefined}>
              <div className={`bub ${message.role === 'USER' ? 'me' : 'ai'}`}>{message.content}</div>
              {message.merchants?.length > 0 && (
                <div className="m-list">
                  <div className="t2" style={{ margin: '0 0 6px 2px' }}>
                    {c?.user?.regionName ? `${c.user.regionName} 지역화폐 가맹점` : '지역화폐 가맹점'}
                  </div>
                  {message.merchants.map((merchant) => <ChatMerchant key={merchant.id} merchant={merchant} />)}
                  {c && (
                    <button type="button" className="t2 m-more" onClick={() => { setOpen(false); c.go('S017'); }}>
                      가맹점 전체 보기 ›
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="row" style={{ padding: 12 }}>
          <input
            className="input"
            style={{ flex: 1, width: 'auto' }}
            maxLength={500}
            placeholder="소비에 대해 물어보세요"
            value={text}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => { if (event.key === 'Enter') send(); }}
          />
          <button type="button" className="btn sm" style={{ width: 72 }} disabled={busy || !text.trim()} onClick={send}>전송</button>
        </div>
      </div>
      <button type="button" className="chat-fab" aria-label="AI 챗봇 닫기" onClick={() => setOpen(false)}>
        <Icon name="ai-coach" size={32} />
      </button>
    </>
  );
}
