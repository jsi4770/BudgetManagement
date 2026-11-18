import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import { useToast } from '../components/Toast'
import { errMsg, useLoad } from '../hooks'
import { addDays, todayStr, won } from '../format'
import type { ChatMessage, CoachingSession, Merchant, Proposal, SendMessageResult } from '../types'

export default function Coaching() {
  const { id } = useParams()
  const nav = useNavigate()
  const toast = useToast()
  const session = useLoad(() => api<CoachingSession>(`/coachings/${id}`), [id])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [merchantsByMsg, setMerchantsByMsg] = useState<Record<number, Merchant[]>>({})
  const [proposal, setProposal] = useState<Proposal | null>(null)
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (session.data) { setMessages(session.data.messages); setProposal(session.data.proposal) }
  }, [session.data])
  useEffect(() => { bottom.current?.scrollIntoView({ block: 'end' }) }, [messages, sending])

  const ended = session.data?.status !== undefined && session.data.status !== 'IN_PROGRESS'

  async function send() {
    const content = text.trim()
    if (!content || sending) return
    setSending(true)
    setText('')
    try {
      const r = await api<SendMessageResult>(`/coachings/${id}/messages`, { method: 'POST', body: { content } })
      setMessages((m) => [...m, r.userMessage, r.aiMessage])
      if (r.proposal) setProposal(r.proposal)
      if (r.recommendedMerchants?.length) setMerchantsByMsg((p) => ({ ...p, [r.aiMessage.id]: r.recommendedMerchants }))
    } catch (e) { toast(errMsg(e)); setText(content) } finally { setSending(false) }
  }

  if (session.loading) return <div className="center-screen">불러오는 중…</div>
  if (session.error) return <div className="app"><p className="error">{session.error}</p><Link to="/coaching">돌아가기</Link></div>

  return (
    <div className="app">
      <div className="page-head"><h1>AI 코칭</h1><Link to="/coaching" className="sub">새 대화</Link></div>

      <div className="chat" aria-live="polite">
        {messages.map((m) => (
          <div key={m.id} style={{ display: 'grid', gap: 8 }}>
            <div className={`bubble ${m.role === 'AI' ? 'ai' : 'user'}`}>{m.content}</div>
            {merchantsByMsg[m.id] && (
              <div className="mini-merchants" aria-label="추천 가맹점">
                {merchantsByMsg[m.id].map((mc) => (
                  <div className="mcard" key={mc.id}>
                    <span className="tag" style={{ justifySelf: 'start' }}>{mc.groupLabel} · 지역화폐</span>
                    <b>{mc.name}</b>
                    <span className="sub">{mc.address}</span>
                    {mc.phone && <a href={`tel:${mc.phone}`} className="sub">{mc.phone}</a>}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        {sending && <div className="typing">코치가 생각하는 중…</div>}
        <div ref={bottom} />
      </div>

      {proposal && !ended && (
        <ProposalCard
          key={`${proposal.category?.id}-${proposal.goalAmount}-${proposal.goalCount}-${proposal.periodDays}`}
          coachingId={id!}
          proposal={proposal}
          onConfirmed={() => { toast('챌린지를 시작했어요!'); nav('/') }}
        />
      )}

      {ended ? (
        <div className="notice">끝난 대화예요. <Link to="/coaching">새 대화 시작</Link></div>
      ) : (
        <div className="composer">
          <input className="input" aria-label="메시지" placeholder="코치에게 이야기해 보세요" value={text} maxLength={500}
            onChange={(e) => setText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && !e.nativeEvent.isComposing && send()} />
          <button className="btn" disabled={!text.trim() || sending} onClick={send}>전송</button>
        </div>
      )}
    </div>
  )
}

/** AI 가 제안한 목표. 금액(횟수)과 기간을 조절해 확정하면 챌린지가 시작된다. */
function ProposalCard({ coachingId, proposal, onConfirmed }: { coachingId: string; proposal: Proposal; onConfirmed: () => void }) {
  const [amount, setAmount] = useState(proposal.goalAmount ?? 0)
  const [count, setCount] = useState(proposal.goalCount ?? 0)
  const [days, setDays] = useState(proposal.periodDays)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const isAmount = proposal.goalType === 'AMOUNT'
  const catName = proposal.category?.name ?? '지출'

  async function confirm() {
    if (!proposal.category) return
    setBusy(true); setError('')
    const start = todayStr()
    try {
      await api(`/coachings/${coachingId}/confirm`, {
        method: 'POST',
        body: {
          categoryId: proposal.category.id,
          title: proposal.title || (isAmount ? `${catName} ${amount.toLocaleString()}원 이하` : `${catName} ${count}회 이하`),
          goalType: proposal.goalType,
          goalAmount: isAmount ? amount : null,
          goalCount: isAmount ? null : count,
          startDate: start,
          endDate: addDays(start, days - 1),
        },
      })
      onConfirmed()
    } catch (e) { setError(errMsg(e)); setBusy(false) }
  }

  return (
    <div className="card stack" style={{ borderColor: 'var(--brand)' }}>
      <div className="row between"><b>코치의 제안</b><span className="tag">{catName}</span></div>
      <div className="row between">
        <span>{isAmount ? '기간 동안 최대' : '기간 동안 최대 횟수'}</span>
        <div className="row">
          <button className="btn ghost small" aria-label="줄이기" onClick={() => isAmount ? setAmount((a) => Math.max(1000, a - 5000)) : setCount((c) => Math.max(1, c - 1))}>−</button>
          <b style={{ minWidth: 92, textAlign: 'center' }}>{isAmount ? won(amount) : `${count}회`}</b>
          <button className="btn ghost small" aria-label="늘리기" onClick={() => isAmount ? setAmount((a) => a + 5000) : setCount((c) => c + 1)}>＋</button>
        </div>
      </div>
      <div className="chips" role="group" aria-label="기간">
        {[7, 14, 30].map((d) => <button key={d} className="chip" aria-pressed={days === d} onClick={() => setDays(d)}>{d}일</button>)}
      </div>
      <button className="btn" disabled={busy || !proposal.category} onClick={confirm}>{busy ? '시작하는 중…' : '이 목표로 챌린지 시작'}</button>
      <p className="error" role="alert">{error}</p>
    </div>
  )
}
