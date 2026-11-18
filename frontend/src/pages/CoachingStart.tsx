import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useToast } from '../components/Toast'
import { errMsg, useLoad } from '../hooks'
import { won } from '../format'
import type { Nudge } from '../types'

export default function CoachingStart() {
  const nav = useNavigate()
  const toast = useToast()
  const nudge = useLoad(() => api<Nudge | null>('/nudges/current'), [])
  const [busy, setBusy] = useState(false)
  const last = (() => { try { return localStorage.getItem('lastCoachingId') } catch { return null } })()

  async function start(nudgeId?: number) {
    setBusy(true)
    try {
      const r = await api<{ coachingId: number }>('/coachings', { method: 'POST', body: nudgeId ? { nudgeId } : {} })
      try { localStorage.setItem('lastCoachingId', String(r.coachingId)) } catch { /* 저장소를 못 써도 진행한다 */ }
      nav(`/coaching/${r.coachingId}`)
    } catch (e) { toast(errMsg(e)); setBusy(false) }
  }

  return (
    <div className="app">
      <div className="page-head"><h1>AI 코칭</h1></div>
      <div className="stack">
        <div className="notice">소비 내역을 바탕으로 AI 코치와 이야기하고, 나에게 맞는 절약 목표를 함께 정해요. 지역을 설정했다면 우리 동네 가맹점도 추천해 드려요.</div>

        {nudge.data && (
          <div className="card stack">
            <span className="tag warn">지금 이야기해 볼 만해요</span>
            <b>{nudge.data.title}</b>
            <span className="sub">최근 {won(nudge.data.spendingAmount)} · 평소 {won(nudge.data.avgAmount)}</span>
            <button className="btn" disabled={busy} onClick={() => start(nudge.data!.id)}>이 내용으로 시작</button>
          </div>
        )}

        <button className="btn ghost" disabled={busy} onClick={() => start()}>자유롭게 새 대화 시작</button>
        {last && <button className="btn ghost" onClick={() => nav(`/coaching/${last}`)}>이전 대화 이어서 보기</button>}
      </div>
    </div>
  )
}
