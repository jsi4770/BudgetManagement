import { useEffect, useRef, useState } from 'react';
import heic2any from 'heic2any';
import { api } from '../api';
import { Hd, Keypad, Loading, Money } from '../components/common';
import {
  calendarCells, iconFor, monthEnd, monthLabel, shiftMonth, shortDate, todayISO, yearMonth,
} from '../format';

export function Ledger({ c }) {
  const [view, setView] = useState('cal');
  const [ym, setYm] = useState(yearMonth());
  const [selected, setSelected] = useState(todayISO());
  const [filter, setFilter] = useState('전체');
  const [days, setDays] = useState([]);
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancel = false;
    (async () => {
      setLoading(true);
      setError('');
      try {
        const end = monthEnd(ym);
        const to = end > todayISO() && ym === yearMonth() ? todayISO() : end;
        const [calendar, page, expense] = await Promise.all([
          api.calendar(ym),
          api.transactions({ from: `${ym}-01`, to, size: 100 }),
          api.categories('EXPENSE'),
        ]);
        if (cancel) return;
        setDays(calendar?.days || []);
        setItems(page?.items || []);
        setCategories(expense || []);
      } catch (err) {
        if (!cancel) setError(err.message);
      } finally {
        if (!cancel) setLoading(false);
      }
    })();
    return () => { cancel = true; };
  }, [ym]);

  const sums = Object.fromEntries(days.map((day) => [day.date, day]));
  const selectedItems = items.filter((item) => item.transactionDate === selected);
  const filtered = items.filter((item) => filter === '전체' || item.category?.name === filter);
  const list = view === 'cal' ? selectedItems : filtered;
  const current = yearMonth();

  const remove = async (id) => {
    try {
      await api.deleteTransaction(id);
      setItems((currentItems) => currentItems.filter((item) => item.id !== id));
      c.say('삭제했어요');
    } catch (err) {
      c.say(err.message);
    }
  };

  const edit = (item) => {
    c.setDraft({
      id: item.id,
      type: item.category?.type === 'INCOME' ? 'INCOME' : 'EXPENSE',
      categoryId: item.category?.id,
      amount: String(item.amount),
      date: item.transactionDate,
      memo: item.memo || '',
    });
    c.go('S007');
  };

  return (
    <div className="screen">
      <h1 className="h1" style={{ marginBottom: 16 }}>가계부</h1>
      <div className="tabs">
        <button type="button" className={view === 'cal' ? 'on' : ''} onClick={() => setView('cal')}>달력</button>
        <button type="button" className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>목록</button>
      </div>
      {error && <div className="err">{error}</div>}
      {view === 'cal' ? (
        <div className="card">
          <div className="row" style={{ marginBottom: 8 }}>
            <button type="button" onClick={() => setYm(shiftMonth(ym, -1))}>‹</button>
            <b>{monthLabel(ym)}</b>
            <button type="button" disabled={ym >= current} onClick={() => setYm(shiftMonth(ym, 1))}>›</button>
          </div>
          <div className="cal t2">{'일월화수목금토'.split('').map((day) => <div key={day}>{day}</div>)}</div>
          <div className="cal">
            {calendarCells(ym).map((date, index) => (
              date ? (
                <button type="button" key={date} className={selected === date ? 'sel' : ''} onClick={() => setSelected(date)}>
                  {Number(date.slice(-2))}
                  {sums[date]?.totalExpense > 0 && <small>-{Math.round(sums[date].totalExpense / 1000) / 10}만</small>}
                </button>
              ) : <div key={`empty-${index}`} />
            ))}
          </div>
        </div>
      ) : (
        <div className="chips">
          {['전체', ...categories.map((category) => category.name)].map((name) => (
            <button type="button" key={name} className={filter === name ? 'on' : ''} onClick={() => setFilter(name)}>{name}</button>
          ))}
        </div>
      )}
      <div className="card">
        {view === 'cal' && (
          <div className="row">
            <b>{monthLabel(ym)} {Number(selected.slice(-2))}일</b>
            {selected <= todayISO() && (
              <button type="button" className="chip" onClick={() => { c.setDraft({ type: 'EXPENSE', amount: '', date: selected, memo: '' }); c.go('S007'); }}>＋ 이 날짜에 입력</button>
            )}
          </div>
        )}
        {loading && <Loading />}
        {!loading && list.length === 0 && <div className="t2 center" style={{ padding: 16 }}>내역이 없어요</div>}
        {list.map((item) => (
          <div className="tx" key={item.id}>
            <div className="ico">{iconFor(item.category?.name)}</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 500 }}>{item.memo || item.category?.name}</div>
              <div className="t2">{item.category?.name} · {shortDate(item.transactionDate)}</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <Money transaction={item} />
              <div className="t2">
                <button type="button" onClick={() => edit(item)}>수정</button>
                {' · '}
                <button type="button" onClick={() => remove(item.id)}>삭제</button>
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="pad" />
    </div>
  );
}

