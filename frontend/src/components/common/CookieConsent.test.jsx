import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { CookieConsent } from './CookieConsent';

const STORAGE_KEY = 'trustlayer.cookie-consent.v1';

describe('CookieConsent', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('records a reject-non-essential choice while preserving essential security storage', () => {
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CookieConsent />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Reject non-essential' }));

    expect(screen.queryByRole('button', { name: 'Accept all' })).not.toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY))).toMatchObject({
      essential: true,
      functional: false,
      analytics: false,
    });
  });
});
