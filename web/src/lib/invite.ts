/**
 * WhatsApp and SMS links for inviting family to a circle and for forwarding a warning.
 * Plain links only: the message opens in the person's own WhatsApp or Messages app and
 * nothing is sent until they press Send. Warnings use the redacted text, never the original.
 */
import type { AnalysisRun } from '../ai/types';
import { CATEGORY_LABEL, VERDICT_LABEL } from './format';

/** A link that opens Chaukas on the Family Circle screen and joins this circle. */
export function inviteUrl(code: string, origin = location.origin): string {
  return `${origin}/#/circle/${encodeURIComponent(code)}`;
}

export function inviteText(code: string, circleName: string, origin = location.origin): string {
  return `Join our family circle "${circleName}" on Chaukas, so we can warn each other about scam messages. Tap to join: ${inviteUrl(code, origin)} (code ${code})`;
}

export function whatsappLink(text: string): string {
  return `https://wa.me/?text=${encodeURIComponent(text)}`;
}

/** `sms:?&body=` is understood by both Android and iOS Messages. */
export function smsLink(text: string): string {
  return `sms:?&body=${encodeURIComponent(text)}`;
}

/** Makes scam links unclickable in a forwarded message: http://bad.top/x becomes bad[.]top/x. */
export function defang(text: string): string {
  return text.replace(/\b(?:https?:\/\/|www\.)(\S+)/gi, (_m, rest: string) => rest.replace(/\./g, '[.]'));
}

/** A short, redacted warning someone can forward to family. Links in it are defanged. */
export function warningText(run: AnalysisRun, redactedExcerpt: string, origin = location.origin): string {
  const a = run.analysis;
  redactedExcerpt = defang(redactedExcerpt);
  const flags = a.redFlags.slice(0, 2).map((f) => f.label).join(', ');
  const lines = [
    `⚠️ ${VERDICT_LABEL[a.verdict]}: ${CATEGORY_LABEL[a.category] ?? a.category} (risk ${a.riskScore}/100)`,
    redactedExcerpt ? `"${redactedExcerpt}"` : '',
    flags ? `Signs: ${flags}.` : '',
    a.verdict === 'safe' ? '' : "Don't tap links, share OTPs or send money. If you already did, call 1930 (India) or your bank now.",
    `Checked with Chaukas: ${origin}`,
  ];
  return lines.filter(Boolean).join('\n');
}

/** A warning someone in a group shared, ready to forward on WhatsApp or anywhere. */
export function alertForwardText(a: { verdict: string; riskScore: number; category: string; summary: string; excerpt: string; byName: string }, origin = location.origin): string {
  const verdict = VERDICT_LABEL[a.verdict as keyof typeof VERDICT_LABEL] ?? a.verdict;
  return [
    `⚠️ ${verdict}: ${CATEGORY_LABEL[a.category] ?? a.category} (risk ${a.riskScore}/100), spotted by ${a.byName}`,
    a.excerpt ? `"${defang(a.excerpt)}"` : '',
    defang(a.summary),
    a.verdict === 'safe' ? '' : "Don't tap links, share OTPs or send money. If you already did, call 1930 (India) or your bank now.",
    `Checked with Chaukas: ${origin}`,
  ]
    .filter(Boolean)
    .join('\n');
}

/** Reads a circle code from a route param, or returns '' when it is not one. */
export function codeFromParam(param?: string): string {
  const code = (param ?? '').trim().toUpperCase();
  return /^[A-Z0-9]{6}$/.test(code) ? code : '';
}

/** Personal Chaukas IDs look like `riya-4821`: a readable part from the name and four digits. */
export const HANDLE_RE = /^[a-z0-9]{2,16}-[0-9]{4}$/;

// Devanagari, Bengali, Tamil and Telugu share one layout, so one table (by offset in the block)
// spells names from all four in Latin letters: नानी -> nani, রাজেশ -> rajesh, பாபா -> papa.
const VOWELS: Record<number, string> = { 0x05: 'a', 0x06: 'a', 0x07: 'i', 0x08: 'i', 0x09: 'u', 0x0a: 'u', 0x0b: 'ri', 0x0e: 'e', 0x0f: 'e', 0x10: 'ai', 0x12: 'o', 0x13: 'o', 0x14: 'au' };
const SIGNS: Record<number, string> = { 0x3e: 'a', 0x3f: 'i', 0x40: 'i', 0x41: 'u', 0x42: 'u', 0x43: 'ri', 0x46: 'e', 0x47: 'e', 0x48: 'ai', 0x4a: 'o', 0x4b: 'o', 0x4c: 'au', 0x4d: '' };
const CONS = 'k kh g gh n ch chh j jh ny t th d dh n t th d dh n n p ph b bh m y r r l l l v sh sh s h'.split(' ');
const BLOCKS = [0x0900, 0x0980, 0x0b80, 0x0c00];

