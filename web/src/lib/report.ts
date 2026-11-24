import type { AnalysisRun } from '../ai/types';
import { CATEGORY_LABEL, VERDICT_LABEL } from './format';

export interface Region {
  code: string;
  name: string;
  places: { label: string; href: string; detail: string }[];
}

/**
 * Official places to report scams. Links only, nothing is sent automatically.
 * Adding a country is one entry here (and, if it has a fraud hotline, a line in recoverySteps).
 */
export const REGIONS: Region[] = [
  {
    code: 'IN',
    name: 'India',
    places: [
      { label: 'Call 1930', href: 'tel:1930', detail: 'National Cyber Crime Helpline. Call straight away if you lost money' },
      { label: 'cybercrime.gov.in', href: 'https://cybercrime.gov.in/', detail: 'File a cyber crime complaint online' },
      { label: 'Chakshu (Sanchar Saathi)', href: 'https://sancharsaathi.gov.in/sfc/', detail: 'Report fraud calls, SMS or WhatsApp within 30 days' },
      { label: 'SMS 1909', href: 'sms:1909', detail: 'Report spam calls and SMS to your telecom operator (TRAI DND)' },
    ],
  },
  {
    code: 'US',
    name: 'United States',
    places: [
      { label: 'ReportFraud.ftc.gov', href: 'https://reportfraud.ftc.gov/', detail: 'Federal Trade Commission' },
      { label: 'Forward texts to 7726', href: 'sms:7726', detail: 'Spells SPAM, free on all major carriers' },
      { label: 'IC3', href: 'https://www.ic3.gov/', detail: 'FBI internet crime, if you lost money' },
    ],
  },
  {
    code: 'GB',
    name: 'United Kingdom',
    places: [
      { label: 'Forward texts to 7726', href: 'sms:7726', detail: 'Free, reports to your network' },
      { label: 'report@phishing.gov.uk', href: 'mailto:report@phishing.gov.uk', detail: 'Forward scam emails' },
      { label: 'Report Fraud (Action Fraud)', href: 'https://www.actionfraud.police.uk/', detail: 'If you lost money, or call 0300 123 2040' },
    ],
  },
  {
    code: 'CA',
    name: 'Canada',
    places: [
      { label: 'Canadian Anti-Fraud Centre', href: 'https://antifraudcentre-centreantifraude.ca/', detail: 'Or call 1-888-495-8501' },
      { label: 'Forward texts to 7726', href: 'sms:7726', detail: 'Reports to your carrier' },
    ],
  },
  {
    code: 'AU',
    name: 'Australia',
    places: [
      { label: 'Scamwatch', href: 'https://www.scamwatch.gov.au/report-a-scam', detail: 'National Anti-Scam Centre' },
      { label: 'ReportCyber', href: 'https://www.cyber.gov.au/report-and-recover/report', detail: 'If you lost money' },
    ],
  },
  {
    code: 'OTHER',
    name: 'Another country',
    places: [
      { label: 'Your bank', href: '', detail: 'Call the number on the back of your card, never one from the message' },
      { label: 'econsumer.gov', href: 'https://econsumer.gov/', detail: 'International scam reporting, shared with consumer protection agencies in many countries' },
      { label: 'Local police', href: '', detail: 'Many countries have an online cyber crime report. Search for your national police site' },
    ],
  },
];

/**
 * What to do in the first hour after money, a code or access was handed over.
 * Speed matters: banks can often freeze a transfer reported within hours.
 */
export function recoverySteps(region: string): { title: string; detail: string }[] {
  const helpline =
    region === 'IN'
      ? { title: 'Call 1930 right now', detail: 'The National Cyber Crime Helpline can ask banks to freeze the money while it is still moving. The first hour matters most.' }
      : region === 'GB'
        ? { title: 'Call your bank on 159', detail: 'Stop Scams UK connects you to most UK banks, so they can try to stop the payment.' }
        : { title: 'Call your bank or card company now', detail: 'Use the number on the back of your card. Ask them to stop the payment and block the card.' };
  return [
    helpline,
    { title: region === 'IN' ? 'Block your card, UPI and net banking' : 'Block your card and online banking', detail: 'Use your bank app or call the number on your card. Do not call any number from the message.' },
    { title: 'Change passwords you shared', detail: 'Start with email and banking. Turn on two-step verification. Never share a one-time code again, even with "the bank".' },
    { title: 'Remove any app they asked you to install', detail: 'Screen-sharing apps like AnyDesk or TeamViewer give them control. Uninstall them and restart your phone.' },
    { title: 'Keep the evidence', detail: 'Take screenshots of the message, the number and any transaction ID before you delete anything.' },
    { title: region === 'IN' ? 'File a complaint on cybercrime.gov.in' : 'Report it officially', detail: 'Use the links above. A complaint number helps your bank and the police trace the money.' },
  ];
}

export function guessRegion(): string {
  const lang = navigator.language || '';
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
  if (/-GB$/i.test(lang) || tz === 'Europe/London') return 'GB';
  if (/-IN$/i.test(lang) || /^(hi|bn|ta|te|mr|gu|kn|ml|pa)\b/i.test(lang) || tz === 'Asia/Kolkata' || tz === 'Asia/Calcutta') return 'IN';
  if (/-CA$/i.test(lang) || tz.startsWith('America/Toronto') || tz.startsWith('America/Vancouver')) return 'CA';
  if (/-AU$/i.test(lang) || tz.startsWith('Australia/')) return 'AU';
  if (/-US$/i.test(lang) || tz.startsWith('America/')) return 'US';
  return 'OTHER';
}

/** A plain-text summary someone can paste into a report form or send to their bank. */
export function buildReport(run: AnalysisRun): string {
  const a = run.analysis;
  return [
    `Suspicious message report (prepared with Chaukas)`,
    `Date checked: ${new Date(run.createdAt).toLocaleString()}`,
    `Assessment: ${VERDICT_LABEL[a.verdict]} (risk ${a.riskScore}/100), ${CATEGORY_LABEL[a.category]}`,
    `Summary: ${a.summary}`,
    a.redFlags.length ? `Warning signs:\n${a.redFlags.map((f) => `- ${f.label}${f.evidence ? `: "${f.evidence}"` : ''}`).join('\n')}` : '',
    `Original message:\n${run.input.trim() || '(screenshot)'}`,
  ]
    .filter(Boolean)
    .join('\n\n');
}

export function downloadFile(name: string, content: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
