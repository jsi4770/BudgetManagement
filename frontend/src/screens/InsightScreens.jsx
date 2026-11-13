import { useEffect, useState } from 'react';
import { api } from '../api';
import { Hd, Loading, Usage } from '../components/common';
import { iconFor, percent, rangeLabel, won, yearMonth } from '../format';

export function Analysis() {
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
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, height: 110, marginTop: 12 }}>
                {weekly.map((week, index) => (
                  <div key={week.weekStart} style={{ flex: 1, textAlign: 'center' }}>
                    <div style={{ height: (week.totalExpense / max) * 80, background: index === weekly.length - 1 ? 'var(--primary)' : 'var(--primary-light)', borderRadius: 8 }} />
                    <div className="t2" style={{ marginTop: 4 }}>{rangeLabel(week.weekStart, week.weekEnd)}</div>
                  </div>
                ))}
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
            {(comparison?.items || []).filter((item) => item.amount > 0).map((item) => (
              <div key={item.category.id} style={{ marginBottom: 14 }}>
                <div className="row">
                  <span>{iconFor(item.category.name)} {item.category.name}</span>
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
                </div>
              </div>
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
            <span className="chip">💡 소비 증가 항목</span>
            <h2 className="h1" style={{ margin: '12px 0 4px' }}>{iconFor(nudge.category?.name)} {nudge.category?.name}</h2>
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
          <button type="button" className="btn sub" onClick={() => c.go('S010')}>소비 현황 확인</button>
        </>
      )}
    </div>
  );
}