export function toLatin(name: string): string {
  let out = '';
  const chars = [...name];
  chars.forEach((ch, i) => {
    const cp = ch.codePointAt(0)!;
    const base = BLOCKS.find((b) => cp >= b && cp < b + 0x80);
    if (base === undefined) return void (out += ch);
    const off = cp - base;
    if (off >= 0x15 && off <= 0x39) {
      out += CONS[off - 0x15];
      const next = chars[i + 1]?.codePointAt(0) ?? 0;
      const nextOff = next >= base && next < base + 0x80 ? next - base : -1;
      // Inherent "a" unless a vowel sign follows. Hindi, Marathi and Bengali also drop it at the end
      // of a word (राजेश -> rajesh), except after a joined consonant (कृष्ण -> krishna). Tamil and
      // Telugu keep it (నాన్న -> nanna).
      const prev = chars[i - 1]?.codePointAt(0) ?? 0;
      const joined = prev - base === 0x4d;
      const endOfWord = nextOff === -1 || nextOff === 0x01 || nextOff === 0x02;
      const drops = (base === 0x0900 || base === 0x0980) && endOfWord && !joined && out.length > 2;
      if (!(nextOff in SIGNS) && !drops) out += 'a';
    } else if (off in VOWELS) out += VOWELS[off];
    else if (off in SIGNS) out += SIGNS[off];
    else if (off === 0x01 || off === 0x02) out += 'n';
  });
  return out;
}

/** The readable part of an ID: the name someone gave, in plain Latin letters. */
export function nameStem(name: string): string {
  return toLatin(name)
    .normalize('NFKD')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
    .slice(0, 16);
}

/** A fresh ID for a display name: the name plus four digits, like `papa-4821`. */
export function makeHandle(name: string, random: () => number = Math.random): string {
  const stem = nameStem(name);
  const digits = String(Math.floor(random() * 10000)).padStart(4, '0');
  return `${stem.length >= 2 ? stem : 'friend'}-${digits}`;
}

/** Whether an ID still matches the name (so a renamed person gets a matching one). */
export function handleFitsName(handle: string, name: string): boolean {
  const stem = nameStem(name);
  return stem.length < 2 ? handle.startsWith('friend-') : handle.startsWith(`${stem}-`);
}

/** Cleans what someone typed or scanned: "@Riya-4821", a full link, or spaces all work. */
export function normalizeHandle(input: string): string {
  const raw = input.trim().toLowerCase();
  const fromLink = raw.match(/#\/circle\/@?([a-z0-9-]+)/);
  const h = (fromLink ? fromLink[1] : raw).replace(/^@/, '').replace(/\s+/g, '');
  return HANDLE_RE.test(h) ? h : '';
}

/** The link inside someone's personal QR code. Opening it offers to add them to a circle. */
export function personUrl(handle: string, origin = location.origin): string {
  return `${origin}/#/circle/@${handle}`;
}

/** The handle in a `#/circle/@riya-4821` route, or ''. */
export function handleFromParam(param?: string): string {
  return param?.startsWith('@') ? normalizeHandle(param) : '';
}

/**
 * The warning for "Send to WhatsApp" on a risky result: the verdict, a short excerpt with
 * personal details already removed and links made unclickable, and what not to do.
 */
export function resultWhatsAppText(a: { verdict: string; riskScore: number; category: string; summary: string }, redactedExcerpt: string, origin = location.origin): string {
  const verdict = VERDICT_LABEL[a.verdict as keyof typeof VERDICT_LABEL] ?? a.verdict;
  return [
    `⚠️ ${verdict}: ${CATEGORY_LABEL[a.category] ?? a.category} (risk ${a.riskScore}/100)`,
    redactedExcerpt ? `"${defang(redactedExcerpt)}"` : '',
    defang(a.summary),
    "Don't tap links, share OTPs or send money. If you already did, call 1930 (India) or your bank now.",
    `Checked with Chaukas: ${origin}`,
  ]
    .filter(Boolean)
    .join('\n');
}
