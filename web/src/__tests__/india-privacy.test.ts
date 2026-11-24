import { describe, expect, it } from 'vitest';
import { redact, restore } from '../ai/redact';

const leaks = (input: string, secret: string, terms: string[] = []) => redact(input, terms).text.includes(secret);

describe('redaction of Indian identifiers', () => {
  it.each([
    ['Call +91 98765 43210 now', '98765 43210'],
    ['call 9876543210', '9876543210'],
    ['call 098765-43210', '98765-43210'],
    ['pay to rahul.k@okaxis today', 'rahul.k@okaxis'],
    ['pay 9876543210@ybl', '9876543210'],
    ['PAN ABCDE1234F', 'ABCDE1234F'],
    ['Aadhaar 2345 6789 0123 linked', '2345 6789 0123'],
    ['482913 is your OTP for login', '482913'],
    ['Use OTP 4829 to verify', '4829'],
    ['a/c no 123456789012', '123456789012'],
  ])('hides %s', (input, secret) => {
    expect(leaks(input, secret)).toBe(false);
  });

  it.each([
    'Dear customer, Rs.1,00,000 credited on 12/11/2026',
    'Order #4521 ships 12 Nov',
    'Price 45000 only',
    'Meet at 10:30 on 12-11-2026',
    'follow us @sbi_official',
  ])('leaves ordinary text alone: %s', (input) => {
    expect(redact(input).text).toBe(input);
  });

  it('round-trips every placeholder', () => {
    const input = 'Hi Priya, call +91 98765 43210, pay priya@okaxis, OTP 482913';
    const r = redact(input, ['Priya']);
    expect(restore(r.text, r.map)).toBe(input);
  });

  it('uses the same placeholder for repeated values', () => {
    const r = redact('Call 9876543210. Again: 9876543210');
    expect(r.count).toBe(1);
    expect(r.text.match(/\[PHONE_1\]/g)?.length).toBe(2);
  });

  it('escapes regex characters in private terms', () => {
    expect(() => redact('a+b (c)', ['a+b', '(c)'])).not.toThrow();
  });

  it('ignores one-letter private terms so it never shreds the text', () => {
    expect(redact('a cat sat', ['a']).text).toBe('a cat sat');
  });

  it('restore leaves unknown placeholders untouched', () => {
    expect(restore('see [PHONE_9]', {})).toBe('see [PHONE_9]');
  });
});
