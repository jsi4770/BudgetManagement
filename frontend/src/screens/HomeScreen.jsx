import { useEffect, useState } from 'react';
import { api } from '../api';
import { Loading, TransactionRow, Usage } from '../components/common';
import { greetingDate, percent, won, yearMonth } from '../format';

export function Home({ c }) {
  const [monthly, setMonthly] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [challenge, setChallenge] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancel = false;
    (async () => {
      try {
        const [summary, page] = await Promise.all([
          api.monthly(yearMonth()),
          api.transactions({ size: 3 }),
        ]);
        const active = await api.challenges('IN_PROGRESS').catch(() => []);
        if (cancel) return;
        setMonthly(summary);
        setTransactions(page?.items || []);
        setChallenge(active?.[0] || null);
      } catch (err) {
        if (!cancel) setError(err.message);
      } finally {
        if (!cancel) setLoading(false);
      }
    })();
    return () => { cancel = true; };
  }, []);

  if (loading) return <div className="screen"><Loading /></div>;

  const rate = percent(monthly?.budgetUsageRate);
  const remaining = monthly?.remainingBudget;

  return (
    <div className="screen">
      <div className="t2">{greetingDate()}</div>
      <h1 className="h1" style={{ margin: '2px 0 20px' }}>{c.user?.username || '회원'}님, 안녕하세요 👋</h1>
      {error && <div className="err">{error}</div>}
      <div className="card">
        <div className="t2">남은 생활비</div>
        {remaining == null ? (
          <>
            <div className="t2" style={{ margin: '8px 0' }}>이번 달 예산이 아직 없어요.</div>
            <button type="button" className="btn sm" onClick={() => c.go('S004')}>예산 설정하기</button>
          </>
        ) : (
          <>
            <div className="amount" style={{ color: remaining < 0 ? 'var(--error)' : 'var(--text)' }}>
              {remaining < 0 ? '-' : ''}{won(remaining)}
            </div>
            <Usage rate={rate} />
            <div className="row t2"><span>예산 사용률 {rate}%</span><span>예산 {won(monthly.budget)}</span></div>
          </>
        )}
        <div className="row" style={{ marginTop: 16, paddingTop: 14, borderTop: '1px solid var(--line)' }}>
          <div><div className="t2">총수입</div><b style={{ color: 'var(--success)', fontSize: 18 }}>+{won(monthly?.totalIncome)}</b></div>
          <div style={{ textAlign: 'right' }}><div className="t2">총지출</div><b style={{ fontSize: 18 }}>-{won(monthly?.totalExpense)}</b></div>
        </div>
      </div>

      {challenge && (
        <div className="card" onClick={() => { c.setChallengeId(challenge.id); c.go('S013'); }} style={{ cursor: 'pointer' }}>
          <span className="chip">🎯 진행 중 챌린지</span>
          <div style={{ marginTop: 8 }}>{challenge.title} · 현재 {won(challenge.spendingAmount)} ›</div>
        </div>
      )}

      <div className="card">
        <div className="row">
          <h2 className="h2">최근 거래</h2>
          <button type="button" className="t2" onClick={() => c.go('S006')}>전체보기 ›</button>
        </div>
        {transactions.length === 0 && <div className="t2 center" style={{ padding: 16 }}>아직 기록이 없어요</div>}
        {transactions.map((transaction) => (
          <TransactionRow key={transaction.id} transaction={transaction} />
        ))}
      </div>
      <div className="pad" />
    </div>
  );
}
