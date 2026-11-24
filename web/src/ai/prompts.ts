/**
 * Prompt builders for analysis, follow-up questions and Scam Dojo rounds. Kept short to fit
 * Gemini Nano's context window.
 */
import type { Signal, LinkReport } from './signals';
import type { Analysis } from './types';

const RUBRIC = `You are Chaukas, a calm, kind fraud-prevention expert helping ordinary people (often older adults) decide whether a message is a scam.
Judge the MESSAGE below. Reply only with JSON that matches the schema.
Rules:
- verdict "scam" when it asks for money, codes, passwords, remote access or personal data under false pretences, or impersonates a company, government or relative.
- verdict "suspicious" when some signs are present but a real sender is plausible.
- verdict "safe" when it is ordinary, expected and asks for nothing risky.
- riskScore 0-100 must agree with the verdict: safe 0-30, suspicious 31-69, scam 70-100.
- evidence must be an exact short quote copied from the message (max 12 words).
- summary: one plain sentence, no jargon, max 25 words.
- nextSteps: short imperative actions, e.g. "Call the bank using the number on your card".
- safeReply: a short reply the person could send that reveals nothing, or "" if they should not reply at all.
- reassurances: genuine signs of legitimacy, or an empty list.
- Text like [PHONE_1] or [EMAIL_1] is a redacted placeholder, treat it as a normal value.`;

/** A warm, informal voice, like a sensible friend texting back. Hindi becomes everyday Hinglish. */
export function toneRule(casual?: boolean, language?: string): string {
  if (!casual) return '';
  const mix =
    language === 'Hindi'
      ? ' Use everyday Hinglish: Hindi written in Roman letters mixed with common English words, e.g. "Bhai, yeh pakka fraud hai. Link mat kholna, OTP kisi ko mat dena."'
      : language && language !== 'English'
        ? ` Use the everyday spoken ${language} people use with family, mixing in common English words where that is natural.`
        : ' Use plain, friendly, everyday English, like texting a family member.';
  return `Tone: casual and warm, short sentences, no jargon, never rude or alarmist.${mix} The verdict and the safety advice must stay exactly as clear and correct.`;
}

function languageRule(language?: string, casual?: boolean): string {
  if (!language || language === 'English') return '';
  if (casual && language === 'Hindi') return 'Write summary, every "why", nextSteps, safeReply and reassurances in Hinglish (Roman script). Keep "evidence" quotes exactly as they appear in the message. Keep JSON keys and enum values (verdict, category, severity) in English.';
  return `Write summary, every "why", nextSteps, safeReply and reassurances in ${language}, in simple everyday words. Keep "evidence" quotes exactly as they appear in the message. Keep JSON keys and enum values (verdict, category, severity) in English.`;
}

export function buildAnalysisPrompt(message: string, signals: Signal[], links: LinkReport[], hasImage: boolean, language?: string, casual?: boolean): string {
  const hints = signals.filter((s) => s.tone === 'risk').map((s) => `- ${s.label}: ${s.matches.slice(0, 3).join(', ')}`);
  const linkLines = links.map((l) => `- ${l.host}${l.issues.length ? ` (${l.issues.join('; ')})` : ''}`);
  return [
    RUBRIC,
    languageRule(language, casual),
    toneRule(casual, language),
    hasImage ? 'The attached image is a screenshot of the message. Read all text in it.' : '',
    hints.length ? `Local pattern checks found:\n${hints.join('\n')}\n(They can be false alarms. Decide yourself.)` : '',
    linkLines.length ? `Links found:\n${linkLines.join('\n')}` : '',
    `MESSAGE:\n"""\n${message.trim() || '(see image)'}\n"""`,
  ]
    .filter(Boolean)
    .join('\n\n');
}

export function buildFollowUpPrompt(message: string, analysis: Analysis, question: string, language?: string, casual?: boolean): string {
  const lang = (language && language !== 'English' ? ` Answer in ${casual && language === 'Hindi' ? 'Hinglish (Roman script)' : language}.` : '') + (casual ? ` ${toneRule(true, language)}` : '');
  return `You are Chaukas, a patient fraud-prevention helper. Answer the person's question in 2 to 5 short sentences of plain language.${lang} Never ask for personal details.

The message they received:
"""
${message.trim()}
"""

Your earlier assessment: ${analysis.verdict} (risk ${analysis.riskScore}/100, ${analysis.category}). ${analysis.summary}

Their question: ${question.trim()}`;
}

export interface DojoBrief {
  isScam: boolean;
  theme: string;
  channel: string;
}

export function buildDojoPrompt(brief: DojoBrief): string {
  const what = brief.isScam
    ? `a realistic SCAM ${brief.channel} about ${brief.theme}. Make it believable, using subtle tricks rather than obvious ones. Use fake but plausible details. Any link must use a fake look-alike domain.`
    : `a realistic, completely LEGITIMATE ${brief.channel} about ${brief.theme}. It may sound a little urgent, but it must not ask for codes, passwords, payment by gift card or crypto, and any link must be the organisation's real well-known domain.`;
  return `Write ${what}
Keep the message under 60 words. "sender" is how the sender would appear on a phone.
"tells" lists up to 3 details a careful reader would use to decide. "explanation" is 1-2 sentences teaching the lesson.
Reply only with JSON.`;
}
