import { describe, expect, it } from 'vitest';
import { inspectQr } from '../lib/qr';
import { buildAnalysisPrompt, buildFollowUpPrompt } from '../ai/prompts';
import { extractJson, normalizeAnalysis } from '../ai/parse';
import { previewAnalysis } from '../ai/preview';
import { buildReport, REGIONS } from '../lib/report';
import { LANGUAGES, languageCode, ON_DEVICE_LANGUAGES } from '../data/settings';
import { computeSignals } from '../ai/signals';
import type { AnalysisRun } from '../ai/types';

describe('QR inspector', () => {
  it('handles a UPI code with no amount or name', () => {
    const f = inspectQr('upi://pay?pa=shop@okicici');
    expect(f.kind).toBe('upi');
    expect(f.headline).toContain('an amount you type');
    expect(f.warnings).toHaveLength(1);
  });
  it('is case-insensitive about the scheme', () => {
    expect(inspectQr('UPI://PAY?pa=a@ybl&am=10').kind).toBe('upi');
  });
  it('explains plain links and text', () => {
    expect(inspectQr('https://example.com/x').headline).toContain('example.com');
    expect(inspectQr('hello').kind).toBe('text');
  });
  it('flags a malformed UPI id', () => {
    expect(inspectQr('upi://pay?pa=not valid').warnings.some((w) => w.includes('malformed'))).toBe(true);
  });
  it('makes a pasted UPI link a strong scam signal', () => {
    expect(computeSignals('upi://pay?pa=a@ybl&am=4999').signals.some((s) => s.id === 'upi_qr')).toBe(true);
  });
});

describe('explain in my language', () => {
  it('asks for the chosen language but keeps quotes and enums intact', () => {
    const p = buildAnalysisPrompt('hello', [], [], false, 'Hindi');
    expect(p).toContain('in Hindi');
    expect(p).toContain('exactly as they appear');
    expect(p).toContain('enum values');
  });
  it('adds nothing for English', () => {
    expect(buildAnalysisPrompt('hello', [], [], false, 'English')).not.toContain('Write summary');
  });
  it('answers follow-ups in the chosen language', () => {
    const a = normalizeAnalysis({ verdict: 'scam', riskScore: 90 });
    expect(buildFollowUpPrompt('m', a, 'q', 'Tamil')).toContain('Answer in Tamil.');
  });
  it('maps every language to a unique code and falls back to English', () => {
    expect(new Set(LANGUAGES.map((l) => l.code)).size).toBe(LANGUAGES.length);
    expect(languageCode('Klingon')).toBe('en');
    expect(ON_DEVICE_LANGUAGES).toContain('en');
  });
});

describe('output repair', () => {
  it('never turns an unreadable score into "safe"', () => {
    expect(normalizeAnalysis({ riskScore: 'abc' }).verdict).toBe('suspicious');
    expect(normalizeAnalysis({}).verdict).toBe('suspicious');
    expect(normalizeAnalysis(null).verdict).toBe('suspicious');
  });
  it('clamps out-of-range scores', () => {
    expect(normalizeAnalysis({ verdict: 'scam', riskScore: 500 }).riskScore).toBe(100);
    expect(normalizeAnalysis({ verdict: 'safe', riskScore: -4 }).riskScore).toBe(0);
  });
  it('finds JSON wrapped in chatter', () => {
    expect(extractJson('Sure! Here it is: {"verdict":"safe"} Hope that helps')).toEqual({ verdict: 'safe' });
  });
});

describe('preview engine', () => {
  it('rates an Indian KYC scam as a scam', () => {
    const a = previewAnalysis('Dear customer your SBI KYC is expired, account will be blocked today. Update now: http://sbi-kyc.top/u and share OTP', false);
    expect(a.verdict).toBe('scam');
    expect(a.redFlags.length).toBeGreaterThan(0);
  });
  it('rates an ordinary message as safe', () => {
    expect(previewAnalysis('Reminder: dentist on Tuesday at 10:30.', false).verdict).toBe('safe');
  });
  it('treats a screenshot with no text as uncertain, not safe', () => {
    expect(previewAnalysis('', true).verdict).not.toBe('safe');
  });
});

describe('report it', () => {
  it('lists India first with the 1930 helpline and the official portal', () => {
    expect(REGIONS[0].code).toBe('IN');
    const hrefs = REGIONS[0].places.map((p) => p.href);
    expect(hrefs).toContain('tel:1930');
    expect(hrefs).toContain('https://cybercrime.gov.in/');
  });
  it('builds a readable report', () => {
    const run: AnalysisRun = {
      id: '1', createdAt: 0, input: 'Pay now', hasImage: false, source: 'preview', mode: 'smart', latencyMs: 1, redactions: 0,
      analysis: normalizeAnalysis({ verdict: 'scam', riskScore: 90, category: 'phishing', summary: 'Fake', redFlags: [{ label: 'Urgency', evidence: 'now', why: 'x' }] }),
    };
    const r = buildReport(run);
    expect(r).toContain('Likely scam');
    expect(r).toContain('Urgency: "now"');
    expect(r).toContain('Pay now');
  });
});

describe('first-hour recovery', () => {
  it('puts the 1930 helpline first in India', async () => {
    const { recoverySteps } = await import('../lib/report');
    expect(recoverySteps('IN')[0].title).toContain('1930');
    expect(recoverySteps('US')[0].title).toContain('bank');
    expect(recoverySteps('IN').length).toBeGreaterThanOrEqual(5);
  });
});
