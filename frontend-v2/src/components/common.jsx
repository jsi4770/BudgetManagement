import { iconFor, isIncome, shortDate, won } from '../format';
import { Icon, PigLogo } from '../icons';
import { categoryLabel, usePrefs } from '../prefs';

export function Hd({ title, onBack }) {
  return (
    <div className="hd">
      <button type="button" onClick={onBack}>‹</button>
      <h1 className="h2">{title}</h1>
    </div>
  );
}

export function Keypad({ value, onChange }) {
  const press = (key) => {
    if (key === '⌫') onChange(value.slice(0, -1));
    else onChange((value + key).replace(/^0+/, '').slice(0, 9));
  };
  return (
    <div className="kp">
      {['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫'].map((key) => (
        <button type="button" key={key} onClick={() => press(key)}>{key}</button>
      ))}
    </div>
  );
}

export function Money({ transaction, expenseColor = 'var(--text)' }) {
  const income = isIncome(transaction.category);
  const text = `${income ? '+' : '-'}${Number(transaction.amount).toLocaleString('ko-KR')}원`;
  const red = !income && expenseColor === 'var(--error)';
  return <b className={red ? 'out' : undefined} style={{ color: income ? 'var(--success)' : expenseColor }}>{text}</b>;
}

export function TransactionRow({ transaction, extra }) {
  const { prefs } = usePrefs();
  const name = categoryLabel(transaction.category, prefs.aliases);
  return (
    <div className="tx">
      <div className="ico"><Icon name={iconFor(transaction.category?.name)} /></div>
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 500 }}>{transaction.memo || name}</div>
        <div className="t2">
          {name}
          {transaction.transactionDate ? ` · ${shortDate(transaction.transactionDate)}` : ''}
        </div>
      </div>
      <div style={{ textAlign: 'right' }}>
        <Money transaction={transaction} />
        {extra}
      </div>
    </div>
  );
}

export function Loading({ text = '불러오는 중…' }) {
  return (
    <div className="brand">
      <PigLogo size={96} />
      <div className="t2">{text}</div>
    </div>
  );
}

export function Usage({ rate }) {
  const value = Math.max(0, Number(rate) || 0);
  const color = value >= 90 ? 'var(--error)' : value >= 70 ? 'var(--warning)' : 'var(--primary)';
  return (
    <div className="progress">
      <div style={{ width: `${Math.min(100, value)}%`, background: color }} />
    </div>
  );
}

export function WonText({ value }) {
  return won(value);
}
