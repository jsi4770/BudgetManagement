import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { errMsg } from '../hooks'

const PW_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$/

export default function Signup() {
  const { signup } = useAuth()
  const nav = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const pwOk = PW_RULE.test(password)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await signup(username.trim(), email.trim(), password)
      nav('/onboarding', { replace: true })
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <h1>회원가입</h1>
      <p className="sub">가입 후 사는 지역과 나이를 한 번만 알려 주세요.</p>
      <form className="stack" onSubmit={submit} style={{ marginTop: 24 }}>
        <div className="field"><label htmlFor="name">이름(닉네임)</label>
          <input id="name" className="input" maxLength={50} autoComplete="nickname" value={username} onChange={(e) => setUsername(e.target.value)} required /></div>
        <div className="field"><label htmlFor="email">이메일</label>
          <input id="email" className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></div>
        <div className="field"><label htmlFor="pw">비밀번호</label>
          <input id="pw" className="input" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={password !== '' && !pwOk} required />
          <span className={password && !pwOk ? 'error' : 'sub'}>8자 이상, 영문·숫자·특수문자를 모두 포함해 주세요.</span></div>
        <button className="btn" disabled={busy || !username.trim() || !email || !pwOk}>{busy ? '가입 중…' : '가입하기'}</button>
        <p className="error" role="alert">{error}</p>
      </form>
      <p className="sub" style={{ textAlign: 'center' }}>이미 계정이 있어요 <Link to="/login">로그인</Link></p>
    </div>
  )
}
