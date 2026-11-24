/**
 * User settings persisted on the device, plus the list of explanation languages.
 */
import { createStore } from '../lib/store';
import type { PrivacyMode } from '../ai/types';

export interface Settings {
  mode: PrivacyMode;
  redactBeforeCloud: boolean;
  allowCloudImages: boolean;
  privateTerms: string;
  largeText: boolean;
  reduceMotion: boolean;
  syncHistory: boolean;
  onboarded: boolean;
  /** Language Chaukas explains its verdicts in. */
  language: string;
  /** A trusted person's phone number for the "Call my family" button. */
  familyPhone: string;
  /** Casual, friendly wording in answers (Hinglish when the language is Hindi). */
  casualTone: boolean;
  /** Share every scam or suspicious check with my Family Circle groups automatically. */
  autoShareCircle: boolean;
  /** Set once Chaukas has moved someone from Private to Smart because Gemini Nano is missing. */
  privateFallbackNoted: boolean;
}

export const settingsStore = createStore<Settings>('chaukas.settings.v1', {
  mode: 'smart',
  redactBeforeCloud: true,
  allowCloudImages: false,
  privateTerms: '',
  largeText: false,
  reduceMotion: false,
  syncHistory: true,
  onboarded: false,
  language: 'English',
  familyPhone: '',
  casualTone: false,
  autoShareCircle: true,
  privateFallbackNoted: false,
});

export function engineSettings() {
  const s = settingsStore.get();
  return {
    redactBeforeCloud: s.redactBeforeCloud,
    allowCloudImages: s.allowCloudImages,
    privateTerms: s.privateTerms.split(',').map((t) => t.trim()).filter(Boolean),
    language: s.language || 'English',
    casual: s.casualTone,
  };
}

/** Languages Chaukas can explain itself in. Codes follow BCP 47. */
export const LANGUAGES: { name: string; native: string; code: string }[] = [
  { name: 'English', native: 'English', code: 'en' },
  { name: 'Hindi', native: 'हिन्दी', code: 'hi' },
  { name: 'Bengali', native: 'বাংলা', code: 'bn' },
  { name: 'Tamil', native: 'தமிழ்', code: 'ta' },
  { name: 'Telugu', native: 'తెలుగు', code: 'te' },
  { name: 'Marathi', native: 'मराठी', code: 'mr' },
  { name: 'Kannada', native: 'ಕನ್ನಡ', code: 'kn' },
  { name: 'Malayalam', native: 'മലയാളം', code: 'ml' },
  { name: 'Gujarati', native: 'ગુજરાતી', code: 'gu' },
  { name: 'Punjabi', native: 'ਪੰਜਾਬੀ', code: 'pa' },
  { name: 'Spanish', native: 'Español', code: 'es' },
  { name: 'Japanese', native: '日本語', code: 'ja' },
  { name: 'French', native: 'Français', code: 'fr' },
  { name: 'Portuguese', native: 'Português', code: 'pt' },
];

/** Output languages Gemini Nano in Chrome is tuned for. */
export const ON_DEVICE_LANGUAGES = ['en', 'es', 'ja'];

export function languageCode(name: string): string {
  return LANGUAGES.find((l) => l.name === name)?.code ?? 'en';
}
