import { describe, expect, it } from 'vitest';

import { compareRisk, formatCurrency, formatNumber, titleCase } from './formatters';

describe('formatters', () => {
  it('renders safe fallback values instead of invalid numeric output', () => {
    expect(formatNumber(undefined)).toBe('0');
    expect(formatCurrency(undefined)).toBe('—');
    expect(formatCurrency('not-a-number')).toBe('—');
  });

  it('orders known risk classifications from lower to higher concern', () => {
    expect(compareRisk('LOW', 'HIGH')).toBeLessThan(0);
    expect(compareRisk('CRITICAL', 'MEDIUM')).toBeGreaterThan(0);
    expect(compareRisk('UNKNOWN', 'LOW')).toBeLessThan(0);
  });

  it('turns API enum values into readable labels', () => {
    expect(titleCase('PAYMENT_SCAM')).toBe('Payment Scam');
    expect(titleCase(null)).toBe('');
  });
});
