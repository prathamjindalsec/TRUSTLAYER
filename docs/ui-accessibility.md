# TRUSTLAYER Phase 12 — UI quality, responsiveness, and accessibility

## What this phase changes

Phase 12 strengthens the working React client instead of adding decorative controls. It keeps the
existing dark navy / green security visual system, but improves the interaction boundary that
matters on a phone, keyboard, and screen reader:

- every public, authenticated, and sign-in layout has a visible-on-focus **Skip to main content**
  link and a focusable `main` landmark;
- the small-screen application navigation traps focus while open, closes with Escape or its
  backdrop, prevents background scrolling, and restores focus to the invoking menu button;
- public mobile navigation and the account actions popover expose their expanded state and close
  with Escape; the account popover uses ordinary link/button semantics rather than incorrectly
  claiming ARIA menu keyboard behavior it did not implement;
- dialogs trap focus, prevent background scrolling, support Escape and pointer-backdrop close,
  and return focus to their invoking control;
- labels, field hints, and validation errors are programmatically associated through
  `aria-describedby`; field errors are announced with `role="alert"`;
- toast text is now a live region and has a distinct, named dismiss button, rather than making
  the message itself a generically named button;
- interactive icon controls were raised to at least 44×44 CSS pixels where practical, including
  theme toggles, navigation controls, modal close, cookie settings, and QR-file removal;
- tables improved in the scanner views have a caption and a keyboard-focusable horizontal scroll
  region, so a narrow viewport does not create undiscoverable clipped columns;
- QR upload receives a visible focus-within treatment, and reduced-motion preferences remain
  respected globally.

These changes are intentionally narrow. The backend continues to make every authorization,
validation, rate-limit, risk, and privacy decision.

## Verification actually performed

```zsh
cd frontend
npm test
npm run build
```

The current final run passed 14 tests and a production Vite build. The added tests specifically verify
the skip-link target, form-field error/hint associations, modal focus trapping and restoration, and
test cleanup so body scroll-lock or DOM nodes cannot leak between test cases.

## Responsive design decisions

The layout is mobile-first: grids collapse to one column below their named Tailwind breakpoints;
wide data tables retain their complete columns inside keyboard-focusable horizontal regions rather
than dropping risk information; navigation switches to a focus-managed off-canvas panel; and action
buttons retain usable touch sizes. Route-level code splitting remains enabled.

No independent assistive-technology audit, real-device matrix, or formal WCAG 2.2 AA certification
has been completed. Automated DOM tests do not prove color contrast, screen-reader announcements in
every browser, touch ergonomics on every handset, or Safari/Firefox behavior. Those are Phase 14
work and remain explicit release blockers for any production claim.
