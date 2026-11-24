import { describe, expect, it } from 'vitest';
import { extractJson, normalizeAnalysis } from '../ai/parse';

describe('parse', () => {
  it('reads fenced JSON', () => {
    expect(extractJson('```json\n{"a":1}\n```')).toEqual({ a: 1 });
  });

  it('repairs a score that contradicts the verdict', () => {
    const a = normalizeAnalysis({ verdict: 'scam', riskScore: 20, category: 'nonsense', summary: 'x', redFlags: [], reassurances: [], nextSteps: [], safeReply: '' });
    expect(a.riskScore).toBe(70);
    expect(a.category).toBe('other');
  });

  it('derives a verdict from the score when missing', () => {
    expect(normalizeAnalysis({ riskScore: 85 }).verdict).toBe('scam');
    expect(normalizeAnalysis({ riskScore: 10 }).verdict).toBe('safe');
  });
});
