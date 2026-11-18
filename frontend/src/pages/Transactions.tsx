import { useMemo, useState } from 'react'
import { api } from '../api'
import Sheet from '../components/Sheet'
import { useToast } from '../components/Toast'
import { errMsg, useLoad } from '../hooks'
import { monthStr, shortDate, todayStr, won } from '../format'
import type { Category, MonthlySummary, Page, Parsed, Transaction } from '../types'

const lastDay = (ym: string) => {
  const [y, m] = ym.split('-').map(Number)
  return `${ym}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`
}
const shiftMonth = (ym: string, d: number) => {
  const [y, m] = ym.split('-').map(Number)
  return monthStr(new Date(y, m - 1 + d, 1))
}

export default function Transactions() {
  const toast = useToast()
  const [ym, setYm] = useState(monthStr())
  const [adding, setAdding] = useState(false)
  const [detail, setDetail] = useState<Transaction | null>(null)

  const list = useLoad(
    () => api<Page<Transaction>>('/transactions', { query: { from: `${ym}-01`, to: ym === monthStr() ? todayStr() : lastDay(ym), size: 100 } }),
    [ym],
  )
  const summary = useLoad(() => api<MonthlySummary>('/analysis/monthly', { query: { yearMonth: ym } }), [ym])
  const categories = useLoad(() => api<Category[]>('/categories'), [])

  const groups = useMemo(() => {
    const m = new Map<string, Transaction[]>()
    list.data?.items.forEach((t) => m.set(t.transactionDate, [...(m.get(t.transactionDate) ?? []), t]))
    return [...m.entries()]
  }, [list.data])

  function refreshAll() { list.reload(); summary.reload() }

  async function remove(t: Transaction) {
    try {
      await api(`/transactions/${t.id}`, { method: 'DELETE' })
      toast('삭제했어요')
      setDetail(null)
      refreshAll()
    } catch (e) { toast(errMsg(e)) }
  }

  return (
    <div className="app">
      <div className="page-head">
        <h1>내역</h1>
        <div className="row">
          <button className="btn ghost small" aria-label="이전 달" onClick={() => setYm(shiftMonth(ym, -1))}>‹</button>
          <b>{Number(ym.slice(5))}월</b>
          <button className="btn ghost small" aria-label="다음 달" disabled={ym >= monthStr()} onClick={() => setYm(shiftMonth(ym, 1))}>›</button>
        </div>
      </div>

      <div className="card row between">
        <div><div className="sub">지출</div><b>{won(summary.data?.totalExpense ?? 0)}</b></div>
        <div style={{ textAlign: 'right' }}><div className="sub">수입</div><b>{won(summary.data?.totalIncome ?? 0)}</b></div>
      </div>

      {list.loading && <p className="muted">불러오는 중…</p>}
      {list.error && <p className="error">{list.error} <button className="link-btn" onClick={list.reload}>다시 시도</button></p>}
      {!list.loading && groups.length === 0 && !list.error && <p className="muted" style={{ textAlign: 'center', marginTop: 40 }}>이 달에는 기록이 없어요.</p>}

      {groups.map(([date, items]) => (
        <div key={date}>
          <div className="date-head">{shortDate(date)}</div>
          <div className="card list" style={{ padding: '0 16px' }}>
            {items.map((t) => (
              <button key={t.id} className="item" style={{ background: 'none', border: 0, textAlign: 'left', width: '100%', cursor: 'pointer' }} onClick={() => setDetail(t)}>
                <div className="grow"><div>{t.memo || t.category.name}</div><div className="sub">{t.category.name}</div></div>
                <div className={`amt${t.category.type === 'INCOME' ? ' income' : ''}`}>{t.category.type === 'INCOME' ? '+' : '-'}{won(t.amount)}</div>
              </button>
            ))}
          </div>
        </div>
      ))}

      <button className="fab" aria-label="내역 추가" onClick={() => setAdding(true)}>+</button>

      {adding && categories.data && (
        <QuickAdd categories={categories.data} onClose={() => setAdding(false)} onSaved={() => { setAdding(false); toast('저장했어요'); refreshAll() }} />
      )}

      {detail && (
        <Sheet label="내역 상세" onClose={() => setDetail(null)}>
          <h2>{detail.memo || detail.category.name}</h2>
          <dl className="kv">
            <div><dt>금액</dt><dd>{won(detail.amount)}</dd></div>
            <div><dt>분류</dt><dd>{detail.category.name}</dd></div>
            <div><dt>날짜</dt><dd>{detail.transactionDate}</dd></div>
          </dl>
          <button className="btn danger" onClick={() => remove(detail)}>삭제</button>
          <button className="btn ghost" style={{ marginTop: 8 }} onClick={() => setDetail(null)}>닫기</button>
        </Sheet>
      )}
    </div>
  )
}

