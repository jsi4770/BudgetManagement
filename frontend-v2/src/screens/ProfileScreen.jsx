import { useEffect, useState } from 'react';
import { api } from '../api';
import { Loading } from '../components/common';

const GENDERS = [['FEMALE', '여성'], ['MALE', '남성'], ['NONE', '선택 안 함']];

/** 사는 지역·만 나이·성별을 받는다. 맞춤 청년정책과 우리 동네 가맹점이 이 값을 기준으로 나온다. */
export function Profile({ c }) {
  const [regions, setRegions] = useState(null);
  const [region, setRegion] = useState(c.user?.regionCode ?? null);
  const [age, setAge] = useState(c.user?.age ? String(c.user.age) : '');
  const [gender, setGender] = useState(c.user?.gender ?? null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancel = false;
    api.regions()
      .then((list) => { if (!cancel) setRegions(list || []); })
      .catch((err) => { if (!cancel) { setRegions([]); setError(err.message); } });
    return () => { cancel = true; };
  }, []);

  const ageNumber = Number(age);
  const ageOk = Number.isInteger(ageNumber) && ageNumber >= 14 && ageNumber <= 99;
  const ready = Boolean(region) && ageOk && Boolean(gender);

  const submit = async () => {
    setBusy(true);
    setError('');
    try {
      const updated = await api.completeOnboarding({ regionCode: region, age: ageNumber, gender });
      c.setUser(updated);
      c.go(c.afterProfile || 'S005');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="screen">
      <h1 className="h1" style={{ margin: '2px 0 6px' }}>내 정보를 알려주세요</h1>
      <p className="t2" style={{ marginBottom: 16 }}>우리 동네 청년정책과 지역화폐 가맹점을 골라드릴게요.</p>

      <div className="card">
        <h2 className="h2" style={{ marginBottom: 12 }}>사는 지역 (충남)</h2>
        {regions === null && <Loading />}
        <div className="grid3" role="group" aria-label="사는 지역">
          {(regions || []).map((item) => (
            <button
              type="button"
              key={item.code}
              className={`opt${region === item.code ? ' on' : ''}`}
              disabled={!item.selectable}
              aria-pressed={region === item.code}
              onClick={() => setRegion(item.code)}
            >
              <span>{item.name}{!item.selectable && <small className="t2" style={{ display: 'block' }}>준비 중</small>}</span>
            </button>
          ))}
        </div>
        <div className="t2" style={{ marginTop: 10 }}>지금은 천안·아산만 선택할 수 있어요.</div>
      </div>

      <div className="card">
        <h2 className="h2" style={{ marginBottom: 12 }}>만 나이</h2>
        <input
          className="input"
          type="number"
          inputMode="numeric"
          min={14}
          max={99}
          placeholder="예: 27"
          value={age}
          onChange={(event) => setAge(event.target.value)}
          aria-invalid={age !== '' && !ageOk}
        />
        {age !== '' && !ageOk && <div className="err">14~99 사이의 숫자로 입력해 주세요.</div>}
      </div>

      <div className="card">
        <h2 className="h2" style={{ marginBottom: 12 }}>성별</h2>
        <div className="grid3" role="group" aria-label="성별">
          {GENDERS.map(([value, label]) => (
            <button
              type="button"
              key={value}
              className={`opt${gender === value ? ' on' : ''}`}
              aria-pressed={gender === value}
              onClick={() => setGender(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {error && <div className="err" role="alert">{error}</div>}
      <button type="button" className="btn" disabled={!ready || busy} onClick={submit}>{busy ? '저장 중…' : '시작하기'}</button>
      <div className="pad" />
    </div>
  );
}
