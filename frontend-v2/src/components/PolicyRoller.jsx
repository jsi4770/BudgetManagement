import { useEffect, useRef, useState } from 'react';

const INTERVAL = 5000;

function dday(policy) {
  if (policy.dDay == null) return '상시';
  if (policy.dDay === 0) return '오늘 마감';
  return `D-${policy.dDay}`;
}

/** 홈 상단에서 맞춤 청년정책을 자동으로 넘겨 보여준다. 터치·호버 중에는 멈추고 좌우로 밀어 넘길 수 있다. */
export function PolicyRoller({ policies, regionName }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [open, setOpen] = useState(null);
  const startX = useRef(null);
  const moved = useRef(false);
  const count = policies.length;

  useEffect(() => {
    if (count < 2 || paused || open) return undefined;
    const timer = window.setInterval(() => setIndex((current) => (current + 1) % count), INTERVAL);
    return () => window.clearInterval(timer);
  }, [count, paused, open]);

  if (count === 0) {
    return (
      <div className="roller roller-empty t2">
        {regionName || '내 지역'}에서 지금 신청할 수 있는 청년정책을 찾고 있어요.
      </div>
    );
  }

  const step = (delta) => setIndex((current) => (current + delta + count) % count);

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
        onPointerDown={(event) => { startX.current = event.clientX; moved.current = false; setPaused(true); }}
        onPointerUp={(event) => {
          if (startX.current !== null) {
            const dx = event.clientX - startX.current;
            if (Math.abs(dx) > 40) { step(dx < 0 ? 1 : -1); moved.current = true; }
          }
          startX.current = null;
          setPaused(false);
        }}
        onPointerCancel={() => { startX.current = null; setPaused(false); }}
      >
        <div className="roller-track" style={{ transform: `translateX(-${index * 100}%)` }}>
          {policies.map((policy, i) => (
            <button
              type="button"
              key={policy.id}
              className="slide"
              aria-hidden={i !== index}
              tabIndex={i === index ? 0 : -1}
              onClick={() => { if (!moved.current) setOpen(policy); }}
            >
              <span className="kicker">
                <span className="pill">{policy.regionSpecific ? `${regionName || '우리 지역'} 맞춤` : '청년정책'}</span>
                <span className="pill">{dday(policy)}</span>
                {policy.category && <span>{policy.category}</span>}
              </span>
              <h3>{policy.name}</h3>
              <p>{policy.support || policy.summary || ''}</p>
            </button>
          ))}
        </div>
        {count > 1 && (
          <div className="dots">
            {policies.map((policy, i) => (
              <button type="button" key={policy.id} aria-label={`${i + 1}번째 정책`} aria-current={i === index} onClick={() => setIndex(i)} />
            ))}
          </div>
        )}
      </div>

      {open && (
        <div className="sheet-bg" onClick={() => setOpen(null)}>
          <div className="sheet policy-sheet" role="dialog" aria-label={open.name} onClick={(event) => event.stopPropagation()}>
            <div className="row" style={{ justifyContent: 'flex-start', flexWrap: 'wrap' }}>
              {open.category && <span className="chip">{open.category}</span>}
              <span className="chip">{dday(open)}</span>
            </div>
            <h2 className="h2" style={{ margin: '10px 0 12px' }}>{open.name}</h2>
            <dl className="kv">
              {open.summary && <div><dt>어떤 정책인가요</dt><dd>{open.summary}</dd></div>}
              {open.support && <div><dt>지원 내용</dt><dd>{open.support}</dd></div>}
              <div><dt>대상 연령</dt><dd>{open.ageText}</dd></div>
              <div><dt>신청 기간</dt><dd>{open.applyPeriod}</dd></div>
              {open.institution && <div><dt>운영 기관</dt><dd>{open.institution}</dd></div>}
            </dl>
            {open.applyUrl || open.refUrl ? (
              <a className="btn link-btn" href={open.applyUrl || open.refUrl} target="_blank" rel="noreferrer noopener">
                신청·자세히 보기
              </a>
            ) : (
              <p className="t2">신청 링크가 등록되지 않은 정책이에요. 운영 기관에 문의해 주세요.</p>
            )}
            <button type="button" className="btn sub" onClick={() => setOpen(null)}>닫기</button>
          </div>
        </div>
      )}
    </>
  );
}
