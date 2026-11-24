/**
 * Preview mode: used only when no Firebase config is present, so people can
 * explore the interface before connecting a project. Answers are built from
 * the local pattern checks and are clearly labelled "Preview" in the UI.
 */
import { computeSignals } from './signals';
import type { DojoBrief } from './prompts';
import type { Analysis, Category } from './types';

const CATEGORY_BY_SIGNAL: Record<string, Category> = {
  delivery: 'delivery_fee',
  job_scam: 'job_offer',
  off_platform: 'job_offer',
  family: 'family_emergency',
  remote_access: 'tech_support',
  investment: 'investment_crypto',
  prize: 'prize_lottery',
  credentials: 'phishing',
  odd_payment: 'bank_or_payment',
  india_kyc: 'phishing',
  upi_pin: 'bank_or_payment',
  upi_qr: 'bank_or_payment',
  digital_arrest: 'government_tax',
  power_cut: 'impersonation',
};

export function previewAnalysis(text: string, hasImage: boolean, variant = 0): Analysis {
  const { signals, score: base } = computeSignals(text);
  const risk = signals.filter((s) => s.tone === 'risk');
  // Several independent warning signs together are far stronger than any one alone.
  const combined = risk.length >= 2 ? Math.round(base * 1.25) : base;
  const score = Math.min(100, Math.max(hasImage && !text ? 50 : 6, combined + variant * 6));
  const verdict = score >= 70 ? 'scam' : score > 30 ? 'suspicious' : 'safe';
  const top = [...risk].sort((a, b) => b.weight - a.weight);
  const category = verdict === 'safe' ? 'legitimate' : (top.map((s) => CATEGORY_BY_SIGNAL[s.id]).find(Boolean) ?? 'phishing');
  return {
    verdict,
    riskScore: score,
    category,
    summary:
      verdict === 'scam'
        ? `This has several hallmarks of a scam: ${top
            .slice(0, 2)
            .map((s) => s.label.replace(/^[A-Z](?=[a-z])/, (c) => c.toLowerCase()))
            .join(' and ')}.`
        : verdict === 'suspicious'
          ? 'Some warning signs are present. Verify with the sender through a channel you already trust.'
          : 'Nothing here matches common scam patterns, but stay alert if anything feels off.',
    redFlags: top.slice(0, 4).map((s) => ({ label: s.label, evidence: s.matches[0] ?? '', why: s.detail })),
    reassurances: verdict === 'safe' ? ['It does not ask for money, codes or personal details.'] : [],
    nextSteps:
      verdict === 'safe'
        ? ['Reply as you normally would', 'Still avoid sharing one-time codes with anyone']
        : ['Do not tap any links or call numbers in the message', 'Contact the organisation using its official app or website', 'Block and report the sender'],
    safeReply: verdict === 'scam' ? '' : 'Thanks, I will contact you through your official website.',
  };
}

const DOJO_SAMPLES: Record<string, { sender: string; message: string; tells: string[]; explanation: string }[]> = {
  scam: [
    {
      sender: '+1 (530) 555-0142',
      message: 'USPS: Your parcel is on hold due to an incomplete address. Update within 12 hours to avoid return: https://usps-redelivery-help.top/track',
      tells: ['".top" domain that is not usps.com', '12-hour deadline', 'Unexpected text from a random number'],
      explanation: 'Delivery companies do not text you payment or address links from personal numbers. Check tracking in the official app.',
    },
    {
      sender: 'Unknown number',
      message: "Hi Mum, it's me. Dropped my phone in the bath, this is my new number. Can you help me pay a bill today? I'll pay you back tomorrow x",
      tells: ['"New number" story', 'Money request on the same day', 'No name used'],
      explanation: 'Call your child on their old number or ask a question only they would know before sending anything.',
    },
  ],
  legit: [
    {
      sender: 'Amazon',
      message: 'Your package with 2 items was delivered to your front door. Track at amazon.com/orders. Not you? Contact us in the Amazon app.',
      tells: ['Real amazon.com domain', 'No payment or code requested', 'Points you to the app you already use'],
      explanation: 'It confirms something you expect and asks for nothing. Still, open the app yourself rather than tapping links.',
    },
  ],
};

export function previewDojo(brief: DojoBrief) {
  const pool = brief.isScam ? DOJO_SAMPLES.scam : DOJO_SAMPLES.legit;
  return pool[Math.floor(Math.random() * pool.length)];
}
