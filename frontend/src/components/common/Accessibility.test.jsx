import axe from 'axe-core';
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Modal } from './Modal';
import { SelectField, TextField } from './FormField';
import { SkipLink } from './SkipLink';

function AccessibleFixture() {
  return (
    <>
      <SkipLink />
      <main id="main-content" tabIndex="-1">
        <h1>Accessibility fixture</h1>
        <form>
          <TextField label="Suspicious URL" hint="Use an absolute HTTPS address." type="url" />
          <SelectField label="Classification" hint="Choose the review category.">
            <option value="">Choose one</option>
            <option value="phishing">Phishing</option>
          </SelectField>
          <button type="submit">Analyze safely</button>
        </form>
      </main>
      <Modal open onClose={() => {}} title="Confirm an action">
        <p id="modal-description">This dialog is focus-managed and has a named close control.</p>
        <button type="button">Continue</button>
      </Modal>
    </>
  );
}

describe('accessibility regression fixture', () => {
  it('has no detectable structural axe violations', async () => {
    render(<AccessibleFixture />);

    const results = await axe.run(document.body, {
      rules: {
        // JSDOM does not calculate rendered color contrast. That needs a real-browser audit.
        'color-contrast': { enabled: false },
      },
    });

    expect(results.violations).toEqual([]);
  });
});
