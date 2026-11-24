import { describe, expect, it } from 'vitest';
import { buildAnalysisPrompt, toneRule } from '../ai/prompts';
import { alertForwardText } from '../lib/invite';

describe('casual tone', () => {
  it('is off unless asked for', () => {
    expect(toneRule(false, 'Hindi')).toBe('');
    expect(buildAnalysisPrompt('hi', [], [], false, 'Hindi')).not.toMatch(/Hinglish/);
  });

  it('uses Hinglish for Hindi and keeps the safety advice clear', () => {
    const p = buildAnalysisPrompt('hi', [], [], false, 'Hindi', true);
    expect(p).toMatch(/Hinglish/);
    expect(p).toMatch(/must stay exactly as clear/);
  });

  it('works for English and other languages', () => {
    expect(toneRule(true, 'English')).toMatch(/friendly, everyday English/);
    expect(toneRule(true, 'Tamil')).toMatch(/everyday spoken Tamil/);
  });
});

describe('forwarding a group warning', () => {
  it('defangs links and names who spotted it', () => {
    const t = alertForwardText({ verdict: 'scam', riskScore: 91, category: 'phishing', summary: 'Fake KYC page at sbi-kyc.top', excerpt: 'Update now http://sbi-kyc.top/v', byName: 'Riya' }, 'https://x.app');
    expect(t).toMatch(/spotted by Riya/);
    expect(t).toContain('sbi-kyc[.]top/v');
    expect(t).not.toContain('http://sbi');
    expect(t).toMatch(/1930/);
  });
});
