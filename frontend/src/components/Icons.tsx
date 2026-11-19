import aiCoach from '../assets/icons/ai-coach.png'
import cafe from '../assets/icons/cafe.png'
import category from '../assets/icons/category.png'
import delivery from '../assets/icons/delivery.png'
import pig from '../assets/icons/dont-worry-pig.png'
import gift from '../assets/icons/gift.png'
import home from '../assets/icons/home.png'
import ledger from '../assets/icons/ledger.png'
import location from '../assets/icons/location.png'
import my from '../assets/icons/my.png'
import rent from '../assets/icons/rent.png'
import shopping from '../assets/icons/shopping.png'
import transport from '../assets/icons/transport.png'
import wallet from '../assets/icons/wallet.png'

const SRC: Record<string, string> = {
  home, ledger, 'ai-coach': aiCoach, location, my,
  shopping, delivery, cafe, transport, rent, wallet, gift, category,
}

const CATEGORY: Record<string, string> = {
  식비: 'shopping',
  배달: 'delivery',
  카페: 'cafe',
  교통: 'transport',
  월세: 'rent',
  급여: 'wallet',
  용돈: 'gift',
  기타: 'category',
}

export function Icon({ name, size = 22 }: { name: string; size?: number }) {
  const src = SRC[name] ?? category
  return <img src={src} width={size} height={size} alt="" style={{ width: size, height: size, display: 'block', flex: 'none', objectFit: 'contain' }} />
}

export function CategoryMark({ name }: { name: string }) {
  return (
    <span className="cat-ico">
      <Icon name={CATEGORY[name] ?? 'category'} size={18} />
    </span>
  )
}

export function PigLogo({ size = 96 }: { size?: number }) {
  return <img className="pig" src={pig} width={size} height={size} alt="" />
}

export const HomeIcon = () => <Icon name="home" />
export const ListIcon = () => <Icon name="ledger" />
export const ChatIcon = () => <Icon name="ai-coach" />
export const StoreIcon = () => <Icon name="location" />
export const UserIcon = () => <Icon name="my" />
