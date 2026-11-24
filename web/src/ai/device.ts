/**
 * Asks Chrome's Prompt API whether Gemini Nano can serve a request, before deciding whether a
 * prompt may leave the device.
 */
import type { DeviceAvailability } from './types';

interface LanguageModelStatic {
  availability(options?: unknown): Promise<string>;
}

function api(): LanguageModelStatic | undefined {
  return (globalThis as unknown as { LanguageModel?: LanguageModelStatic }).LanguageModel;
}

export const TEXT_INPUTS = [{ type: 'text', languages: ['en'] }] as const;
export const IMAGE_INPUTS = [{ type: 'text', languages: ['en'] }, { type: 'image' }] as const;

/**
 * Asks Chrome's built-in Prompt API whether Gemini Nano can serve a request
 * right now. Chaukas uses this to decide, before sending, whether a prompt is
 * about to leave the device, so it can redact first.
 */
export async function deviceAvailability(withImage = false): Promise<DeviceAvailability> {
  const lm = api();
  if (!lm) return 'unsupported';
  try {
    const a = await lm.availability({ expectedInputs: withImage ? IMAGE_INPUTS : TEXT_INPUTS, expectedOutputs: [{ type: 'text', languages: ['en'] }] });
    if (a === 'available' || a === 'downloadable' || a === 'downloading' || a === 'unavailable') return a;
    // Older Chrome builds used "readily" / "after-download".
    if (a === 'readily') return 'available';
    if (a === 'after-download') return 'downloadable';
    return 'unavailable';
  } catch {
    return 'unavailable';
  }
}

export function browserLabel(): string {
  const ua = navigator.userAgent;
  const chrome = ua.match(/Chrome\/(\d+)/);
  if (/Edg\//.test(ua)) return `Edge ${ua.match(/Edg\/(\d+)/)?.[1] ?? ''}`.trim();
  if (chrome) return `Chrome ${chrome[1]}`;
  if (/Firefox\//.test(ua)) return 'Firefox';
  if (/Safari\//.test(ua)) return 'Safari';
  return 'This browser';
}
