import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import ProfileForm from '../components/ProfileForm'

/** 가입 직후 한 번: 사는 지역·나이·성별. 이 값으로 우리 동네 가맹점과 맞춤 청년정책을 보여 준다. */
export default function Onboarding() {
  const { me } = useAuth()
  const nav = useNavigate()
  return (
    <div className="auth-wrap">
      <h1>{me?.username ?? ''}님, 몇 가지만 알려 주세요</h1>
      <p className="sub">내 지역의 지역화폐 가맹점과 나에게 맞는 청년정책을 보여 드리기 위해서예요. 다른 용도로 쓰지 않아요.</p>
      <div style={{ marginTop: 20 }}>
        <ProfileForm submitLabel="시작하기" onDone={() => nav('/', { replace: true })} />
      </div>
    </div>
  )
}
