import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import { errMsg } from '../hooks'
import type { Merchant, Page } from '../types'

const GROUPS = [
  { v: '', label: '전체' }, { v: 'FOOD', label: '식당·주점' }, { v: 'CAFE', label: '카페' }, { v: 'GROCERY', label: '마트·편의점' },
  { v: 'SHOPPING', label: '쇼핑' }, { v: 'TRANSPORT', label: '교통·주유' }, { v: 'HEALTH', label: '병원·약국' },
  { v: 'EDU', label: '교육·학원' }, { v: 'LEISURE', label: '문화·여가' }, { v: 'LIFE', label: '생활서비스' },
]

export default function Merchants() {
  const { me } = useAuth()
  const [group, setGroup] = useState('')
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [items, setItems] = useState<Merchant[]>([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(q.trim()), 300)
    return () => window.clearTimeout(t)
  }, [q])

  async function load(p: number, replace: boolean) {
    setLoading(true); setError('')
    try {
      const r = await api<Page<Merchant>>('/local-merchants', { query: { group, q: debounced, page: p, size: 20 } })
      setItems((prev) => (replace ? r.items : [...prev, ...r.items]))
      setPage(r.page); setTotal(r.totalCount); setHasNext(r.hasNext)
    } catch (e) { setError(errMsg(e)) } finally { setLoading(false) }
  }

  useEffect(() => { load(0, true) /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [group, debounced])

  const baseDate = items.find((m) => m.baseDate)?.baseDate

  return (
    <div className="app">
      <div className="page-head">
        <div><h1>우리 동네 가맹점</h1><span className="sub">{me?.regionName} 지역사랑상품권 사용처</span></div>
      </div>
      <input className="input" type="search" placeholder="가맹점 이름 검색" aria-label="가맹점 이름 검색" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="chips" role="group" aria-label="업종" style={{ marginTop: 10 }}>
        {GROUPS.map((g) => <button key={g.v} className="chip" aria-pressed={group === g.v} onClick={() => setGroup(g.v)}>{g.label}</button>)}
      </div>

      {baseDate && <p className="sub">공개 목록 기준일 {baseDate} · 실제 사용 가능 여부는 매장에 확인해 주세요.</p>}
      {!loading && !error && <p className="sub" style={{ margin: '4px 0' }}>총 {total.toLocaleString()}곳</p>}

      <div className="stack">
        {items.map((m) => (
          <div className="card" key={m.id}>
            <div className="row between"><b>{m.name}</b><span className="tag">{m.groupLabel}</span></div>
            <div className="sub" style={{ marginTop: 4 }}>{m.address}</div>
            {m.phone && <a href={`tel:${m.phone}`} className="sub">{m.phone}</a>}
          </div>
        ))}
      </div>

      {loading && <p className="muted" style={{ textAlign: 'center' }}>불러오는 중…</p>}
      {error && <p className="error">{error} <button className="link-btn" onClick={() => load(0, true)}>다시 시도</button></p>}
      {!loading && !error && items.length === 0 && <p className="muted" style={{ textAlign: 'center', marginTop: 40 }}>조건에 맞는 가맹점이 없어요.</p>}
      {hasNext && !loading && <button className="btn ghost" style={{ marginTop: 12 }} onClick={() => load(page + 1, false)}>더 보기</button>}
    </div>
  )
}
