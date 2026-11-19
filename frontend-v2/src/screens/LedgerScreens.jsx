import { useEffect, useRef, useState } from 'react';
import heic2any from 'heic2any';
import { api } from '../api';
import { Hd, Keypad, Loading, Money } from '../components/common';
import {
  calendarCells, dayHeading, iconFor, monthEnd, monthLabel, shiftMonth, todayISO, yearMonth,
} from '../format';
import { Icon } from '../icons';
import { categoryLabel, usePrefs } from '../prefs';

function wonLabel(amount) {
  const value = Number(amount);
  if (!value) return '금액 없음';
  return `${value.toLocaleString('ko-KR')}원`;
}

function categoryName(categories, id, aliases) {
  const category = categories.find((item) => String(item.id) === String(id));
  return category ? categoryLabel(category, aliases) : '';
}

function groupByDate(items) {
  const groups = new Map();
  items.forEach((item) => {
    const date = item.transactionDate || '';
    if (!groups.has(date)) groups.set(date, []);
    groups.get(date).push(item);
  });
  return [...groups.entries()].sort((a, b) => b[0].localeCompare(a[0]));
}

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
  const { prefs } = usePrefs();

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
  const filtered = items.filter((item) => filter === '전체' || String(item.category?.id) === String(filter));
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
                  {sums[date]?.totalIncome > 0 && <small className="in">+{Math.round(sums[date].totalIncome / 1000) / 10}만</small>}
                  {sums[date]?.totalExpense > 0 && <small>-{Math.round(sums[date].totalExpense / 1000) / 10}만</small>}
                </button>
              ) : <div key={`empty-${index}`} />
            ))}
          </div>
        </div>
      ) : (
        <div className="chips">
          <button type="button" className={filter === '전체' ? 'on' : ''} onClick={() => setFilter('전체')}>전체</button>
          {categories.map((category) => (
            <button type="button" key={category.id} className={String(filter) === String(category.id) ? 'on' : ''} onClick={() => setFilter(category.id)}>
              {categoryLabel(category, prefs.aliases)}
            </button>
          ))}
        </div>
      )}
      {loading && <Loading />}
      {!loading && list.length === 0 && <div className="card t2 center" style={{ padding: 16 }}>내역이 없어요</div>}
      {!loading && groupByDate(list).map(([date, rows]) => (
        <div key={date}>
          <div className="date-head">{dayHeading(date)}</div>
          <div className="card">
            {view === 'cal' && date === selected && selected <= todayISO() && (
              <div className="row" style={{ marginBottom: 8 }}>
                <button type="button" className="chip" onClick={() => { c.setDraft({ type: 'EXPENSE', amount: '', date: selected, memo: '' }); c.go('S007'); }}>＋ 이 날짜에 입력</button>
              </div>
            )}
            {rows.map((item) => (
              <div className="tx" key={item.id}>
                <div className="ico"><Icon name={iconFor(item.category?.name)} /></div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 500 }}>{item.memo || categoryLabel(item.category, prefs.aliases)}</div>
                  <div className="t2">{categoryLabel(item.category, prefs.aliases)}</div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
                  <Money transaction={item} />
                  <button type="button" className="icon-btn" aria-label="수정" onClick={() => edit(item)}>
                    <Icon name="pencil" size={16} />
                  </button>
                  <button type="button" className="t2" onClick={() => remove(item.id)}>삭제</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
      <div className="pad" />
    </div>
  );
}

export function ManualEntry({ c }) {
  const draft = c.draft || { type: 'EXPENSE', amount: '', date: todayISO(), memo: '' };
  const { prefs } = usePrefs();
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
      else await api.createTransactions([body]);
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
      <div className="fld">
        <label>카테고리</label>
        <select
          className="input"
          value={draft.categoryId || ''}
          onChange={(event) => set({ ...draft, categoryId: Number(event.target.value) || null })}
        >
          <option value="">카테고리를 선택하세요</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>{categoryLabel(category, prefs.aliases)}</option>
          ))}
        </select>
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
    api.categories().then(setCategories).catch(() => {});
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
  const { prefs } = usePrefs();
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
          {categories.map((category) => <option key={category.id} value={category.id}>{categoryLabel(category, prefs.aliases)}</option>)}
        </select>
      </div>
      <div className="fld">
        <label>메모</label>
        <input className="input" maxLength={255} value={parsed.memo} onChange={(event) => setParsed({ ...parsed, memo: event.target.value })} />
      </div>
    </>
  );
}

function toTransactionBody(parsed) {
  return {
    categoryId: parsed.categoryId,
    amount: Number(parsed.amount),
    transactionDate: parsed.date,
    memo: parsed.memo?.trim() || null,
  };
}

async function saveParsed(items) {
  const list = Array.isArray(items) ? items : [items];
  if (list.length === 0) throw new Error('저장할 항목이 없어요.');
  if (list.some((item) => !item?.amount || !item.date || !item.categoryId)) {
    throw new Error('금액, 날짜, 카테고리를 채운 뒤 저장해주세요.');
  }
  await api.createTransactions(list.map(toTransactionBody));
}

