import { describe, expect, it } from 'vitest';
import { VERDICT_WORDS, cleanPhone, spokenVerdict, uiLang } from '../lib/verdictWords';
import { LANGUAGES } from '../data/settings';
import { SPEECH_LOCALES } from '../lib/voice';

describe('verdict words', () => {
  it('has every line in all six languages', () => {
    for (const [code, w] of Object.entries(VERDICT_WORDS)) {
      expect(Object.values(w.verdict).every(Boolean), code).toBe(true);
      expect(w.dont).toHaveLength(3);
      expect(w.safeTip && w.why, code).toBeTruthy();
      expect(LANGUAGES.some((l) => l.code === code)).toBe(true);
      expect(SPEECH_LOCALES[code]).toMatch(/-(IN)$/);
    }
  });

  it('maps settings languages and falls back to English', () => {
    expect(uiLang('Hindi')).toBe('hi');
    expect(uiLang('Japanese')).toBe('en');
  });

  it('speaks the verdict and the three do-nots in Hindi', () => {
    const t = spokenVerdict('scam', '', [], 'hi');
    expect(t).toContain(VERDICT_WORDS.hi.verdict.scam);
    expect(t).toContain(VERDICT_WORDS.hi.dont[2]);
  });

  it('cleans phone numbers for tel: links', () => {
    expect(cleanPhone('+91 98765-43210')).toBe('+919876543210');
    expect(cleanPhone('098765 43210')).toBe('09876543210');
    expect(cleanPhone('call me')).toBe('');
    expect(cleanPhone('12')).toBe('');
  });
});
