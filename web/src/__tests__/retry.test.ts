import { describe, expect, it, vi } from 'vitest';
import { withRetry } from '../ai/engine';

describe('withRetry', () => {
  it('tries again when Gemini is busy, then succeeds', async () => {
    const call = vi.fn()
      .mockRejectedValueOnce(new Error('AI: Error fetching from https://x: [503 ] The model is overloaded'))
      .mockResolvedValueOnce('ok');
    await expect(withRetry(call, [1, 1])).resolves.toBe('ok');
    expect(call).toHaveBeenCalledTimes(2);
  });

  it('gives up after the last try', async () => {
    const call = vi.fn().mockRejectedValue(new Error('[500 ] Internal error'));
    await expect(withRetry(call, [1, 1])).rejects.toThrow('500');
    expect(call).toHaveBeenCalledTimes(3);
  });

  it('does not retry a request that was refused', async () => {
    const call = vi.fn().mockRejectedValue(new Error('[403 ] Permission denied'));
    await expect(withRetry(call, [1, 1])).rejects.toThrow('403');
    expect(call).toHaveBeenCalledTimes(1);
  });
});
