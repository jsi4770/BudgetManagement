import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import PolicyRoller from '../components/PolicyRoller'
import { useToast } from '../components/Toast'
import { errMsg } from '../hooks'
import { monthStr, shortDate, won } from '../format'
import type { Challenge, MonthlySummary, Nudge, Page, Transaction, YouthPolicy } from '../types'

type HomeData = {
  policies: YouthPolicy[]
  summary: MonthlySummary | null
  nudge: Nudge | null
  challenges: Challenge[]
  recent: Transaction[]
}

export default function Home() {
  const { me } = useAuth()
  const nav = useNavigate()
  const toast = useToast()
  const [d, setD] = useState<HomeData | null>(null)
  const [starting, setStarting] = useState(false)

  useEffect(() => {
    let alive = true
    ;(async () => {
      // 한 영역이 실패해도 나머지는 보여 준다.
      const [policies, summary, nudge, challenges, recent] = await Promise.allSettled([
        api<YouthPolicy[]>('/youth-policies/home', { query: { limit: 10 } }),
        api<MonthlySummary>('/analysis/monthly', { query: { yearMonth: monthStr() } }),
        api<Nudge | null>('/nudges/current'),
        api<Challenge[]>('/challenges', { query: { status: 'IN_PROGRESS' } }),
        api<Page<Transaction>>('/transactions', { query: { size: 5 } }),
      ])
      const ok = <T,>(r: PromiseSettledResult<T>, fallback: T) => (r.status === 'fulfilled' ? r.value : fallback)
      if (alive)
        setD({
          policies: ok(policies, []),
          summary: ok(summary, null),
          nudge: ok(nudge, null),
          challenges: ok(challenges, []),
          recent: ok(recent, { items: [], page: 0, size: 5, totalCount: 0, hasNext: false }).items,
        })
    })()
    return () => { alive = false }
  }, [])

  async function startCoaching(nudgeId: number) {
    setStarting(true)
    try {
      const r = await api<{ coachingId: number }>('/coachings', { method: 'POST', body: { nudgeId } })
      localStorage.setItem('lastCoachingId', String(r.coachingId))
      nav(`/coaching/${r.coachingId}`)
    } catch (e) {
      toast(errMsg(e))
      setStarting(false)
    }
  }

  const s = d?.summary
  const usage = s?.budgetUsageRate ?? null

  return (
    <div className="app">
      <div className="page-head">
        <div>
          <h1>안녕하세요, {me?.username}님</h1>
          <span className="sub">{me?.regionName ? `${me.regionName} 거주` : ''}</span>
        </div>
      </div>

      <PolicyRoller policies={d?.policies ?? []} regionName={me?.regionName ?? null} />

      <div className="section-title"><span>이번 달 요약</span></div>
      <div className="card">
        {!d ? <p className="muted">불러오는 중…</p> : s ? (
          <div className="stack">
            <div className="row between">
              <div><div className="sub">지출</div><div className="big">{won(s.totalExpense)}</div></div>
              <div style={{ textAlign: 'right' }}><div className="sub">수입</div><div style={{ fontWeight: 700 }}>{won(s.totalIncome)}</div></div>
            </div>
            {s.budget ? (
              <div>
                <div className="bar" aria-hidden><i style={{ width: `${Math.min(100, usage ?? 0)}%` }} /></div>
                <div className="row between sub" style={{ marginTop: 6 }}>
                  <span>예산 {won(s.budget)}</span>
                  <span>{(s.remainingBudget ?? 0) >= 0 ? `${won(s.remainingBudget ?? 0)} 남음` : `${won(-(s.remainingBudget ?? 0))} 초과`}</span>
                </div>
              </div>
            ) : <p className="sub" style={{ margin: 0 }}>이번 달 예산을 아직 정하지 않았어요.</p>}
          </div>
        ) : <p className="muted">요약을 불러오지 못했어요.</p>}
      </div>

      {d?.nudge && (
        <>
          <div className="section-title"><span>코치의 한마디</span></div>
          <div className="card stack">
            <div><span className="tag warn">+{Math.round(d.nudge.growthRate)}%</span></div>
            <div style={{ fontWeight: 700 }}>{d.nudge.title}</div>
            <div className="sub">최근 {won(d.nudge.spendingAmount)} · 평소 {won(d.nudge.avgAmount)}</div>
            <button className="btn" disabled={starting} onClick={() => startCoaching(d.nudge!.id)}>
              {starting ? '시작하는 중…' : '코치와 이야기해 보기'}
            </button>
          </div>
        </>
      )}

      {d && d.challenges.length > 0 && (
        <>
          <div className="section-title"><span>진행 중인 챌린지</span></div>
          {d.challenges.map((c) => (
            <div className="card stack" key={c.id}>
              <div className="row between"><b>{c.title}</b><span className="tag">{c.daysLeft}일 남음</span></div>
              <div className={`bar${c.achievementRate > 100 ? ' over' : ''}`}><i style={{ width: `${Math.min(100, c.achievementRate)}%` }} /></div>
              <div className="sub">
                {c.goalType === 'AMOUNT'
                  ? `${won(c.spendingAmount)} / 최대 ${won(c.goalAmount ?? 0)}`
                  : `${c.spendingCount}회 / 최대 ${c.goalCount ?? 0}회`}
              </div>
            </div>
          ))}
        </>
      )}

      <div className="section-title"><span>최근 내역</span><Link to="/transactions">전체 보기</Link></div>
      <div className="card">
        {!d ? <p className="muted">불러오는 중…</p> : d.recent.length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>아직 기록이 없어요. 내역 탭에서 첫 지출을 입력해 보세요.</p>
        ) : (
          <div className="list">
            {d.recent.map((t) => (
              <div className="item" key={t.id}>
                <div className="grow"><div>{t.memo || t.category.name}</div><div className="sub">{shortDate(t.transactionDate)} · {t.category.name}</div></div>
                <div className={`amt${t.category.type === 'INCOME' ? ' income' : ''}`}>{t.category.type === 'INCOME' ? '+' : '-'}{won(t.amount)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
