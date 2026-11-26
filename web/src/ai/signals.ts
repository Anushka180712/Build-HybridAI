/**
 * Instant, deterministic signals computed locally in under a millisecond.
 * They show up while the model is still thinking, they power preview mode,
 * and they are passed to the model as hints.
 */
export type SignalTone = 'risk' | 'info';

export interface Signal {
  id: string;
  label: string;
  detail: string;
  weight: number;
  tone: SignalTone;
  matches: string[];
}

export interface LinkReport {
  url: string;
  host: string;
  issues: string[];
}

interface PhraseRule {
  id: string;
  label: string;
  detail: string;
  weight: number;
  re: RegExp;
}

const PHRASES: PhraseRule[] = [
  {
    id: 'urgency',
    label: 'Pressure to act fast',
    detail: 'Scammers rush you so you do not stop to check.',
    weight: 18,
    re: /\b(urgent(ly)?|immediately|right away|within \d+ ?(hours?|hrs?|minutes?|mins?)|final (notice|warning|reminder)|act now|last chance|expires? (today|soon|tonight)|today only|avoid (suspension|penalt(y|ies)|legal action))\b/gi,
  },
  {
    id: 'threat',
    label: 'Threat or penalty',
    detail: 'Threats of arrest, fines or account closure are a classic lever.',
    weight: 16,
    re: /\b(suspend(ed)?|locked|deactivat(ed|ion)|arrest|warrant|lawsuit|legal action|penalt(y|ies)|fine[sd]?|terminated|blocked)\b/gi,
  },
  {
    id: 'odd_payment',
    label: 'Unusual way to pay',
    detail: 'Real companies never ask for gift cards, crypto or wire transfers.',
    weight: 26,
    re: /\b(gift ?cards?|itunes card|google play card|steam card|bitcoin|btc|usdt|crypto(currency)?|wire transfer|western union|moneygram|zelle|cash ?app|venmo)\b/gi,
  },
  {
    id: 'credentials',
    label: 'Asks for codes or passwords',
    detail: 'No real service will ask you to share a one-time code or password.',
    weight: 24,
    re: /\b(password|passcode|one[- ]time (code|password)|otp|verification code|security code|pin( number)?|social security|ssn|login details|confirm your (identity|account|details))\b/gi,
  },
  {
    id: 'prize',
    label: 'Too good to be true',
    detail: 'Unexpected winnings or refunds are bait.',
    weight: 18,
    re: /\b(you('ve| have)? won|winner|congratulations|claim (your|now)|free (gift|iphone|money)|prize|lottery|unclaimed|refund (is )?(pending|available)|cash ?back reward)\b/gi,
  },
  {
    id: 'delivery',
    label: 'Parcel or toll fee',
    detail: 'Fake "unpaid fee" texts for parcels and tolls are very common.',
    weight: 14,
    re: /\b(parcel|package|delivery|shipment|redeliver(y)?|customs fee|toll|unpaid (fee|balance|toll)|address (is )?incomplete)\b/gi,
  },
  {
    id: 'family',
    label: '"New number" family story',
    detail: 'Impersonating a child or relative who "lost their phone" is a fast-growing scam.',
    weight: 20,
    re: /\b(hi (mum|mom|dad)|new number|lost my phone|broke my phone|phone (is )?broken|this is my new|don'?t tell (mom|dad|anyone))\b/gi,
  },
  {
    id: 'secrecy',
    label: 'Asks you to keep it secret',
    detail: 'Isolation stops you from asking someone you trust.',
    weight: 14,
    re: /\b(keep (this|it) (between us|secret|confidential)|do not tell|don'?t tell|tell no one)\b/gi,
  },
  {
    id: 'remote_access',
    label: 'Remote access or tech support',
    detail: 'Scammers ask you to install apps like AnyDesk to take over your device.',
    weight: 22,
    re: /\b(anydesk|teamviewer|remote access|ultraviewer|virus (detected|found)|your (computer|device) (is|has been) (infected|hacked)|call (microsoft|apple) support)\b/gi,
  },
  {
    id: 'investment',
    label: 'Guaranteed returns',
    detail: 'Guaranteed profits do not exist.',
    weight: 20,
    re: /\b(guaranteed (returns?|profit)|double your (money|investment)|\d{2,3}% (returns?|profit)|trading platform|investment opportunity|risk[- ]free)\b/gi,
  },
  {
    id: 'job_scam',
    label: 'Easy-money job offer',
    detail: 'Real employers do not pay hundreds a day for "rating products" or liking videos with no experience.',
    weight: 24,
    re: /\b(part[- ]time (job|role|work)|work from home|rating products|like (videos|posts) and earn|no experience (needed|required)|daily (income|payout)|task[- ]based (job|work)|[$₹]\s?\d[\d,]*\s?(-|to)\s?[$₹]?\s?\d[\d,]*\s?(\/|per |a )day)/gi,
  },
  {
    id: 'off_platform',
    label: 'Moves you to another app',
    detail: 'Scammers push you to Telegram or a private chat where nothing is moderated.',
    weight: 16,
    re: /\b(add me on (telegram|whatsapp|signal)|telegram @?\w+|contact (me|us) on telegram|t\.me\/\w+)/gi,
  },
];

PHRASES.push(
  {
    id: 'india_kyc',
    label: 'KYC, PAN or Aadhaar "update"',
    detail: 'Banks never block your account over a texted KYC link or ask for your OTP.',
    weight: 26,
    re: /\b(kyc|e-?kyc|pan( card)?|aadhaa?r|sbi|yono|hdfc|icici|pnb|axis|bank account|khata)\b.{0,45}\b(update|updat(ed|ion)|expire[ds]?|block(ed)?|suspend(ed|ion)?|verify|link|deactivat(ed|ion)|hold)\b/gi,
  },
  {
    id: 'upi_pin',
    label: 'UPI PIN or collect request',
    detail: 'You never need your UPI PIN to receive money. Entering it always sends money.',
    weight: 26,
    re: /\b(upi pin|collect request|enter (your )?pin|scan (this|the) qr|sent .{0,25} by mistake|galti se .{0,20}(bhej|paise)|wapas (kare|bhejo|karo)|approve request|cashback (approved|refund))\b/gi,
  },
  {
    id: 'upi_qr',
    label: 'Payment QR code',
    detail: 'The QR code is a UPI payment request. Scanning it sends money from you, it never pays you.',
    weight: 54,
    re: /upi:\/\/pay\S*/gi,
  },
  {
    id: 'digital_arrest',
    label: '"Digital arrest" or police threat',
    detail: 'Police, CBI, customs or TRAI never arrest anyone over a video call or ask for money to settle a case.',
    weight: 26,
    re: /\b(digital arrest|cbi|narcotics|customs (department|officer)|trai|your (number|sim) will be (blocked|disconnected)|money laundering)\b/gi,
  },
  {
    id: 'power_cut',
    label: 'Power cut threat',
    detail: 'Electricity boards do not text you a personal number to avoid disconnection tonight.',
    weight: 26,
    re: /\b(electricity|power|light|bijli|current|bill).{0,35}(disconnect(ed)?|cut|suspended|off|unpaid|pending|baki|officer|9\.\d{2} ?(pm|am))\b/gi,
  },
  {
    id: 'call_officer',
    label: 'Contact officer / mobile number',
    detail: 'Scammers tell you to urgently call a personal mobile number pretending to be an officer.',
    weight: 22,
    re: /\b(contact|call|officer|dial|sampark (kare|kariye|karo)|helpline).{0,30}(\+?91|0)?[6-9]\d{9}\b/gi,
  },
  {
    id: 'scam_keyword',
    label: 'Scam or fraud keyword',
    detail: 'Direct reference to scams, fraud, phishing or fake schemes.',
    weight: 38,
    re: /\b(scam(s|mer|ming)?|fraud(ulent)?|phishing|fake|fraudster|cheat(ing)?)\b/gi,
  },
  {
    id: 'recharge',
    label: 'Free recharge or data offer',
    detail: 'Promises of free mobile recharges or data are classic phishing hooks.',
    weight: 36,
    re: /\b(free (recharge|data|internet|5g|offer)|recharge (offer|approved|free)|5g (free|offer|recharge)|free (jio|airtel|vi))\b/gi,
  },
  {
    id: 'traffic_challan',
    label: 'Traffic fine or e-challan notice',
    detail: 'Fake e-challan notices pressure you to pay bogus traffic fines.',
    weight: 36,
    re: /\b(e-?challan|traffic (fine|police|challan)|pay fine|parivahan|pending challan)\b/gi,
  },
);

const SHORTENERS = ['bit.ly', 'tinyurl.com', 't.co', 'goo.gl', 'ow.ly', 'is.gd', 'buff.ly', 'rebrand.ly', 'cutt.ly', 'shorturl.at', 'rb.gy', 't.ly', 's.id'];
const RISKY_TLDS = ['top', 'xyz', 'icu', 'click', 'buzz', 'rest', 'cfd', 'sbs', 'live', 'shop', 'loan', 'zip', 'mov', 'cyou', 'vip', 'info', 'online', 'site', 'support'];
const BRANDS: Record<string, string[]> = {
  paypal: ['paypal.com'],
  amazon: ['amazon.com', 'amazon.co.uk', 'amazon.in', 'amazon.de', 'amazon.ca', 'amazon.com.au'],
  apple: ['apple.com', 'icloud.com'],
  microsoft: ['microsoft.com', 'live.com', 'outlook.com'],
  netflix: ['netflix.com'],
  usps: ['usps.com'],
  fedex: ['fedex.com'],
  ups: ['ups.com'],
  dhl: ['dhl.com'],
  irs: ['irs.gov'],
  chase: ['chase.com'],
  wellsfargo: ['wellsfargo.com'],
  bankofamerica: ['bankofamerica.com'],
  google: ['google.com'],
  whatsapp: ['whatsapp.com'],
  instagram: ['instagram.com'],
  coinbase: ['coinbase.com'],
};

const LOOKALIKE: Record<string, string> = { '0': 'o', '1': 'l', '3': 'e', '4': 'a', '5': 's', '7': 't', rn: 'm', vv: 'w' };

function normalizeLookalike(s: string): string {
  let out = s.toLowerCase();
  for (const [k, v] of Object.entries(LOOKALIKE)) out = out.split(k).join(v);
  return out.replace(/[-_.]/g, '');
}

const URL_RE = /\b((?:https?:\/\/|www\.)[^\s<>"')\]]+|[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|top|xyz|icu|click|info|online|site|live|shop|co|io|me|ly|gov|vip|support)(?:\/[^\s<>"')\]]*)?)/gi;

export function extractLinks(text: string): LinkReport[] {
  const seen = new Set<string>();
  const reports: LinkReport[] = [];
  for (const raw of text.match(URL_RE) ?? []) {
    const cleaned = raw.replace(/[.,;:!?]+$/, '');
    if (seen.has(cleaned) || cleaned.includes('@')) continue;
    seen.add(cleaned);
    let host = '';
    let protocol = '';
    try {
      const u = new URL(/^https?:\/\//i.test(cleaned) ? cleaned : `http://${cleaned}`);
      host = u.hostname.toLowerCase();
      protocol = /^https?:\/\//i.test(cleaned) ? u.protocol : '';
    } catch {
      continue;
    }
    const issues: string[] = [];
    const tld = host.split('.').pop() ?? '';
    const registrable = host.split('.').slice(-2).join('.');
    if (SHORTENERS.includes(host)) issues.push('Link shortener hides the real destination');
    if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) issues.push('Points to a raw IP address, not a named site');
    if (host.startsWith('xn--') || host.includes('.xn--')) issues.push('Uses look-alike international characters (punycode)');
    if (RISKY_TLDS.includes(tld)) issues.push(`".${tld}" domains are cheap and heavily used for scams`);
    if (protocol === 'http:') issues.push('Not encrypted (http, not https)');
    if (host.split('.').length > 4) issues.push('Unusually deep sub-domain chain');
    const flat = normalizeLookalike(host);
    for (const [brand, official] of Object.entries(BRANDS)) {
      const isOfficial = official.some((d) => host === d || host.endsWith(`.${d}`));
      if (!isOfficial && flat.includes(brand)) {
        issues.push(`Mentions "${brand}" but is not ${official[0]}`);
        break;
      }
    }
    if (/(-|\.)(verify|secure|login|update|account|billing|support|help)(-|\.)/.test(`.${host}.`) && !Object.values(BRANDS).flat().includes(registrable)) {
      issues.push('Security-sounding words in the address are a common trick');
    }
    reports.push({ url: cleaned, host, issues });
  }
  return reports;
}

export function computeSignals(text: string): { signals: Signal[]; links: LinkReport[]; score: number } {
  const signals: Signal[] = [];
  for (const rule of PHRASES) {
    const matches = Array.from(new Set((text.match(rule.re) ?? []).map((m) => m.trim())));
    if (matches.length) {
      signals.push({ id: rule.id, label: rule.label, detail: rule.detail, weight: rule.weight, tone: 'risk', matches });
    }
  }
  const links = extractLinks(text);
  const risky = links.filter((l) => l.issues.length > 0);
  if (risky.length) {
    signals.push({
      id: 'links',
      label: risky.length === 1 ? 'Suspicious link' : `${risky.length} suspicious links`,
      detail: risky.flatMap((l) => l.issues).slice(0, 2).join('. '),
      weight: Math.min(34, 18 + risky.reduce((n, l) => n + l.issues.length, 0) * 5),
      tone: 'risk',
      matches: risky.map((l) => l.url),
    });
  } else if (links.length) {
    signals.push({
      id: 'links_ok',
      label: links.length === 1 ? 'Contains a link' : `Contains ${links.length} links`,
      detail: 'Nothing obviously wrong with the address, but check before tapping.',
      weight: 0,
      tone: 'info',
      matches: links.map((l) => l.url),
    });
  }
  // A message whose links all check out is a little more likely to be genuine.
  const raw = Math.max(0, signals.reduce((n, s) => n + s.weight, 0) - (links.length && !risky.length ? 10 : 0));
  const score = Math.round(100 * (1 - Math.exp(-raw / 45)));
  return { signals, links, score };
}
