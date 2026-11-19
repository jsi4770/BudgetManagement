import { useEffect, useState } from 'react';
import { api } from '../api';
import { Hd, Loading } from '../components/common';

const GROUPS = [
  ['', '전체'], ['FOOD', '식당·주점'], ['CAFE', '카페'], ['GROCERY', '마트·편의점'],
  ['SHOPPING', '쇼핑'], ['TRANSPORT', '교통·주유'], ['HEALTH', '병원·약국'],
  ['EDU', '교육·학원'], ['LEISURE', '문화·여가'], ['LIFE', '생활서비스'],
];

export function Merchants({ c }) {
  const [group, setGroup] = useState('');
  const [text, setText] = useState('');
  const [keyword, setKeyword] = useState('');
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const timer = window.setTimeout(() => setKeyword(text.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [text]);

  const load = async (nextPage, replace) => {
    setLoading(true);
    setError('');
    try {
      const result = await api.localMerchants({ group, q: keyword, page: nextPage, size: 20 });
      setItems((current) => (replace ? result.items : [...current, ...result.items]));
      setPage(result.page);
      setTotal(result.totalCount);
      setHasNext(result.hasNext);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(0, true); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [group, keyword]);

  const baseDate = items.find((item) => item.baseDate)?.baseDate;

  return (
    <div className="screen">
      <Hd title="우리 동네 가맹점" onBack={() => c.go('S015')} />
      <div className="t2" style={{ marginBottom: 16 }}>
        {c.user?.regionName ? `${c.user.regionName} 지역화폐 사용처` : '지역화폐 사용처'}
      </div>

      <input
        className="input"
        type="search"
        placeholder="가맹점 이름 검색"
        aria-label="가맹점 이름 검색"
        value={text}
        onChange={(event) => setText(event.target.value)}
      />
      <div className="chips" role="group" aria-label="업종" style={{ marginTop: 12 }}>
        {GROUPS.map(([value, label]) => (
          <button type="button" key={value || 'all'} className={group === value ? 'on' : ''} onClick={() => setGroup(value)}>
            {label}
          </button>
        ))}
      </div>

      {!error && !(loading && items.length === 0) && (
        <div className="t2" style={{ margin: '0 0 12px' }}>
          총 {total.toLocaleString('ko-KR')}곳{baseDate ? ` · 기준일 ${baseDate}` : ''}
        </div>
      )}

      {items.map((item) => (
        <div className="card merchant" key={item.id}>
          <div className="row">
            <b>{item.name}</b>
            <span className="chip">{item.groupLabel}</span>
          </div>
          {item.address && <div className="t2" style={{ marginTop: 6 }}>{item.address}</div>}
          {item.phone && <a className="t2 tel" href={`tel:${item.phone}`}>{item.phone}</a>}
        </div>
      ))}

      {loading && <Loading />}
      {error && (
        <div className="err">
          {error} <button type="button" className="t2" style={{ textDecoration: 'underline' }} onClick={() => load(0, true)}>다시 시도</button>
        </div>
      )}
      {!loading && !error && items.length === 0 && (
        <div className="t2 center" style={{ padding: 32 }}>조건에 맞는 가맹점이 없어요</div>
      )}
      {hasNext && !loading && (
        <button type="button" className="btn sub" onClick={() => load(page + 1, false)}>더 보기</button>
      )}
      {items.length > 0 && <div className="t2 center" style={{ marginTop: 8 }}>실제 사용 가능 여부는 매장에 확인해 주세요.</div>}
      <div className="pad" />
    </div>
  );
}
