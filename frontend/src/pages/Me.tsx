import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import ProfileForm from '../components/ProfileForm'
import Sheet from '../components/Sheet'
import { useToast } from '../components/Toast'
import { errMsg, useLoad } from '../hooks'
import { genderLabel, shortDate, won } from '../format'
import type { Challenge } from '../types'

const DEMO = import.meta.env.VITE_DEMO_TOOLS === 'true'

export default function Me() {
  const { me, logout, reloadMe } = useAuth()
  const nav = useNavigate()
  const toast = useToast()
  const [editing, setEditing] = useState(false)
  const challenges = useLoad(() => api<Challenge[]>('/challenges'), [])

  async function seed(scenario: 'NORMAL' | 'DELIVERY_SPIKE') {
    try {
      const r = await api<{ transactionCount: number; nudgeCreated: boolean }>('/dev/seed', { method: 'POST', body: { scenario } })
      toast(`시연 데이터 ${r.transactionCount}건 · 넛지 ${r.nudgeCreated ? '생성' : '없음'}`)
      challenges.reload()
    } catch (e) { toast(errMsg(e)) }
  }

  return (
    <div className="app">
      <div className="page-head"><h1>마이</h1></div>

      <div className="card stack">
        <div className="row between"><b style={{ fontSize: 18 }}>{me?.username}</b><button className="link-btn" onClick={() => setEditing(true)}>수정</button></div>
        <div className="sub">{me?.email}</div>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <span className="tag">{me?.regionName ?? '지역 미설정'}</span>
          <span className="tag">{me?.age ? `만 ${me.age}세` : '나이 미설정'}</span>
          <span className="tag">{genderLabel(me?.gender ?? null)}</span>
        </div>
      </div>

      <div className="section-title"><span>챌린지 기록</span></div>
      <div className="card">
        {challenges.loading ? <p className="muted">불러오는 중…</p> : (challenges.data?.length ?? 0) === 0 ? (
          <p className="muted" style={{ margin: 0 }}>아직 챌린지가 없어요. 코칭에서 목표를 정해 보세요.</p>
        ) : (
          <div className="list">
            {challenges.data!.map((c) => (
              <div className="item" key={c.id}>
                <div className="grow">
                  <div>{c.title}</div>
                  <div className="sub">{shortDate(c.startDate)} ~ {shortDate(c.endDate)} · {c.goalType === 'AMOUNT' ? won(c.spendingAmount) : `${c.spendingCount}회`}</div>
                </div>
                <span className={`tag${c.status === 'FAILED' ? ' warn' : ''}`}>{c.status === 'IN_PROGRESS' ? '진행 중' : c.status === 'SUCCESS' ? '성공' : '아쉬움'}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {DEMO && (
        <>
          <div className="section-title"><span>시연 도구</span></div>
          <div className="card stack">
            <p className="sub" style={{ margin: 0 }}>내 거래·챌린지를 지우고 시나리오 데이터를 다시 만듭니다(백엔드 dev 프로필).</p>
            <button className="btn ghost" onClick={() => seed('DELIVERY_SPIKE')}>배달 급증 시나리오 (넛지 생성)</button>
            <button className="btn ghost" onClick={() => seed('NORMAL')}>평소 시나리오 (넛지 없음)</button>
          </div>
        </>
      )}

      <button className="btn ghost" style={{ marginTop: 20 }} onClick={async () => { await logout(); nav('/login', { replace: true }) }}>로그아웃</button>

      {editing && (
        <Sheet label="내 정보 수정" onClose={() => setEditing(false)}>
          <h2>내 정보 수정</h2>
          <ProfileForm submitLabel="저장" onDone={async () => { setEditing(false); await reloadMe(); toast('저장했어요') }} />
        </Sheet>
      )}
    </div>
  )
}
