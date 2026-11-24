/**
 * Shared types for analyses, runs and privacy modes.
 */
export type Verdict = 'safe' | 'suspicious' | 'scam';

export const CATEGORIES = [
  'phishing',
  'impersonation',
  'delivery_fee',
  'bank_or_payment',
  'tech_support',
  'family_emergency',
  'romance',
  'investment_crypto',
  'job_offer',
  'prize_lottery',
  'government_tax',
  'marketplace',
  'legitimate',
  'other',
] as const;
export type Category = (typeof CATEGORIES)[number];

export interface RedFlag {
  label: string;
  /** A short quote copied from the message that shows the flag. */
  evidence: string;
  why: string;
}

export interface Analysis {
  verdict: Verdict;
  riskScore: number;
  category: Category;
  summary: string;
  redFlags: RedFlag[];
  reassurances: string[];
  nextSteps: string[];
  safeReply: string;
}

/** Where the answer came from. `preview` = local heuristics, no model. */
export type Source = 'on_device' | 'in_cloud' | 'preview';

/** User-facing privacy modes, mapped onto Firebase AI Logic InferenceMode. */
export type PrivacyMode = 'private' | 'smart' | 'cloud';

export interface AnalysisRun {
  id: string;
  createdAt: number;
  input: string;
  hasImage: boolean;
  analysis: Analysis;
  source: Source;
  mode: PrivacyMode;
  latencyMs: number;
  /** Number of personal details replaced before the prompt left the device. */
  redactions: number;
  /** The exact text that was sent to the cloud, when it was. */
  sentToCloud?: string;
  secondOpinion?: { analysis: Analysis; latencyMs: number; source: Source };
}

export type DeviceAvailability = 'available' | 'downloadable' | 'downloading' | 'unavailable' | 'unsupported';
