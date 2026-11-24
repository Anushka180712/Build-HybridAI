import { CATEGORIES, type Analysis, type Category, type Verdict } from './types';

/** Extracts the first JSON object from model text, tolerating code fences. */
export function extractJson(text: string): unknown {
  const fenced = text.replace(/```(?:json)?/gi, '').trim();
  try {
    return JSON.parse(fenced);
  } catch {
    const start = fenced.indexOf('{');
    const end = fenced.lastIndexOf('}');
    if (start >= 0 && end > start) return JSON.parse(fenced.slice(start, end + 1));
    throw new Error('The model did not return JSON.');
  }
}

const clamp = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n));
const str = (v: unknown, max = 400) => (typeof v === 'string' ? v.trim().slice(0, max) : '');
const strList = (v: unknown, n: number) => (Array.isArray(v) ? v.map((x) => str(x, 240)).filter(Boolean).slice(0, n) : []);

/**
 * Normalises whatever the model produced into a well-formed Analysis.
 * Small on-device models occasionally drift (score disagreeing with verdict,
 * unknown category), so we repair rather than fail.
 */
export function normalizeAnalysis(raw: unknown): Analysis {
  const o = (raw ?? {}) as Record<string, unknown>;
  // An unreadable score must never default to "safe": fall back to the middle.
  const n = Math.round(Number(o.riskScore));
  let score = clamp(Number.isFinite(n) ? n : 50, 0, 100);
  const verdict: Verdict = (['safe', 'suspicious', 'scam'] as const).includes(o.verdict as Verdict)
    ? (o.verdict as Verdict)
    : score >= 70
      ? 'scam'
      : score > 30
        ? 'suspicious'
        : 'safe';
  // Keep score and verdict consistent so the UI never contradicts itself.
  if (verdict === 'safe' && score > 30) score = 30;
  if (verdict === 'scam' && score < 70) score = 70;
  if (verdict === 'suspicious') score = clamp(score, 31, 69);
  const category = (CATEGORIES as readonly string[]).includes(o.category as string) ? (o.category as Category) : verdict === 'safe' ? 'legitimate' : 'other';
  const redFlags = Array.isArray(o.redFlags)
    ? o.redFlags
        .map((f) => {
          const r = (f ?? {}) as Record<string, unknown>;
          return { label: str(r.label, 80), evidence: str(r.evidence, 160), why: str(r.why, 240) };
        })
        .filter((f) => f.label)
        .slice(0, 5)
    : [];
  return {
    verdict,
    riskScore: score,
    category,
    summary: str(o.summary, 300) || 'No summary was returned.',
    redFlags,
    reassurances: strList(o.reassurances, 3),
    nextSteps: strList(o.nextSteps, 4),
    safeReply: str(o.safeReply, 400),
  };
}
