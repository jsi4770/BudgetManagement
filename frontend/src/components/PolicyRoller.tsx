import { useEffect, useRef, useState } from 'react'
import type { YouthPolicy } from '../types'
import Sheet from './Sheet'

const INTERVAL = 5000

function dday(p: YouthPolicy) {
  if (p.dDay === null) return '상시'
  if (p.dDay === 0) return '오늘 마감'
  return `D-${p.dDay}`
}

/** 홈 상단에서 맞춤 청년정책을 자동으로 넘기며 보여 준다. 터치·호버·포커스 중에는 멈추고, 좌우로 밀어 넘길 수 있다. */
export default function PolicyRoller({ policies, regionName }: { policies: YouthPolicy[]; regionName: string | null }) {
  const [i, setI] = useState(0)
  const [paused, setPaused] = useState(false)
  const [open, setOpen] = useState<YouthPolicy | null>(null)
  const startX = useRef<number | null>(null)
  const moved = useRef(false)
  const n = policies.length

  useEffect(() => {
    if (n < 2 || paused || open) return
    const t = window.setInterval(() => setI((x) => (x + 1) % n), INTERVAL)
    return () => window.clearInterval(t)
  }, [n, paused, open])

  if (n === 0) {
    return <div className="roller roller-empty">{regionName ?? '내 지역'}에서 지금 신청할 수 있는 청년정책을 찾고 있어요.</div>
  }

  const go = (d: number) => setI((x) => (x + d + n) % n)

  return (
    <>
      <div
        className="roller"
        role="region"
        aria-roledescription="carousel"
        aria-label="맞춤 청년정책"
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
        onFocus={() => setPaused(true)}
        onBlur={() => setPaused(false)}
        onPointerDown={(e) => { startX.current = e.clientX; moved.current = false; setPaused(true) }}
        onPointerUp={(e) => {
          if (startX.current !== null) {
            const dx = e.clientX - startX.current
            if (Math.abs(dx) > 40) { go(dx < 0 ? 1 : -1); moved.current = true }
          }
          startX.current = null
          setPaused(false)
        }}
        onPointerCancel={() => { startX.current = null; setPaused(false) }}
      >
        <div className="roller-track" style={{ transform: `translateX(-${i * 100}%)` }} aria-live={paused ? 'polite' : 'off'}>
          {policies.map((p, idx) => (
            <button
              key={p.id}
              className="slide"
              aria-hidden={idx !== i}
              tabIndex={idx === i ? 0 : -1}
              onClick={() => { if (!moved.current) setOpen(p) }}
            >
              <span className="kicker">
                <span className="pill">{p.regionSpecific ? `${regionName ?? '우리 지역'} 맞춤` : '청년정책'}</span>
                <span className="pill">{dday(p)}</span>
                {p.category && <span>{p.category}</span>}
              </span>
              <h3>{p.name}</h3>
              <p>{p.support ?? p.summary ?? ''}</p>
            </button>
          ))}
        </div>
        {n > 1 && (
          <div className="dots" role="tablist" aria-label="정책 선택">
            {policies.map((p, idx) => (
              <button key={p.id} aria-label={`${idx + 1}번째 정책`} aria-current={idx === i} onClick={() => setI(idx)} />
            ))}
          </div>
        )}
      </div>

      {open && (
        <Sheet label={open.name} onClose={() => setOpen(null)}>
          <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
            {open.category && <span className="tag">{open.category}</span>}
            <span className={`tag${open.dDay !== null && open.dDay <= 7 ? ' warn' : ''}`}>{dday(open)}</span>
          </div>
          <h2 style={{ marginTop: 10 }}>{open.name}</h2>
          <dl className="kv">
            {open.summary && <div><dt>어떤 정책인가요</dt><dd>{open.summary}</dd></div>}
            {open.support && <div><dt>지원 내용</dt><dd>{open.support}</dd></div>}
            <div><dt>대상 연령</dt><dd>{open.ageText}</dd></div>
            <div><dt>신청 기간</dt><dd>{open.applyPeriod}</dd></div>
            {open.institution && <div><dt>운영 기관</dt><dd>{open.institution}</dd></div>}
          </dl>
          {open.applyUrl || open.refUrl ? (
            <a className="btn" style={{ display: 'block', textAlign: 'center' }} href={(open.applyUrl ?? open.refUrl)!} target="_blank" rel="noreferrer noopener">
              신청·자세히 보기
            </a>
          ) : (
            <p className="muted">신청 링크가 등록되지 않은 정책이에요. 운영 기관에 문의해 주세요.</p>
          )}
          <button className="btn ghost" style={{ marginTop: 8 }} onClick={() => setOpen(null)}>닫기</button>
        </Sheet>
      )}
    </>
  )
}
