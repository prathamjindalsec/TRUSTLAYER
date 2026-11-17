import { createContext, useContext, useEffect, useMemo, useState } from 'react';

const ThemeContext = createContext(null);
const STORAGE_KEY = 'trustlayer.theme-preference';
const CONSENT_KEY = 'trustlayer.cookie-consent.v1';

function functionalStorageAllowed() {
  try {
    return JSON.parse(localStorage.getItem(CONSENT_KEY))?.functional === true;
  } catch {
    return false;
  }
}

function resolveTheme(preference) {
  if (preference === 'system') {
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return preference;
}

export function ThemeProvider({ children }) {
  const [preference, setPreference] = useState(() => (
    functionalStorageAllowed() ? localStorage.getItem(STORAGE_KEY) || 'system' : 'system'
  ));
  const [resolvedTheme, setResolvedTheme] = useState(() => resolveTheme(preference));

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    const update = () => setResolvedTheme(resolveTheme(preference));
    update();
    if (preference === 'system' && media) {
      media.addEventListener('change', update);
      return () => media.removeEventListener('change', update);
    }
    return undefined;
  }, [preference]);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolvedTheme === 'dark');
    document.documentElement.style.colorScheme = resolvedTheme;
  }, [resolvedTheme]);

  useEffect(() => {
    const syncConsent = () => {
      if (!functionalStorageAllowed()) {
        localStorage.removeItem(STORAGE_KEY);
        setPreference('system');
      }
    };
    window.addEventListener('trustlayer:cookie-consent-updated', syncConsent);
    return () => window.removeEventListener('trustlayer:cookie-consent-updated', syncConsent);
  }, []);

  const value = useMemo(() => ({
    preference,
    resolvedTheme,
    setTheme(nextPreference) {
      if (functionalStorageAllowed()) {
        localStorage.setItem(STORAGE_KEY, nextPreference);
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
      setPreference(nextPreference);
    },
  }), [preference, resolvedTheme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used inside ThemeProvider.');
  }
  return context;
}