export function ManualEntry({ c }) {
  const draft = c.draft || { type: 'EXPENSE', amount: '', date: todayISO(), memo: '' };
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (next) => c.setDraft(next);

  useEffect(() => {
    let cancel = false;
    api.categories(draft.type).then((list) => {
      if (cancel) return;
      setCategories(list || []);
      c.setDraft((current) => {
        const base = current || { type: draft.type, amount: '', date: todayISO(), memo: '' };
        if (base.type !== draft.type || base.categoryId) return base;
        return { ...base, categoryId: list?.[0]?.id };
      });
    }).catch((err) => { if (!cancel) setError(err.message); });
    return () => { cancel = true; };
  }, [draft.type]);

  const save = async () => {
    const body = {
      categoryId: draft.categoryId,
      amount: Number(draft.amount),
      transactionDate: draft.date,
      memo: draft.memo?.trim() || null,
    };
    if (!body.categoryId || !body.amount || !body.transactionDate) {
      setError('금액, 날짜, 카테고리를 확인해주세요.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      if (draft.id) await api.updateTransaction(draft.id, body);
      else await api.createTransaction(body);
      c.say(draft.id ? '수정했어요' : '저장했어요');
      c.setDraft(null);
      c.go('S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="screen">
      <Hd title={draft.id ? '거래 수정' : '직접 입력'} onBack={() => c.go('S005')} />
      <div className="tabs">
        <button type="button" className={draft.type === 'EXPENSE' ? 'on' : ''} onClick={() => set({ ...draft, type: 'EXPENSE', categoryId: null })}>지출</button>
        <button type="button" className={draft.type === 'INCOME' ? 'on' : ''} onClick={() => set({ ...draft, type: 'INCOME', categoryId: null })}>수입</button>
      </div>
      <div className="center">
        <div className="t2">금액</div>
        <div className="amount" style={{ color: draft.type === 'INCOME' ? 'var(--success)' : 'var(--text)' }}>
          {draft.amount ? Number(draft.amount).toLocaleString('ko-KR') : '0'}원
        </div>
      </div>
      <Keypad value={draft.amount || ''} onChange={(amount) => set({ ...draft, amount })} />
      <div className="chips">
        {categories.map((category) => (
          <button type="button" key={category.id} className={draft.categoryId === category.id ? 'on' : ''} onClick={() => set({ ...draft, categoryId: category.id })}>
            {iconFor(category.name)} {category.name}
          </button>
        ))}
      </div>
      <div className="row">
        <input className="input" type="date" max={todayISO()} value={draft.date || ''} onChange={(event) => set({ ...draft, date: event.target.value })} />
        <input className="input" placeholder="메모" maxLength={255} value={draft.memo || ''} onChange={(event) => set({ ...draft, memo: event.target.value })} />
      </div>
      {error && <div className="err">{error}</div>}
      <button type="button" className="btn" disabled={!Number(draft.amount) || busy} onClick={save}>저장</button>
    </div>
  );
}

export function AiInput({ c }) {
  const [text, setText] = useState('');
  const [parsed, setParsed] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.categories('EXPENSE').then(setCategories).catch(() => {});
  }, []);

  const analyze = async () => {
    if (!text.trim()) return setError('한 줄로 지출을 적어주세요.');
    setBusy(true);
    setError('');
    setParsed(null);
    try {
      setParsed(toParsed(await api.parseTransaction(text.trim())));
    } catch (err) {
      setError(err.code === 'AI_PARSE_FAILED' || err.code === 'AI_UNAVAILABLE'
        ? `${err.message} 직접 입력으로 기록할 수 있어요.`
        : err.message);
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    setBusy(true);
    setError('');
    try {
      await saveParsed(parsed);
      c.say('저장했어요');
      c.go('S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="screen">
      <Hd title="한 줄 입력" onBack={() => c.go('S005')} />
      <div className="t2" style={{ marginBottom: 8 }}>말하듯이 적어보세요. AI가 날짜·금액·카테고리를 정리해요.</div>
      <textarea className="input" maxLength={200} value={text} onChange={(event) => { setText(event.target.value); setParsed(null); }} />
      {error && (
        <div className="err">
          {error}
          <br />
          <button type="button" style={{ color: 'var(--primary)', fontWeight: 700 }} onClick={() => { c.setDraft({ type: 'EXPENSE', amount: '', date: todayISO(), memo: '' }); c.go('S007'); }}>
            직접 입력으로 이동 ›
          </button>
        </div>
      )}
      <button type="button" className="btn" disabled={busy} onClick={analyze}>{busy ? '분석 중…' : 'AI로 분석하기'}</button>
      {parsed && (
        <div className="card" style={{ marginTop: 16 }}>
          <span className="chip">AI 분석 결과 · 확인 후 수정해주세요</span>
          <ParsedFields parsed={parsed} setParsed={setParsed} categories={categories} />
          <button type="button" className="btn" disabled={busy} onClick={save}>확인하고 저장</button>
        </div>
      )}
    </div>
  );
}

const MAX_IMAGE = 10 * 1024 * 1024;

async function prepareReceipt(file) {
  const name = file.name.toLowerCase();
  const heic = file.type === 'image/heic' || file.type === 'image/heif' || name.endsWith('.heic') || name.endsWith('.heif');
  let image = file;
  if (heic) {
    const converted = await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.9 });
    const blob = Array.isArray(converted) ? converted[0] : converted;
    image = new File([blob], file.name.replace(/\.hei[cf]$/i, '.jpg'), { type: 'image/jpeg' });
  }
  const type = image.type;
  const imageName = image.name.toLowerCase();
  const allowed = type === 'image/jpeg' || type === 'image/png' || imageName.endsWith('.jpg') || imageName.endsWith('.jpeg') || imageName.endsWith('.png');
  if (!allowed) {
    const error = new Error('jpg 또는 png만 올릴 수 있어요.');
    error.code = 'UNSUPPORTED_FILE_TYPE';
    throw error;
  }
  if (image.size > MAX_IMAGE) {
    const error = new Error('이미지는 10MB 이하만 올릴 수 있어요.');
    error.code = 'FILE_TOO_LARGE';
    throw error;
  }
  return image;
}

function toParsed(result) {
  return {
    amount: result.amount ? String(result.amount) : '',
    date: result.transactionDate || '',
    categoryId: result.category?.id || null,
    memo: result.memo || '',
    missing: result.missingFields || [],
  };
}

function ParsedFields({ parsed, setParsed, categories }) {
  return (
    <>
      {parsed.missing.length > 0 && <div className="t2" style={{ marginTop: 8 }}>비어 있는 항목: {parsed.missing.join(', ')}</div>}
      <div className="fld" style={{ marginTop: 14 }}>
        <label>금액(원)</label>
        <input className="input" inputMode="numeric" value={parsed.amount} onChange={(event) => setParsed({ ...parsed, amount: event.target.value.replace(/\D/g, '') })} />
      </div>
      <div className="fld">
        <label>날짜</label>
        <input className="input" type="date" max={todayISO()} value={parsed.date} onChange={(event) => setParsed({ ...parsed, date: event.target.value })} />
      </div>
      <div className="fld">
        <label>카테고리</label>
        <select className="input" value={parsed.categoryId || ''} onChange={(event) => setParsed({ ...parsed, categoryId: Number(event.target.value) })}>
          <option value="">선택</option>
          {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select>
      </div>
      <div className="fld">
        <label>메모</label>
        <input className="input" maxLength={255} value={parsed.memo} onChange={(event) => setParsed({ ...parsed, memo: event.target.value })} />
      </div>
    </>
  );
}

async function saveParsed(parsed) {
  if (!parsed?.amount || !parsed.date || !parsed.categoryId) {
    throw new Error('금액, 날짜, 카테고리를 채운 뒤 저장해주세요.');
  }
  await api.createTransaction({
    categoryId: parsed.categoryId,
    amount: Number(parsed.amount),
    transactionDate: parsed.date,
    memo: parsed.memo?.trim() || null,
  });
}

export function ReceiptOcr({ c }) {
  const inputRef = useRef(null);
  const [parsed, setParsed] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.categories('EXPENSE').then(setCategories).catch(() => {});
  }, []);

  const upload = async (file) => {
    if (!file) return;
    setBusy(true);
    setError('');
    setParsed(null);
    try {
      const image = await prepareReceipt(file);
      const result = await api.ocrReceipt(image);
      setParsed(toParsed(result));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    setBusy(true);
    setError('');
    try {
      await saveParsed(parsed);
      c.say('저장했어요');
      c.go('S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="screen">
      <Hd title="영수증 입력" onBack={() => c.go('S005')} />
      <div className="card center">
        <div className="big">🧾</div>
        <div className="t2">jpg, png만 올릴 수 있어요. 아이폰 HEIC는 jpg로 바꾼 뒤 분석합니다.</div>
      </div>
      <input ref={inputRef} type="file" accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif" hidden onChange={(event) => upload(event.target.files?.[0])} />
      <button type="button" className="btn" disabled={busy} onClick={() => inputRef.current?.click()}>{busy ? '영수증을 읽는 중…' : '영수증 사진 올리기'}</button>
      {error && (
        <div className="err">
          {error}
          <br />
          <button type="button" style={{ color: 'var(--primary)', fontWeight: 700 }} onClick={() => { c.setDraft({ type: 'EXPENSE', amount: '', date: todayISO(), memo: '' }); c.go('S007'); }}>
            직접 입력으로 이동 ›
          </button>
        </div>
      )}
      {parsed && (
        <div className="card" style={{ marginTop: 16 }}>
          <span className="chip">OCR 추출 결과 · 틀린 곳은 수정해주세요</span>
          <ParsedFields parsed={parsed} setParsed={setParsed} categories={categories} />
          <button type="button" className="btn" disabled={busy} onClick={save}>확인하고 저장</button>
        </div>
      )}
    </div>
  );
}
