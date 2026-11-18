import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { errMsg } from '../hooks'

export default function Login() {
  const { login } = useAuth()
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(email.trim(), password)
      nav('/', { replace: true })
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <h1>넛지 가계부</h1>
      <p className="sub">소비 습관을 함께 돌아보고, 우리 동네 혜택까지 챙겨 드려요.</p>
      <form className="stack" onSubmit={submit} style={{ marginTop: 28 }}>
        <div className="field"><label htmlFor="email">이메일</label>
          <input id="email" className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></div>
        <div className="field"><label htmlFor="pw">비밀번호</label>
          <input id="pw" className="input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></div>
        <button className="btn" disabled={busy || !email || !password}>{busy ? '로그인 중…' : '로그인'}</button>
        <p className="error" role="alert">{error}</p>
      </form>
      <p className="sub" style={{ textAlign: 'center' }}>처음이세요? <Link to="/signup">회원가입</Link></p>
    </div>
  )
}
