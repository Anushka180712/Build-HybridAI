/**
 * Labels and small text helpers.
 */
export function timeAgo(ts: number): string {
  const s = Math.max(1, Math.round((Date.now() - ts) / 1000));
  if (s < 60) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  if (d < 7) return `${d} d ago`;
  return new Date(ts).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export const CATEGORY_LABEL: Record<string, string> = {
  phishing: 'Phishing',
  impersonation: 'Impersonation',
  delivery_fee: 'Parcel or toll fee',
  bank_or_payment: 'Bank or payment',
  tech_support: 'Tech support',
  family_emergency: 'Family emergency',
  romance: 'Romance',
  investment_crypto: 'Investment or crypto',
  job_offer: 'Job offer',
  prize_lottery: 'Prize or lottery',
  government_tax: 'Government or tax',
  marketplace: 'Marketplace',
  legitimate: 'Looks legitimate',
  other: 'Other',
};

export const VERDICT_LABEL = { safe: 'Looks safe', suspicious: 'Be careful', scam: 'Likely scam' } as const;

export function sourceLabel(source: string): string {
  return source === 'on_device' ? 'On this device' : source === 'in_cloud' ? 'Cloud' : 'Preview';
}

export function excerpt(text: string, n = 140): string {
  const t = text.replace(/\s+/g, ' ').trim();
  return t.length > n ? `${t.slice(0, n - 1)}…` : t;
}
