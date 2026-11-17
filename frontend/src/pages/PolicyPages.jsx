import { Cookie, FileText, LockKeyhole } from 'lucide-react';

import { usePageTitle } from '../hooks/usePageTitle';

const policies = {
  privacy: {
    icon: LockKeyhole,
    eyebrow: 'Privacy policy',
    title: 'Privacy by default, with limits stated plainly.',
    sections: [
      ['What we process', 'TRUSTLAYER processes account information, redacted scanner targets, simulation data, and community reports needed to provide the requested security features. Raw URL secrets, QR payloads, and beneficiary values are deliberately minimized or redacted by the current backend design.'],
      ['Why we process it', 'We use this information to authenticate users, produce explainable risk assessments, retain user-owned history, prevent abuse, and maintain a community intelligence record. We do not sell personal data or enable advertising trackers in this application.'],
      ['Your controls', 'You can update profile data, revoke active sessions, log out, and request account deletion through the account workspace. Personal-data export and a formal retention job are planned but not yet implemented; this limitation is documented rather than hidden.'],
      ['Security limits', 'No application can promise zero risk. This project has not undergone an independent penetration test or production security review, and it must not be described as production-certified.'],
    ],
  },
  terms: {
    icon: FileText,
    eyebrow: 'Terms of use',
    title: 'TRUSTLAYER is decision support—not a financial control system.',
    sections: [
      ['No guarantee of safety', 'Risk classifications represent explainable signals and limitations. A LOW result does not prove safety, and a HIGH result does not prove fraud. You remain responsible for independently verifying sensitive payment and identity decisions.'],
      ['No transaction authority', 'The transaction module uses user-entered simulations only. TRUSTLAYER does not connect to banks, UPI, card networks, wallets, or payment providers. It cannot initiate, block, delay, approve, reverse, or recover a real payment.'],
      ['Community data', 'Community reports remain UNVERIFIED unless an authorized human reviewer changes their status. Do not publish another person’s sensitive information in a report description.'],
      ['Appropriate use', 'Do not use the service to harass, surveil, evade law enforcement, submit malicious files, or abuse the platform. Suspicious URL analysis must remain within the platform’s safe static-analysis boundary.'],
    ],
  },
};

export function PrivacyPage() {
  return <PolicyPage type="privacy" />;
}

export function TermsPage() {
  return <PolicyPage type="terms" />;
}

export function CookiePolicyPage() {
  usePageTitle('Cookie choices');
  return <PolicyFrame icon={Cookie} eyebrow="Cookie policy" title="Only essential security cookies are active by default."><p>TRUSTLAYER uses a secure, HttpOnly refresh cookie to maintain a signed-in session and a readable XSRF token cookie to defend browser state changes against CSRF. Those are essential for authenticated security features.</p><p>Functional preference storage is optional and currently limited to interface choices on this device. Analytics and advertising cookies are not enabled in this application.</p><p>Use the cookie button in the lower-left corner to revise your choices at any time.</p></PolicyFrame>;
}

function PolicyPage({ type }) {
  const policy = policies[type];
  usePageTitle(policy.eyebrow);
  return <PolicyFrame {...policy}>{policy.sections.map(([heading, body]) => <section key={heading}><h2>{heading}</h2><p>{body}</p></section>)}</PolicyFrame>;
}

function PolicyFrame({ icon: Icon, eyebrow, title, children }) {
  return <div className="px-4 py-14 sm:px-6 sm:py-20"><article className="mx-auto max-w-3xl"><span className="inline-flex rounded-xl bg-trust-50 p-3 text-trust-700 dark:bg-trust-500/15 dark:text-trust-300"><Icon className="h-6 w-6" aria-hidden="true" /></span><p className="mt-6 text-xs font-bold uppercase tracking-[0.14em] text-trust-700 dark:text-trust-300">{eyebrow}</p><h1 className="mt-3 text-4xl font-bold tracking-tight text-ink-950 dark:text-white">{title}</h1><div className="mt-10 space-y-8 text-sm leading-7 text-slate-700 dark:text-slate-300">{children}</div></article></div>;
}
