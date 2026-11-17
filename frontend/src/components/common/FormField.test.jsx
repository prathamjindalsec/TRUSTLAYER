import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { SelectField, TextField } from './FormField';

describe('form fields', () => {
  it('connects a text error to its input and announces it', () => {
    render(<TextField label="Email" error="Enter a valid email address." />);

    const input = screen.getByLabelText('Email');
    const error = screen.getByRole('alert');
    expect(input).toHaveAttribute('aria-describedby', error.id);
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('connects select hints and errors to the select control', () => {
    const { rerender } = render(<SelectField label="Risk level" hint="Choose a classification."><option>Low</option></SelectField>);
    const select = screen.getByLabelText('Risk level');
    expect(select).toHaveAttribute('aria-describedby');
    expect(screen.getByText('Choose a classification.')).toHaveAttribute('id');

    rerender(<SelectField label="Risk level" error="A classification is required."><option>Low</option></SelectField>);
    expect(screen.getByRole('alert')).toHaveTextContent('A classification is required.');
  });
});
