import { describe, expect, it } from 'vitest';
import { codeFromParam, defang, handleFromParam, HANDLE_RE, handleFitsName, inviteText, inviteUrl, makeHandle, normalizeHandle, personUrl, smsLink, warningText, whatsappLink } from '../lib/invite';
import type { AnalysisRun } from '../ai/types';

const run = {
  id: 'r1',
  createdAt: 0,
  input: 'Your KYC is expired. Call +91 98765 43210',
  hasImage: false,
  source: 'in_cloud',
  mode: 'smart',
  latencyMs: 900,
  redactions: 1,
  analysis: {
    verdict: 'scam',
    riskScore: 92,
    category: 'bank_impersonation',
    summary: 'Fake KYC update.',
    redFlags: [{ label: 'KYC "update"', evidence: 'KYC is expired', explanation: '' }, { label: 'Pressure', evidence: '', explanation: '' }],
    nextSteps: [],
    reassurances: [],
    safeReply: '',
  },
} as unknown as AnalysisRun;

describe('WhatsApp and SMS sharing', () => {
  it('builds an invite link that opens the circle screen', () => {
    expect(inviteUrl('AB12CD', 'https://chaukas-app.web.app')).toBe('https://chaukas-app.web.app/#/circle/AB12CD');
    expect(inviteText('AB12CD', 'The Patels', 'https://x.app')).toContain('https://x.app/#/circle/AB12CD');
  });

  it('encodes WhatsApp and SMS links', () => {
    expect(whatsappLink('Hi & bye?')).toBe('https://wa.me/?text=Hi%20%26%20bye%3F');
    expect(smsLink('Hi there')).toBe('sms:?&body=Hi%20there');
  });

  it('only accepts real circle codes from the route', () => {
    expect(codeFromParam('ab12cd')).toBe('AB12CD');
    expect(codeFromParam('nope')).toBe('');
    expect(codeFromParam(undefined)).toBe('');
  });

  it('makes scam links unclickable', () => {
    expect(defang('Pay at http://sbi-kyc.top/v now')).toBe('Pay at sbi-kyc[.]top/v now');
    expect(defang('see www.bad.xyz')).toBe('see bad[.]xyz');
    expect(warningText(run, 'go to https://a.top/b', 'https://x.app')).toContain('a[.]top/b');
  });

  it('forwards only the redacted excerpt, never the original message', () => {
    const t = warningText(run, 'Your KYC is expired. Call [PHONE_1]', 'https://x.app');
    expect(t).toContain('[PHONE_1]');
    expect(t).not.toContain('98765');
    expect(t).toContain('risk 92/100');
    expect(t).toContain('1930');
    expect(t).toContain('https://x.app');
  });
});


describe('personal Chaukas IDs', () => {
  it('builds a readable ID that the rules accept', () => {
    expect(makeHandle('Riya Sharma', () => 0.4821)).toBe('riyasharma-4821');
    expect(makeHandle('नानी', () => 0.07)).toBe('nani-0700');
    expect(makeHandle('पापा', () => 0.4821)).toBe('papa-4821');
    expect(makeHandle('राजेश', () => 0.1)).toBe('rajesh-1000');
    expect(makeHandle('রাজেশ', () => 0.1)).toBe('rajesh-1000');
    expect(makeHandle('அம்மா', () => 0.1)).toBe('amma-1000');
    expect(makeHandle('నాన్న', () => 0.1)).toBe('nanna-1000');
    expect(handleFitsName('papa-4821', 'Papa')).toBe(true);
    expect(handleFitsName('someone-4821', 'Papa')).toBe(false);
    expect(makeHandle('A', () => 0)).toBe('friend-0000');
    expect(HANDLE_RE.test(makeHandle('Abcdefghijklmnopqrstuvwxyz'))).toBe(true);
  });

  it('accepts what people type or scan', () => {
    expect(normalizeHandle('  @Riya-4821 ')).toBe('riya-4821');
    expect(normalizeHandle('https://chaukas-app.web.app/#/circle/@riya-4821')).toBe('riya-4821');
    expect(normalizeHandle('riya')).toBe('');
    expect(normalizeHandle('ABC123')).toBe('');
  });

  it('round-trips through the QR link and route', () => {
    const url = personUrl('riya-4821', 'https://x.app');
    expect(url).toBe('https://x.app/#/circle/@riya-4821');
    expect(handleFromParam('@riya-4821')).toBe('riya-4821');
    expect(handleFromParam('ABC123')).toBe('');
  });
});
