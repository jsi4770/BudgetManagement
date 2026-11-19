import { createContext, useContext, useEffect, useState } from 'react';

const EMPTY = { aliases: {}, caps: {}, fixed: [] };
const PrefsContext = createContext({ prefs: EMPTY, update: () => {} });

function storageKey(userId) {
  return `bm-prefs:${userId || 'guest'}`;
}

export function PrefsProvider({ userId, children }) {
  const [prefs, setPrefs] = useState(EMPTY);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(storageKey(userId));
      setPrefs(raw ? { ...EMPTY, ...JSON.parse(raw) } : EMPTY);
    } catch {
      setPrefs(EMPTY);
    }
  }, [userId]);

  const update = (next) => {
    setPrefs((current) => {
      const value = typeof next === 'function' ? next(current) : next;
      localStorage.setItem(storageKey(userId), JSON.stringify(value));
      return value;
    });
  };

  return <PrefsContext.Provider value={{ prefs, update }}>{children}</PrefsContext.Provider>;
}

export function usePrefs() {
  return useContext(PrefsContext);
}

export function categoryLabel(category, aliases = {}) {
  if (!category) return '';
  const alias = aliases[category.id] ?? aliases[String(category.id)];
  const text = alias == null ? '' : String(alias).trim();
  return text || category.name || '';
}
