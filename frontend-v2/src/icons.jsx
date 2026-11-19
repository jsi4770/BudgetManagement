import aiCoach from './assets/icons/ai-coach.png';
import analytics from './assets/icons/analytics.png';
import barChart from './assets/icons/bar-chart.png';
import bell from './assets/icons/bell.png';
import cafe from './assets/icons/cafe.png';
import calendar from './assets/icons/calendar.png';
import card from './assets/icons/card.png';
import category from './assets/icons/category.png';
import challenge from './assets/icons/challenge.png';
import coins from './assets/icons/coins.png';
import delivery from './assets/icons/delivery.png';
import pig from './assets/icons/dont-worry-pig.png';
import error from './assets/icons/error.png';
import gift from './assets/icons/gift.png';
import goal from './assets/icons/goal.png';
import help from './assets/icons/help.png';
import home from './assets/icons/home.png';
import info from './assets/icons/info.png';
import ledger from './assets/icons/ledger.png';
import loading from './assets/icons/loading.png';
import location from './assets/icons/location.png';
import more from './assets/icons/more.png';
import my from './assets/icons/my.png';
import pieChart from './assets/icons/pie-chart.png';
import receipt from './assets/icons/receipt.png';
import rent from './assets/icons/rent.png';
import savings from './assets/icons/savings.png';
import scan from './assets/icons/scan.png';
import settings from './assets/icons/settings.png';
import shield from './assets/icons/shield.png';
import shopping from './assets/icons/shopping.png';
import success from './assets/icons/success.png';
import support from './assets/icons/support.png';
import tip from './assets/icons/tip.png';
import transport from './assets/icons/transport.png';
import trend from './assets/icons/trend.png';
import trophy from './assets/icons/trophy.png';
import wallet from './assets/icons/wallet.png';
import warning from './assets/icons/warning.png';

const SRC = {
  home,
  ledger,
  analytics,
  challenge,
  my,
  shopping,
  delivery,
  cafe,
  transport,
  rent,
  more,
  receipt,
  calendar,
  wallet,
  card,
  coins,
  'pie-chart': pieChart,
  'bar-chart': barChart,
  trend,
  category,
  savings,
  goal,
  trophy,
  gift,
  bell,
  settings,
  'ai-coach': aiCoach,
  scan,
  shield,
  help,
  info,
  tip,
  location,
  support,
  success,
  warning,
  error,
  loading,
};

const ALIAS = {
  'user-avatar': 'my',
  'bank-card': 'card',
  'receipt-scan': 'scan',
  'trend-up': 'trend',
  'shield-check': 'shield',
};

function Pencil({ size }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false" style={{ display: 'block' }}>
      <path fill="currentColor" d="M3.2 16.9 15.1 5l3.9 3.9L7.1 20.8H3.2v-3.9zm12.6-12.6 2.1-2.1a1.2 1.2 0 0 1 1.7 0l2.2 2.2a1.2 1.2 0 0 1 0 1.7l-2.1 2.1-3.9-3.9z" />
    </svg>
  );
}

export function PigLogo({ size = 96 }) {
  return <img className="pig" src={pig} width={size} height={size} alt="" style={{ objectFit: 'contain' }} />;
}

export function Icon({ name, size = 22 }) {
  if (name === 'pencil') return <Pencil size={size} />;
  const src = SRC[ALIAS[name] || name] || category;
  return <img src={src} width={size} height={size} alt="" style={{ width: size, height: size, display: 'block', flex: 'none', objectFit: 'contain' }} />;
}