export function ReceiptOcr({ c }) {
  const inputRef = useRef(null);
  const { prefs } = usePrefs();
  const [items, setItems] = useState([]);
  const [editing, setEditing] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.categories().then(setCategories).catch(() => {});
  }, []);

  const updateItem = (index, next) => {
    setItems((current) => current.map((item, itemIndex) => (itemIndex === index ? next : item)));
  };

  const upload = async (file) => {
    if (!file) return;
    setBusy(true);
    setError('');
    setItems([]);
    setEditing(null);
    try {
      const image = await prepareReceipt(file);
      const result = await api.ocrReceipt(image);
      const list = Array.isArray(result) ? result : [];
      if (list.length === 0) {
        const empty = new Error('영수증에서 거래를 찾지 못했어요.');
        empty.code = 'AI_PARSE_FAILED';
        throw empty;
      }
      setItems(list.map(toParsed));
    } catch (err) {
      setError(err.code === 'AI_PARSE_FAILED' || err.code === 'OCR_FAILED' || err.code === 'AI_UNAVAILABLE'
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
      await saveParsed(items);
      c.say(items.length > 1 ? `${items.length}건 저장했어요` : '저장했어요');
      c.go('S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="screen">
      <Hd title="영수증 촬영/캡처화면 추출" onBack={() => c.go('S005')} />
      <div className="card center">
        <div className="big"><Icon name="receipt-scan" size={48} /></div>
        <div className="t2">촬영 사진이나 캡처 화면을 올리면 위에서부터 여러 건을 꺼내요. jpg, png만 올릴 수 있고 아이폰 HEIC는 jpg로 바꿉니다.</div>
      </div>
      <input ref={inputRef} type="file" accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif" hidden onChange={(event) => upload(event.target.files?.[0])} />
      <button type="button" className="btn" disabled={busy} onClick={() => inputRef.current?.click()}>{busy ? '영수증을 읽는 중…' : '촬영 사진 및 캡처화면 올리기'}</button>
      {error && (
        <div className="err">
          {error}
          <br />
          <button type="button" style={{ color: 'var(--primary)', fontWeight: 700 }} onClick={() => { c.setDraft({ type: 'EXPENSE', amount: '', date: todayISO(), memo: '' }); c.go('S007'); }}>
            직접 입력으로 이동 ›
          </button>
        </div>
      )}
      {items.map((parsed, index) => (
        <div key={`${parsed.date}-${index}`}>
          <div className="date-head">{parsed.date ? dayHeading(parsed.date) : '날짜 없음'}</div>
          <div className="card">
            <div className="row">
              <b>{wonLabel(parsed.amount)}</b>
              <div style={{ display: 'flex', gap: 8 }}>
                {editing !== index && (
                  <button type="button" className="icon-btn" aria-label="수정" onClick={() => setEditing(index)}>
                    <Icon name="pencil" size={16} />
                  </button>
                )}
                <button type="button" className="t2" onClick={() => setItems((current) => current.filter((_, itemIndex) => itemIndex !== index))}>빼기</button>
              </div>
            </div>
            {editing === index ? (
              <ParsedFields parsed={parsed} setParsed={(next) => updateItem(index, next)} categories={categories} />
            ) : (
              <>
                <div className="t2" style={{ marginTop: 8 }}>{categoryName(categories, parsed.categoryId, prefs.aliases) || '카테고리 없음'}</div>
                <div style={{ marginTop: 4 }}>{parsed.memo || '메모 없음'}</div>
              </>
            )}
          </div>
        </div>
      ))}
      {items.length > 0 && (
        <button type="button" className="btn" disabled={busy} onClick={save}>
          {busy ? '저장 중…' : `확인하고 ${items.length}건 저장`}
        </button>
      )}
    </div>
  );
}

export function CategoryTransactions({ c }) {
  const { prefs } = usePrefs();
  const category = c.categoryFocus;
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!category?.id) return undefined;
    let cancel = false;
    api.transactions({ categoryId: category.id, from: `${yearMonth()}-01`, to: todayISO(), size: 100 })
      .then((page) => { if (!cancel) setItems(page?.items || []); })
      .catch((err) => { if (!cancel) { setError(err.message); setItems([]); } });
    return () => { cancel = true; };
  }, [category?.id]);

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
      <Hd title={categoryLabel(category, prefs.aliases) || '소비 내역'} onBack={() => c.go(c.categoryBack || 'S015')} />
      <div className="t2" style={{ marginBottom: 8 }}>{monthLabel(yearMonth())} 소비 내역</div>
      {error && <div className="err">{error}</div>}
      {items == null && !error && <Loading />}
      {items && items.length === 0 && <div className="card t2 center">이 카테고리의 소비 내역이 없어요</div>}
      {groupByDate(items || []).map(([date, rows]) => (
        <div key={date}>
          <div className="date-head">{dayHeading(date)}</div>
          <div className="card">
            {rows.map((item) => (
              <div className="tx" key={item.id}>
                <div className="ico"><Icon name={iconFor(item.category?.name)} /></div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 500 }}>{item.memo || categoryLabel(item.category, prefs.aliases)}</div>
                  <div className="t2">{categoryLabel(item.category, prefs.aliases)}</div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
                  <Money transaction={item} />
                  <button type="button" className="icon-btn" aria-label="수정" onClick={() => edit(item)}>
                    <Icon name="pencil" size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
      <div className="pad" />
    </div>
  );
}
