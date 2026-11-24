import { describe, expect, it } from 'vitest';
import { encodeWav, voiceErrorMessage } from '../lib/voice';

describe('voice', () => {
  it('encodes mono 16-bit WAV with a valid header', () => {
    const wav = encodeWav(new Float32Array([0, 1, -1, 0.5]), 16000);
    const v = new DataView(wav.buffer);
    const tag = (o: number) => String.fromCharCode(...wav.subarray(o, o + 4));
    expect(tag(0)).toBe('RIFF');
    expect(tag(8)).toBe('WAVE');
    expect(tag(36)).toBe('data');
    expect(v.getUint32(24, true)).toBe(16000);
    expect(v.getUint16(22, true)).toBe(1);
    expect(v.getUint32(40, true)).toBe(8);
    expect(v.getInt16(46, true)).toBe(0x7fff);
    expect(v.getInt16(48, true)).toBe(-0x8000);
  });

  it('explains errors in plain words and stays quiet on a cancel', () => {
    expect(voiceErrorMessage('aborted')).toBe('');
    expect(voiceErrorMessage('NotAllowedError')).toMatch(/microphone is blocked/i);
    expect(voiceErrorMessage('transcribe-failed')).toMatch(/connection/);
  });
});

describe('trimSilence', () => {
  it('cuts the quiet around the words but keeps a margin', async () => {
    const { trimSilence } = await import('../lib/voice');
    const rate = 16000;
    const s = new Float32Array(rate * 3); // 3 s
    for (let i = rate; i < rate * 2; i++) s[i] = 0.3 * Math.sin(i / 5); // speech in the middle second
    const out = trimSilence(s, rate);
    expect(out.length).toBeGreaterThan(rate);
    expect(out.length).toBeLessThan(rate * 1.7);
    expect(trimSilence(new Float32Array(100), rate).length).toBe(100);
  });
});

describe('joinResults', () => {
  it('joins separate phrases and drops Android repeats', async () => {
    const { joinResults } = await import('../lib/voice');
    expect(joinResults(['your bill ', 'is due today'])).toBe('your bill is due today');
    expect(joinResults(['your', 'your bill', 'your bill is due'])).toBe('your bill is due');
    expect(joinResults(['बिजली कट', 'बिजली कट जाएगी'])).toBe('बिजली कट जाएगी');
    expect(joinResults(['', ' '])).toBe('');
  });
});
