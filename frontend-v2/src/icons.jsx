import aiCoach from './assets/icons/ai-coach.svg';
import analytics from './assets/icons/analytics.svg';
import barChart from './assets/icons/bar-chart.svg';
import bell from './assets/icons/bell.svg';
import cafe from './assets/icons/cafe.svg';
import calendar from './assets/icons/calendar.svg';
import card from './assets/icons/card.svg';
import category from './assets/icons/category.svg';
import challenge from './assets/icons/challenge.svg';
import coins from './assets/icons/coins.svg';
import delivery from './assets/icons/delivery.svg';
import pig from './assets/icons/dont-worry-pig.png';
import error from './assets/icons/error.svg';
import gift from './assets/icons/gift.svg';
import goal from './assets/icons/goal.svg';
import help from './assets/icons/help.svg';
import home from './assets/icons/home.svg';
import info from './assets/icons/info.svg';
import ledger from './assets/icons/ledger.svg';
import loading from './assets/icons/loading.svg';
import location from './assets/icons/location.svg';
import more from './assets/icons/more.svg';
import my from './assets/icons/my.svg';
import pieChart from './assets/icons/pie-chart.svg';
import receipt from './assets/icons/receipt.svg';
import rent from './assets/icons/rent.svg';
import savings from './assets/icons/savings.svg';
import scan from './assets/icons/scan.svg';
import settings from './assets/icons/settings.svg';
import shield from './assets/icons/shield.svg';
import shopping from './assets/icons/shopping.svg';
import success from './assets/icons/success.svg';
import support from './assets/icons/support.svg';
import tip from './assets/icons/tip.svg';
import transport from './assets/icons/transport.svg';
import trend from './assets/icons/trend.svg';
import trophy from './assets/icons/trophy.svg';
import wallet from './assets/icons/wallet.svg';
import warning from './assets/icons/warning.svg';

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
  return (
    <span
      aria-hidden="true"
      style={{
        width: size,
        height: size,
        display: 'block',
        flex: 'none',
        backgroundColor: 'currentColor',
        WebkitMaskImage: `url(${src})`,
        maskImage: `url(${src})`,
        WebkitMaskRepeat: 'no-repeat',
        maskRepeat: 'no-repeat',
        WebkitMaskPosition: 'center',
        maskPosition: 'center',
        WebkitMaskSize: 'contain',
        maskSize: 'contain',
      }}
    />
  );
}
