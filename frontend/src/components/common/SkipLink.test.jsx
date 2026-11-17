import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { SkipLink } from './SkipLink';

describe('SkipLink', () => {
  it('targets the named main landmark and stays keyboard reachable', () => {
    render(<><SkipLink /><main id="main-content" tabIndex="-1">Dashboard content</main></>);

    const link = screen.getByRole('link', { name: 'Skip to main content' });
    link.focus();
    fireEvent.click(link);

    expect(link).toHaveFocus();
    expect(link).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('main')).toHaveAttribute('tabindex', '-1');
  });
});
