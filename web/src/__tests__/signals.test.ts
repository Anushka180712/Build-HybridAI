import { describe, expect, it } from 'vitest';
import { computeSignals, extractLinks } from '../ai/signals';
import { inspectQr } from '../lib/qr';

describe('signals', () => {
  it('flags look-alike and risky domains', () => {
    const [l] = extractLinks('Verify now: https://paypa1-secure.top/login');
    expect(l.host).toBe('paypa1-secure.top');
    expect(l.issues.some((i) => i.includes('paypal'))).toBe(true);
    expect(l.issues.some((i) => i.includes('.top'))).toBe(true);
  });

  it('does not flag official domains', () => {
    const [l] = extractLinks('Track at amazon.com/orders');
    expect(l.issues).toEqual([]);
  });

  it('scores a classic scam high and a normal message low', () => {
    const scam = computeSignals('URGENT: your account is suspended. Buy gift cards and send the verification code within 2 hours: bit.ly/x');
    const ok = computeSignals('See you at lunch tomorrow, I booked the table for 1pm.');
    expect(scam.score).toBeGreaterThan(70);
    expect(ok.score).toBeLessThan(10);
  });
});

describe('India patterns', () => {
  it('flags KYC and UPI PIN scams', () => {
    const k = computeSignals('Your SBI account will be blocked, KYC not updated. Share OTP.');
    expect(k.signals.some((s) => s.id === 'india_kyc')).toBe(true);
    const u = computeSignals('I sent money by mistake, just enter your UPI PIN to approve the collect request');
    expect(u.signals.some((s) => s.id === 'upi_pin')).toBe(true);
    expect(u.score).toBeGreaterThan(30);
  });

  it('flags scam keywords, free recharge, and e-challan', () => {
    const s = computeSignals('This is a scam message from unknown');
    expect(s.signals.some((sig) => sig.id === 'scam_keyword')).toBe(true);
    expect(s.score).toBeGreaterThanOrEqual(50);

    const r = computeSignals('Free 5G recharge offer for all users');
    expect(r.signals.some((sig) => sig.id === 'recharge')).toBe(true);
    expect(r.score).toBeGreaterThanOrEqual(50);

    const c = computeSignals('Pending e-challan traffic fine pay immediately');
    expect(c.signals.some((sig) => sig.id === 'traffic_challan')).toBe(true);
    expect(c.score).toBeGreaterThanOrEqual(50);
  });
});

describe('QR inspector', () => {
  it('explains that a UPI QR sends money', () => {
    const f = inspectQr('upi://pay?pa=refund.desk@okaxis&pn=Refund%20Desk&am=4999&tn=Cashback%20refund');
    expect(f.kind).toBe('upi');
    expect(f.headline).toContain('SEND');
    expect(f.headline).toContain('4,999');
    expect(f.warnings.length).toBeGreaterThan(1);
  });
});
