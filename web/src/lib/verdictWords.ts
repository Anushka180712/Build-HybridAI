/**
 * The few words Chaukas speaks and shows in English and five Indian languages: the verdict
 * itself, three plain "don'ts" and a closing tip. The model explains the details in the chosen
 * language; these fixed lines make sure the most important part is always right.
 */
import type { Verdict } from '../ai/types';

export type UiLang = 'en' | 'hi' | 'bn' | 'ta' | 'te' | 'mr';

export interface VerdictWords {
  verdict: Record<Verdict, string>;
  dont: string[];
  safeTip: string;
  why: string;
}

export const VERDICT_WORDS: Record<UiLang, VerdictWords> = {
  en: {
    verdict: {
      scam: 'Stop. This looks like a scam.',
      suspicious: 'Be careful with this message.',
      safe: 'This looks safe.',
    },
    dont: ['Do not tap any link', 'Do not share an OTP or UPI PIN', 'Do not send money'],
    safeTip: 'If you are still unsure, call the person on a number you already know.',
    why: 'Why',
  },
  hi: {
    verdict: {
      scam: 'रुकिए! यह धोखा लगता है।',
      suspicious: 'सावधान! इस मैसेज पर भरोसा न करें।',
      safe: 'यह सुरक्षित लगता है।',
    },
    dont: ['किसी लिंक पर टैप न करें', 'OTP या UPI PIN किसी को न बताएँ', 'पैसे न भेजें'],
    safeTip: 'फिर भी शक हो, तो उस व्यक्ति को उसके पुराने नंबर पर कॉल करें।',
    why: 'क्यों',
  },
  bn: {
    verdict: {
      scam: 'থামুন! এটা প্রতারণা মনে হচ্ছে।',
      suspicious: 'সাবধান! এই মেসেজে ভরসা করবেন না।',
      safe: 'এটা নিরাপদ মনে হচ্ছে।',
    },
    dont: ['কোনো লিংকে ট্যাপ করবেন না', 'OTP বা UPI PIN কাউকে বলবেন না', 'টাকা পাঠাবেন না'],
    safeTip: 'তবুও সন্দেহ হলে, মানুষটাকে তাঁর চেনা নম্বরে ফোন করুন।',
    why: 'কেন',
  },
  ta: {
    verdict: {
      scam: 'நில்லுங்கள்! இது மோசடி போலத் தெரிகிறது.',
      suspicious: 'கவனம்! இந்த மெசேஜை நம்பாதீர்கள்.',
      safe: 'இது பாதுகாப்பானதாகத் தெரிகிறது.',
    },
    dont: ['எந்த லிங்கையும் தட்டாதீர்கள்', 'OTP அல்லது UPI PIN-ஐ யாரிடமும் சொல்லாதீர்கள்', 'பணம் அனுப்பாதீர்கள்'],
    safeTip: 'இன்னும் சந்தேகம் இருந்தால், அவருக்குத் தெரிந்த பழைய எண்ணில் அழையுங்கள்.',
    why: 'ஏன்',
  },
  te: {
    verdict: {
      scam: 'ఆగండి! ఇది మోసంలా ఉంది.',
      suspicious: 'జాగ్రత్త! ఈ మెసేజ్‌ను నమ్మకండి.',
      safe: 'ఇది సురక్షితంగా ఉంది.',
    },
    dont: ['ఏ లింక్‌నూ నొక్కకండి', 'OTP లేదా UPI PIN ఎవరికీ చెప్పకండి', 'డబ్బు పంపకండి'],
    safeTip: 'ఇంకా అనుమానం ఉంటే, ఆ వ్యక్తికి మీకు తెలిసిన పాత నంబర్‌కు కాల్ చేయండి.',
    why: 'ఎందుకు',
  },
  mr: {
    verdict: {
      scam: 'थांबा! ही फसवणूक वाटते.',
      suspicious: 'सावधान! या मेसेजवर विश्वास ठेवू नका.',
      safe: 'हे सुरक्षित वाटते.',
    },
    dont: ['कोणत्याही लिंकवर टॅप करू नका', 'OTP किंवा UPI PIN कोणालाही सांगू नका', 'पैसे पाठवू नका'],
    safeTip: 'तरीही शंका असल्यास, त्या व्यक्तीला त्यांच्या जुन्या नंबरवर फोन करा.',
    why: 'का',
  },
};

const BY_NAME: Record<string, UiLang> = { English: 'en', Hindi: 'hi', Bengali: 'bn', Tamil: 'ta', Telugu: 'te', Marathi: 'mr' };

/** The fixed-words language for a settings language name; anything else uses English. */
export function uiLang(language: string): UiLang {
  return BY_NAME[language] ?? 'en';
}

/** What Read aloud says: the verdict, why, and the do-nots, in the interface language. */
export function spokenVerdict(verdict: Verdict, summary: string, reasons: string[], lang: UiLang): string {
  const c = VERDICT_WORDS[lang];
  const parts = [c.verdict[verdict]];
  if (summary) parts.push(summary);
  if (reasons.length) parts.push(`${c.why}: ${reasons.join(', ')}.`);
  parts.push(verdict === 'safe' ? c.safeTip : `${c.dont.join('. ')}.`);
  return parts.join(' ');
}

/** Keeps digits and a leading + so the number works in a tel: link. */
export function cleanPhone(raw: string): string {
  const t = raw.trim();
  const digits = t.replace(/\D/g, '');
  if (digits.length < 6 || digits.length > 15) return '';
  return t.startsWith('+') ? `+${digits}` : digits;
}
