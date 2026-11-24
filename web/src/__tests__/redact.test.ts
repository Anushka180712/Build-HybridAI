import { describe, expect, it } from 'vitest';
import { redact, restore } from '../ai/redact';

describe('redact', () => {
  it('hides emails, phones, cards and one-time codes', () => {
    const input = 'Hi, your code is 482913. Call +44 7700 900123, mail rose.k@example.com, card 4111 1111 1111 1111';
    const r = redact(input);
    expect(r.text).not.toContain('482913');
    expect(r.text).not.toContain('7700 900123');
    expect(r.text).not.toContain('rose.k@example.com');
    expect(r.text).not.toContain('4111 1111 1111 1111');
    expect(r.text).toContain('[EMAIL_1]');
    expect(r.text).toContain('[CODE_1]');
    expect(r.count).toBeGreaterThanOrEqual(4);
  });

  it('keeps link hosts but drops query strings', () => {
    const r = redact('Pay at https://ezpass-tollpay.top/pay?id=88213&u=me');
    expect(r.text).toContain('https://ezpass-tollpay.top/pay');
    expect(r.text).not.toContain('88213');
  });

  it('redacts custom private terms', () => {
    const r = redact('Hello Priya, see you Tuesday', ['Priya']);
    expect(r.text).toBe('Hello [NAME_1], see you Tuesday');
  });

  it('does not treat ordinary numbers as phones', () => {
    const r = redact('Your balance is $4.15 and the fee is 50.00 in 2026');
    expect(r.count).toBe(0);
  });

  it('restores placeholders locally', () => {
    const r = redact('Email me at a.b@example.com');
    expect(restore('Do not reply to [EMAIL_1]', r.map)).toBe('Do not reply to a.b@example.com');
  });
});
