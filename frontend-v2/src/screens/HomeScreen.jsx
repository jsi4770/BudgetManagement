import { useEffect, useState } from 'react';
import { api } from '../api';
import { Loading, TransactionRow, Usage } from '../components/common';
import { greetingDate, percent, won, yearMonth } from '../format';
import { Icon } from '../icons';
import { PolicyRoller } from '../components/PolicyRoller';

function goalText(item) {
  return item.goalType === 'COUNT' ? `${item.goalCount ?? 0}회` : won(item.goalAmount);
}

function spentText(item) {
  return item.goalType === 'COUNT' ? `${item.spendingCount ?? 0}회` : won(item.spendingAmount);
}

export function Home({ c }) {
  const [monthly, setMonthly] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [challenges, setChallenges] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [challengeOpen, setChallengeOpen] = useState(true);
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
        const [active, recommended] = await Promise.all([
          api.challenges('IN_PROGRESS').catch(() => []),
          // 정책 영역이 실패해도 나머지 홈 화면은 그대로 보여준다.
          api.youthPolicies(10).catch(() => []),
        ]);
        if (cancel) return;
        setPolicies(recommended || []);
        setMonthly(summary);
        setTransactions(page?.items || []);
        setChallenges(active || []);
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
      <PolicyRoller policies={policies} regionName={c.user?.regionName} />
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
              {won(remaining)}
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

      <div className="card">
        <button type="button" className="row" onClick={() => setChallengeOpen((open) => !open)} style={{ width: '100%' }}>
          <h2 className="h2" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Icon name="challenge" size={18} /> 진행 중인 챌린지
          </h2>
          <span className="t2">{challengeOpen ? '접기' : `펼치기 ${challenges.length}`}</span>
        </button>
        {challengeOpen && challenges.length === 0 && (
          <div className="t2" style={{ marginTop: 12 }}>진행 중인 챌린지가 없어요</div>
        )}
        {challengeOpen && challenges.map((item) => (
          <button
            type="button"
            key={item.id}
            className="tx"
            style={{ width: '100%', textAlign: 'left' }}
            onClick={() => { c.setChallengeId(item.id); c.go('S013'); }}
          >
            <div className="ico"><Icon name="goal" size={18} /></div>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 500 }}>{item.title}</div>
              <div className="t2">실제 {spentText(item)} · 목표 {goalText(item)}{item.daysLeft != null ? ` · ${item.daysLeft}일 남음` : ''}</div>
            </div>
          </button>
        ))}
      </div>

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
