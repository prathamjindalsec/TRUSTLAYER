import { render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  logoutAllDevices: vi.fn(),
  clearAccessToken: vi.fn(),
  clearSessionRecovery: vi.fn(),
  configureSessionRecovery: vi.fn(),
  setAccessToken: vi.fn(),
}));

vi.mock('../api/auth', () => ({
  refresh: mocks.refresh,
  login: mocks.login,
  logout: mocks.logout,
  logoutAllDevices: mocks.logoutAllDevices,
}));

vi.mock('../api/http', () => ({
  clearAccessToken: mocks.clearAccessToken,
  clearSessionRecovery: mocks.clearSessionRecovery,
  configureSessionRecovery: mocks.configureSessionRecovery,
  setAccessToken: mocks.setAccessToken,
}));

import { AuthProvider, useAuth } from './AuthContext';

function AuthStatus() {
  const { status } = useAuth();
  return <output>{status}</output>;
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shares the boot refresh request across React StrictMode effect replays', async () => {
    let resolveRefresh;
    mocks.refresh.mockImplementationOnce(() => new Promise((resolve) => {
      resolveRefresh = resolve;
    }));

    render(
      <StrictMode>
        <AuthProvider>
          <AuthStatus />
        </AuthProvider>
      </StrictMode>,
    );

    await waitFor(() => expect(mocks.refresh).toHaveBeenCalledTimes(1));
    resolveRefresh({ accessToken: 'short-lived-access-token', user: { id: 7, email: 'user@example.test' } });

    await waitFor(() => expect(screen.getByText('authenticated')).toBeInTheDocument());
    expect(mocks.setAccessToken).toHaveBeenCalledWith('short-lived-access-token');
  });
});
