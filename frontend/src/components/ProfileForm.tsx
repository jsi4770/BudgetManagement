import { useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import { errMsg, useLoad } from '../hooks'
import type { Me, Region } from '../types'

const GENDERS = [
  { v: 'FEMALE', label: '여성' },
  { v: 'MALE', label: '남성' },
  { v: 'NONE', label: '선택 안 함' },
] as const

/** 지역·나이·성별을 받는 폼. 가입 직후 온보딩과 마이페이지 수정이 같이 쓴다. */
export default function ProfileForm({ submitLabel, onDone }: { submitLabel: string; onDone: () => void }) {
  const { me, setMe } = useAuth()
  const regions = useLoad(() => api<Region[]>('/regions'), [])
  const [region, setRegion] = useState<string | null>(me?.regionCode ?? null)
  const [age, setAge] = useState<string>(me?.age ? String(me.age) : '')
  const [gender, setGender] = useState<string | null>(me?.gender ?? null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const ageNum = Number(age)
  const ageOk = Number.isInteger(ageNum) && ageNum >= 14 && ageNum <= 99
  const ready = !!region && ageOk && !!gender

  async function submit() {
    setBusy(true)
    setError('')
    try {
      const updated = await api<Me>('/users/me/onboarding', { method: 'PUT', body: { regionCode: region, age: ageNum, gender } })
      setMe(updated)
      onDone()
    } catch (e) {
      setError(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <h2>사는 지역 (충남)</h2>
        {regions.loading && <p className="muted">불러오는 중…</p>}
        {regions.error && <p className="error">{regions.error} <button className="link-btn" onClick={regions.reload}>다시 시도</button></p>}
        <div className="grid3" role="group" aria-label="사는 지역">
          {regions.data?.map((r) => (
            <button
              key={r.code}
              type="button"
              className="opt"
              disabled={!r.selectable}
              aria-pressed={region === r.code}
              onClick={() => setRegion(r.code)}
            >
              {r.name}
              {!r.selectable && <small>준비 중</small>}
            </button>
          ))}
        </div>
        <p className="sub" style={{ marginBottom: 0 }}>지금은 천안·아산만 선택할 수 있어요. 다른 지역은 곧 열릴 예정이에요.</p>
      </section>

      <section className="card">
        <h2>만 나이</h2>
        <input
          className="input"
          type="number"
          inputMode="numeric"
          min={14}
          max={99}
          placeholder="예: 27"
          value={age}
          onChange={(e) => setAge(e.target.value)}
          aria-invalid={age !== '' && !ageOk}
        />
        {age !== '' && !ageOk && <p className="error">14~99 사이의 숫자로 입력해 주세요.</p>}
      </section>

      <section className="card">
        <h2>성별</h2>
        <div className="grid3" role="group" aria-label="성별">
          {GENDERS.map((g) => (
            <button key={g.v} type="button" className="opt" aria-pressed={gender === g.v} onClick={() => setGender(g.v)}>
              {g.label}
            </button>
          ))}
        </div>
        <p className="sub" style={{ marginBottom: 0 }}>밝히고 싶지 않다면 "선택 안 함"을 눌러 주세요.</p>
      </section>

      <button className="btn" disabled={!ready || busy} onClick={submit}>{busy ? '저장 중…' : submitLabel}</button>
      <p className="error" role="alert">{error}</p>
    </div>
  )
}
