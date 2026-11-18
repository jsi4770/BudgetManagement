import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import TabBar from './components/TabBar'
import Coaching from './pages/Coaching'
import CoachingStart from './pages/CoachingStart'
import Home from './pages/Home'
import Login from './pages/Login'
import Me from './pages/Me'
import Merchants from './pages/Merchants'
import Onboarding from './pages/Onboarding'
import Signup from './pages/Signup'
import Transactions from './pages/Transactions'

/** 로그인과 온보딩 완료 여부로 갈 곳을 정한다. 앱 본문에는 하단 탭을 붙인다. */
function Protected() {
  const { ready, me } = useAuth()
  if (!ready) return <div className="center-screen">불러오는 중…</div>
  if (!me) return <Navigate to="/login" replace />
  if (!me.onboardingCompleted) return <Navigate to="/onboarding" replace />
  return (
    <>
      <Outlet />
      <TabBar />
    </>
  )
}

/** 로그인 전 화면. 이미 로그인했으면 홈으로 보낸다. */
function Guest() {
  const { ready, me } = useAuth()
  if (!ready) return <div className="center-screen">불러오는 중…</div>
  if (me) return <Navigate to={me.onboardingCompleted ? '/' : '/onboarding'} replace />
  return <Outlet />
}

function OnboardingGate() {
  const { ready, me } = useAuth()
  if (!ready) return <div className="center-screen">불러오는 중…</div>
  if (!me) return <Navigate to="/login" replace />
  if (me.onboardingCompleted) return <Navigate to="/" replace />
  return <Onboarding />
}

export default function App() {
  return (
    <Routes>
      <Route element={<Guest />}>
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
      </Route>
      <Route path="/onboarding" element={<OnboardingGate />} />
      <Route element={<Protected />}>
        <Route path="/" element={<Home />} />
        <Route path="/transactions" element={<Transactions />} />
        <Route path="/coaching" element={<CoachingStart />} />
        <Route path="/coaching/:id" element={<Coaching />} />
        <Route path="/merchants" element={<Merchants />} />
        <Route path="/me" element={<Me />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
