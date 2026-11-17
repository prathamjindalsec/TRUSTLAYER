import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest's globals are intentionally disabled in this project. Register cleanup explicitly so
// a dialog, live region, local storage preference, or body-scroll lock cannot leak into another
// test and create a false result.
afterEach(() => {
  cleanup();
  document.body.style.overflow = '';
  localStorage.clear();
  sessionStorage.clear();
});
