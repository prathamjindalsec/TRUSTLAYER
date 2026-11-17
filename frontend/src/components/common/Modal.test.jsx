import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { Modal } from './Modal';

function ControlledModal() {
  const [open, setOpen] = useState(true);
  const [password, setPassword] = useState('');
  return (
    <>
      <button type="button">Open dialog</button>
      <Modal open={open} onClose={() => setOpen(false)} title="Confirm a sensitive action">
        <label htmlFor="password">Current password</label>
        <input id="password" value={password} onChange={(event) => setPassword(event.target.value)} />
      </Modal>
    </>
  );
}

function TriggeredModal() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>Open dialog</button>
      <Modal open={open} onClose={() => setOpen(false)} title="Confirm a sensitive action">
        <label htmlFor="triggered-password">Current password</label>
        <input id="triggered-password" />
      </Modal>
    </>
  );
}

describe('Modal', () => {
  it('does not steal focus from a controlled input after it rerenders', () => {
    render(<ControlledModal />);
    const password = screen.getByLabelText('Current password');
    password.focus();

    fireEvent.change(password, { target: { value: 'a' } });

    expect(password).toHaveValue('a');
    expect(password).toHaveFocus();
  });

  it('closes when Escape is pressed', () => {
    render(<ControlledModal />);

    fireEvent.keyDown(window, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('traps keyboard focus and restores it to the invoking control after close', () => {
    render(<TriggeredModal />);
    const opener = screen.getByRole('button', { name: 'Open dialog' });
    // JSDOM does not move focus to a clicked button as a browser does.
    opener.focus();
    fireEvent.click(opener);
    const close = screen.getByRole('button', { name: 'Close dialog' });
    const password = screen.getByLabelText('Current password');

    close.focus();
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
    expect(password).toHaveFocus();

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(opener).toHaveFocus();
  });
});
