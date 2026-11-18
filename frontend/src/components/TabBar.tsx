import { NavLink } from 'react-router-dom'
import { ChatIcon, HomeIcon, ListIcon, StoreIcon, UserIcon } from './Icons'

const tabs = [
  { to: '/', label: '홈', icon: <HomeIcon />, end: true },
  { to: '/transactions', label: '내역', icon: <ListIcon /> },
  { to: '/coaching', label: '코칭', icon: <ChatIcon /> },
  { to: '/merchants', label: '가맹점', icon: <StoreIcon /> },
  { to: '/me', label: '마이', icon: <UserIcon /> },
]

export default function TabBar() {
  return (
    <div className="tabbar">
      <nav aria-label="주요 메뉴">
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end} className={({ isActive }) => `tab${isActive ? ' active' : ''}`}>
            {t.icon}
            {t.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