/** 자연어로 적으면 서버가 금액·분류·날짜를 뽑아 주고, 확인 후 저장한다. 직접 입력도 된다. */
function QuickAdd({ categories, onClose, onSaved }: { categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const [text, setText] = useState('')
  const [parsing, setParsing] = useState(false)
  const [amount, setAmount] = useState('')
  const [categoryId, setCategoryId] = useState<number | ''>('')
  const [date, setDate] = useState(todayStr())
  const [memo, setMemo] = useState('')
  const [hint, setHint] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function parse() {
    setParsing(true); setError(''); setHint('')
    try {
      const p = await api<Parsed>('/transactions/parse', { method: 'POST', body: { text } })
      if (p.amount) setAmount(String(p.amount))
      if (p.category) setCategoryId(p.category.id)
      if (p.transactionDate) setDate(p.transactionDate)
      if (p.memo) setMemo(p.memo)
      setHint(p.missingFields.length ? `확인이 필요해요: ${p.missingFields.map((f) => ({ amount: '금액', category: '분류', transactionDate: '날짜' } as Record<string, string>)[f] ?? f).join(', ')}` : '이렇게 이해했어요. 맞는지 확인해 주세요.')
    } catch (e) { setError(errMsg(e)) } finally { setParsing(false) }
  }

  async function save() {
    setSaving(true); setError('')
    try {
      await api('/transactions', { method: 'POST', body: { categoryId, amount: Number(amount), transactionDate: date, memo: memo || null } })
      onSaved()
    } catch (e) { setError(errMsg(e)); setSaving(false) }
  }

  const valid = categoryId !== '' && Number(amount) >= 1 && !!date
  return (
    <Sheet label="내역 추가" onClose={onClose}>
      <h2>내역 추가</h2>
      <div className="field">
        <label htmlFor="nl">한 줄로 적어 보세요</label>
        <div className="row">
          <input id="nl" className="input" placeholder="예: 어제 스타벅스 4500원" value={text} maxLength={200} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && text.trim() && parse()} />
          <button className="btn small" disabled={!text.trim() || parsing} onClick={parse}>{parsing ? '…' : '분석'}</button>
        </div>
      </div>
      {hint && <p className="notice" style={{ margin: '12px 0 0' }}>{hint}</p>}
      <div className="stack" style={{ marginTop: 14 }}>
        <div className="field"><label htmlFor="amt">금액(원)</label>
          <input id="amt" className="input" type="number" inputMode="numeric" min={1} value={amount} onChange={(e) => setAmount(e.target.value)} /></div>
        <div className="field"><label htmlFor="cat">분류</label>
          <select id="cat" className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">선택해 주세요</option>
            <optgroup label="지출">{categories.filter((c) => c.type === 'EXPENSE').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</optgroup>
            <optgroup label="수입">{categories.filter((c) => c.type === 'INCOME').map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</optgroup>
          </select></div>
        <div className="field"><label htmlFor="dt">날짜</label>
          <input id="dt" className="input" type="date" max={todayStr()} value={date} onChange={(e) => setDate(e.target.value)} /></div>
        <div className="field"><label htmlFor="memo">메모</label>
          <input id="memo" className="input" maxLength={255} value={memo} onChange={(e) => setMemo(e.target.value)} /></div>
        <button className="btn" disabled={!valid || saving} onClick={save}>{saving ? '저장 중…' : '저장'}</button>
        <p className="error" role="alert">{error}</p>
      </div>
    </Sheet>
  )
}
