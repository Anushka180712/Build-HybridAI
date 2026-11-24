import { describe, expect, it } from 'vitest';
import { SAMPLES } from '../lib/samples';
import { previewAnalysis } from '../ai/preview';

describe('built-in examples', () => {
  it.each(SAMPLES.map((s) => [s.label, s] as const))('%s gets a sensible verdict in preview mode', (_l, s) => {
    const a = previewAnalysis(s.text, false);
    if (s.tone === 'safe') expect(a.verdict).toBe('safe');
    else expect(a.verdict).not.toBe('safe');
    if (s.tone === 'scam') expect(a.verdict).toBe('scam');
  });
});
