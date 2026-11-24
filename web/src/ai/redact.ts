/**
 * Local PII redaction. Runs entirely in the browser before any prompt is
 * allowed to leave the device. Placeholders are reversible locally so cloud
 * answers can be mapped back onto the user's original text.
 */
export interface Redaction {
  text: string;
  /** placeholder -> original value */
  map: Record<string, string>;
  count: number;
  kinds: Record<string, number>;
}

interface Rule {
  kind: string;
  re: RegExp;
  test?: (match: string) => boolean;
  /** Only replace a capture group instead of the whole match. */
  group?: number;
}

function luhn(digits: string): boolean {
  const d = digits.replace(/\D/g, '');
  if (d.length < 13 || d.length > 19) return false;
  let sum = 0;
  let dbl = false;
  for (let i = d.length - 1; i >= 0; i--) {
    let n = d.charCodeAt(i) - 48;
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}

const RULES: Rule[] = [
  { kind: 'EMAIL', re: /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi },
  { kind: 'CARD', re: /\b(?:\d[ -]?){13,19}\b/g, test: luhn },
  { kind: 'IBAN', re: /\b[A-Z]{2}\d{2}(?:[ ]?[A-Z0-9]{4}){2,7}(?:[ ]?[A-Z0-9]{1,3})?\b/g },
  { kind: 'SSN', re: /\b\d{3}-\d{2}-\d{4}\b/g },
  // UPI IDs look like emails without a dot in the handle (name@okaxis, 98xxxx@ybl).
  { kind: 'UPI', re: /\b[\w.-]{2,}@[a-z][a-z0-9]{1,15}\b(?![.@\w])/gi },
  // Indian PAN card: five letters, four digits, one letter.
  { kind: 'PAN', re: /\b[A-Z]{5}\d{4}[A-Z]\b/g },
  // Aadhaar: 12 digits, first digit 2-9, often grouped 4-4-4.
  { kind: 'AADHAAR', re: /\b[2-9]\d{3}[\s-]?\d{4}[\s-]?\d{4}\b/g },
  // "482913 is your OTP": the code comes before the keyword.
  {
    kind: 'CODE',
    re: /\b(\d{4,8})\b(?=[\s.,:-]*(?:is|as)\s+(?:your|the)\s+(?:otp|code|one[- ]?time|verification|pin|passcode))/gi,
    group: 1,
  },
  {
    kind: 'CODE',
    re: /\b(?:code|otp|pin|passcode|verification)\b[^\d\n]{0,20}(\d{4,8})\b/gi,
    group: 1,
  },
  // Indian mobiles: +91 98765 43210, 098765-43210, 9876543210.
  { kind: 'PHONE', re: /(?:\+91[\s-]?|\b0|\b)[6-9]\d{4}[\s-]?\d{5}\b/g },
  {
    kind: 'PHONE',
    re: /(?:\+?\d{1,3}[\s.-]?)?(?:\(\d{2,4}\)|\d{2,4})[\s.-]?\d{3,4}[\s.-]?\d{3,4}\b/g,
    test: (m) => m.replace(/\D/g, '').length >= 9,
  },
  { kind: 'ACCOUNT', re: /\b(?:acct|account|a\/c)\.?\s*(?:no\.?|number|#)?\s*[:#]?\s*(\d{6,})\b/gi, group: 1 },
];

/** Query strings often carry personal tokens; keep the host and path only. */
const URL_QUERY = /\b(https?:\/\/[^\s?#]+)[?#][^\s]*/gi;

/** Names the person wants hidden, e.g. their own name, set in Settings. */
export function redact(input: string, extraTerms: string[] = []): Redaction {
  const map: Record<string, string> = {};
  const kinds: Record<string, number> = {};
  const reverse = new Map<string, string>();
  let text = input;

  const placeholder = (kind: string, value: string) => {
    const existing = reverse.get(value);
    if (existing) return existing;
    kinds[kind] = (kinds[kind] ?? 0) + 1;
    const tag = `[${kind}_${kinds[kind]}]`;
    map[tag] = value;
    reverse.set(value, tag);
    return tag;
  };

  text = text.replace(URL_QUERY, (_m, base: string) => `${base}?[QUERY_REMOVED]`);

  for (const term of extraTerms.map((t) => t.trim()).filter((t) => t.length >= 2)) {
    const re = new RegExp(`\\b${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi');
    text = text.replace(re, (m) => placeholder('NAME', m));
  }

  for (const rule of RULES) {
    text = text.replace(rule.re, (match: string, ...groups: unknown[]) => {
      if (match.startsWith('[') && match.endsWith(']')) return match;
      if (rule.group) {
        const g = groups[rule.group - 1];
        if (typeof g !== 'string') return match;
        return match.replace(g, placeholder(rule.kind, g));
      }
      if (rule.test && !rule.test(match)) return match;
      // Keep surrounding whitespace that the phone/card patterns may swallow.
      const lead = match.match(/^\s*/)?.[0] ?? '';
      return lead + placeholder(rule.kind, match.trim());
    });
  }

  const count = Object.keys(map).length;
  return { text, map, count, kinds };
}

/** Puts original values back into model output produced from redacted text. */
export function restore(text: string, map: Record<string, string>): string {
  return text.replace(/\[[A-Z]+_\d+\]/g, (tag) => map[tag] ?? tag);
}
