import { Cookie, Settings2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { Modal } from './Modal';

const STORAGE_KEY = 'trustlayer.cookie-consent.v1';
const defaultPreferences = { essential: true, functional: false, analytics: false };

function loadPreferences() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? null;
  } catch {
    return null;
  }
}

export function CookieConsent() {
  const [savedPreferences, setSavedPreferences] = useState(loadPreferences);
  const [showSettings, setShowSettings] = useState(false);
  const [preferences, setPreferences] = useState(savedPreferences ?? defaultPreferences);

  useEffect(() => {
    const onOpen = () => setShowSettings(true);
    window.addEventListener('trustlayer:cookie-settings', onOpen);
    return () => window.removeEventListener('trustlayer:cookie-settings', onOpen);
  }, []);

  const save = (nextPreferences) => {
    const normalized = { ...nextPreferences, essential: true, savedAt: new Date().toISOString() };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
    setSavedPreferences(normalized);
    setPreferences(normalized);
    window.dispatchEvent(new Event('trustlayer:cookie-consent-updated'));
    setShowSettings(false);
  };

  return (
    <>
      {!savedPreferences && (
        <section className="fixed inset-x-3 bottom-3 z-40 mx-auto max-w-3xl rounded-2xl border border-slate-200 bg-white p-4 shadow-2xl dark:border-slate-700 dark:bg-ink-900 sm:bottom-5 sm:p-5" aria-label="Cookie choices">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
            <span className="hidden rounded-xl bg-trust-50 p-3 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300 sm:block" aria-hidden="true"><Cookie className="h-5 w-5" /></span>
            <div className="min-w-0 flex-1">
              <h2 className="font-bold text-ink-950 dark:text-white">Your privacy choices</h2>
              <p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">
                TRUSTLAYER uses essential storage for sign-in security and this preference. It has no analytics or advertising trackers enabled by default. Read the <Link className="link-inline" to="/privacy">Privacy Policy</Link>.
              </p>
            </div>
          </div>
          <div className="mt-4 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button type="button" className="btn-secondary" onClick={() => save({ ...defaultPreferences })}>Reject non-essential</button>
            <button type="button" className="btn-secondary" onClick={() => setShowSettings(true)}><Settings2 className="h-4 w-4" aria-hidden="true" />Customize</button>
            <button type="button" className="btn-primary" onClick={() => save({ essential: true, functional: true, analytics: true })}>Accept all</button>
          </div>
        </section>
      )}
      <Modal
        open={showSettings}
        onClose={() => setShowSettings(false)}
        title="Cookie and storage choices"
        footer={<><button type="button" className="btn-secondary" onClick={() => setShowSettings(false)}>Cancel</button><button type="button" className="btn-primary" onClick={() => save(preferences)}>Save choices</button></>}
      >
        <p className="mb-4 text-sm leading-6 text-slate-600 dark:text-slate-300">We do not load advertising trackers. Essential security cookies cannot be disabled while using authenticated features.</p>
        <PreferenceToggle label="Essential security" description="Refresh-session and CSRF protection cookies; always required." checked disabled />
        <PreferenceToggle label="Functional preferences" description="Stores appearance and interface preferences on this device." checked={preferences.functional} onChange={(checked) => setPreferences((current) => ({ ...current, functional: checked }))} />
        <PreferenceToggle label="Analytics" description="No analytics tooling is configured in this version." checked={false} disabled />
      </Modal>
      {savedPreferences && (
        <button type="button" onClick={() => setShowSettings(true)} className="fixed bottom-3 left-3 z-30 inline-flex h-11 w-11 items-center justify-center rounded-full border bg-white text-slate-600 shadow-panel hover:text-trust-700 dark:bg-ink-900 dark:text-slate-300 dark:hover:text-trust-300" aria-label="Manage cookie choices">
          <Cookie className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </>
  );
}

function PreferenceToggle({ label, description, checked, disabled, onChange }) {
  const id = `cookie-${label.toLowerCase().replaceAll(/\s+/g, '-')}`;
  return (
    <div className="flex items-start justify-between gap-4 border-t py-4 first:border-t-0 first:pt-0 dark:border-slate-800">
      <div>
        <label htmlFor={id} className="font-semibold text-slate-900 dark:text-white">{label}</label>
        <p className="mt-1 text-sm leading-5 text-slate-600 dark:text-slate-300">{description}</p>
      </div>
      <input id={id} type="checkbox" className="mt-1 h-5 w-5 shrink-0 accent-trust-600" checked={checked} disabled={disabled} onChange={(event) => onChange?.(event.target.checked)} />
    </div>
  );
}
