import type { AnalysisRun } from '../ai/types';
import { CATEGORY_LABEL, VERDICT_LABEL } from './format';

export interface Region {
  code: string;
  name: string;
  places: { label: string; href: string; detail: string }[];
}

/**
 * Official places to report scams. Links only, nothing is sent automatically.
 * India official cyber crime helplines and reporting portals.
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
];

/**
 * What to do in the first hour after money, a code or access was handed over.
 * Speed matters: banks can often freeze a transfer reported within hours.
 */
export function recoverySteps(region: string = 'IN'): { title: string; detail: string }[] {
  const helpline =
    region === 'IN'
      ? { title: 'Call 1930 right now', detail: 'The National Cyber Crime Helpline can ask banks to freeze the money while it is still moving. The first hour matters most.' }
      : { title: 'Call your bank or card company now', detail: 'Use the number on the back of your card. Ask them to stop the payment and block the card.' };
  return [
    helpline,
    { title: 'Block your card, UPI and net banking', detail: 'Use your bank app or call the number on your card. Do not call any number from the message.' },
    { title: 'Change passwords you shared', detail: 'Start with email and banking. Turn on two-step verification. Never share a one-time code again, even with "the bank".' },
    { title: 'Remove any app they asked you to install', detail: 'Screen-sharing apps like AnyDesk or TeamViewer give them control. Uninstall them and restart your phone.' },
    { title: 'Keep the evidence', detail: 'Take screenshots of the message, the number and any transaction ID before you delete anything.' },
    { title: 'File a complaint on cybercrime.gov.in', detail: 'Use the links above. A complaint number helps your bank and the police trace the money.' },
  ];
}

export function guessRegion(): string {
  return 'IN';
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
