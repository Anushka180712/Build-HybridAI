/**
 * The hybrid engine. Every model call goes through Firebase AI Logic with onDeviceParams and
 * inCloudParams sharing one JSON schema. It checks Gemini Nano availability before each
 * request, so personal details are redacted only when the prompt will really reach the cloud.
 */
import { getAI, getGenerativeModel, GoogleAIBackend, InferenceMode, type AI, type GenerativeModel, type HybridParams, type Part } from 'firebase/ai';
import { getFirebase } from '../firebase/app';
import { currentRemoteConfig } from '../firebase/remoteConfig';
import { ANALYSIS_SCHEMA, DOJO_SCHEMA } from './schema';
import { buildAnalysisPrompt, buildDojoPrompt, buildFollowUpPrompt, type DojoBrief } from './prompts';
import { extractJson, normalizeAnalysis } from './parse';
import { redact, restore } from './redact';
import { computeSignals } from './signals';
import { deviceAvailability, IMAGE_INPUTS, TEXT_INPUTS } from './device';
import { previewAnalysis, previewDojo } from './preview';
import { log } from '../lib/log';
import { languageCode, ON_DEVICE_LANGUAGES } from '../data/settings';
import type { Analysis, AnalysisRun, PrivacyMode, Source } from './types';

export interface EngineSettings {
  /** Redact personal details whenever a prompt is about to leave the device. */
  redactBeforeCloud: boolean;
  /** Allow screenshots (which cannot be redacted) to be sent to the cloud. */
  allowCloudImages: boolean;
  /** Extra words to always hide, such as the person's own name. */
  privateTerms: string[];
  /** Language to explain the verdict in (English name, e.g. "Hindi"). */
  language?: string;
  /** Casual, friendly wording (Hinglish for Hindi). */
  casual?: boolean;
}

export interface ImageInput {
  mimeType: 'image/png' | 'image/jpeg';
  base64: string;
  previewUrl: string;
}

type Kind = 'analysis' | 'dojo' | 'text';

const MODE_MAP: Record<PrivacyMode, InferenceMode> = {
  private: InferenceMode.ONLY_ON_DEVICE,
  smart: InferenceMode.PREFER_ON_DEVICE,
  cloud: InferenceMode.PREFER_IN_CLOUD,
};

export class ChaukasError extends Error {
  constructor(
    message: string,
    public hint?: string,
    /** 'image-cloud' or 'private-unavailable': the app offers a one-tap "Check with cloud". */
    public code?: string,
  ) {
    super(message);
  }
}

let ai: AI | null = null;
const models = new Map<string, GenerativeModel>();

function getAi(): AI | null {
  const fb = getFirebase();
  if (!fb) return null;
  ai ??= getAI(fb.app, { backend: new GoogleAIBackend() });
  return ai;
}

export const isPreviewEngine = () => getAi() === null;

/**
 * Builds (and caches) a Firebase AI Logic hybrid model. The same request is
 * served by Gemini Nano in Chrome or by Gemini in the cloud depending on the
 * InferenceMode, with an identical JSON schema on both sides.
 */
function model(kind: Kind, mode: InferenceMode, withImage = false, language = 'English'): GenerativeModel {
  const instance = getAi();
  if (!instance) throw new ChaukasError('Firebase is not configured.');
  const cloudModel = currentRemoteConfig().cloudModel;
  const code = languageCode(language);
  const outLang = ON_DEVICE_LANGUAGES.includes(code) ? code : 'en';
  const key = `${kind}|${mode}|${cloudModel}|${withImage}|${outLang}`;
  const cached = models.get(key);
  if (cached) return cached;

  const schema = kind === 'analysis' ? ANALYSIS_SCHEMA : kind === 'dojo' ? DOJO_SCHEMA : undefined;
  const params: HybridParams = {
    mode,
    onDeviceParams: {
      createOptions: {
        expectedInputs: [...(withImage ? IMAGE_INPUTS : TEXT_INPUTS)] as never,
        expectedOutputs: [{ type: 'text', languages: [outLang] }],
      },
      promptOptions: schema ? { responseConstraint: schema } : undefined,
    },
    inCloudParams: {
      model: cloudModel,
      generationConfig: schema
        ? { responseMimeType: 'application/json', responseSchema: schema as never, temperature: kind === 'dojo' ? 1 : 0.2 }
        : { temperature: 0.4, maxOutputTokens: 400 },
    },
  };
  const m = getGenerativeModel(instance, params);
  models.set(key, m);
  return m;
}

/** The HTTP status in a Firebase AI Logic error, or 0. */
function statusOf(err: unknown): number {
  const msg = err instanceof Error ? err.message : String(err);
  return Number(msg.match(/\[(\d{3})/)?.[1] ?? msg.match(/\b(429|5\d\d)\b/)?.[1] ?? 0);
}

/**
 * Gemini sometimes answers 500, 503 ("overloaded") or 429 for a few seconds. Those are worth
 * retrying quietly instead of showing an error: up to two more tries, about 1 s and 3 s apart.
 */
export async function withRetry<T>(call: () => Promise<T>, waits = [1000, 3000]): Promise<T> {
  for (let i = 0; ; i++) {
    try {
      return await call();
    } catch (err) {
      const status = statusOf(err);
      const retryable = status === 429 || status >= 500;
      if (!retryable || i >= waits.length) throw err;
      log.debug('retry', `cloud answered ${status}, trying again in ${waits[i]} ms`);
      await new Promise((r) => setTimeout(r, waits[i]));
    }
  }
}

function friendly(err: unknown): ChaukasError {
  if (err instanceof ChaukasError) return err;
  const msg = err instanceof Error ? err.message : String(err);
  const detail = msg.replace(/\s+/g, ' ').slice(0, 200);
  if (/ONLY_ON_DEVICE|on-device model is not available|LanguageModel API not available/i.test(msg)) {
    return new ChaukasError('Private mode needs Gemini Nano, which is not ready in this browser.', 'Most phones and browsers do not have it. Switch to Smart mode: it removes personal details before using the cloud.');
  }
  // Only call it "offline" when the device really is, or the request never left the browser.
  if ((typeof navigator !== 'undefined' && navigator.onLine === false) || /^Failed to fetch$|NetworkError|ERR_INTERNET_DISCONNECTED/i.test(msg)) {
    return new ChaukasError('You appear to be offline and the on-device model is not ready.', 'Private mode keeps working offline once Gemini Nano is downloaded.');
  }
  const status = Number(msg.match(/\[(\d{3})/)?.[1] ?? msg.match(/\b(4\d\d|5\d\d)\b/)?.[1] ?? 0);
  if (/app[ -]?check|attestation/i.test(msg) || status === 401) {
    return new ChaukasError('The cloud model refused the request (App Check).', 'Register this browser\'s App Check debug token (or your reCAPTCHA key) in the Firebase console, then reload.');
  }
  if (status === 403) return new ChaukasError('The cloud model refused the request.', `Check that Firebase AI Logic is enabled for this project. Details: ${detail}`);
  if (status === 404) return new ChaukasError('The cloud model was not found.', `Check the cloud_model value in Remote Config. Details: ${detail}`);
  if (status === 429) return new ChaukasError('Too many checks right now.', 'Wait a minute and try again.');
  if (status >= 500) return new ChaukasError('Gemini is busy right now.', `It did not answer after three tries (error ${status}). Wait a few seconds and tap Check again.`);
  if (status === 400) return new ChaukasError('The cloud model rejected the request.', detail);
  if (/JSON/i.test(msg)) return new ChaukasError('The model returned an unreadable answer.', 'Try again. Small on-device models occasionally slip.');
  return new ChaukasError('Something went wrong while checking the message.', detail);
}

const uid = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`);

/** Main entry point: check a message (text and/or screenshot). */
export async function analyzeMessage(args: { text: string; image?: ImageInput | null; mode: PrivacyMode; settings: EngineSettings }): Promise<AnalysisRun> {
  const { text, image, mode, settings } = args;
  const started = performance.now();
  const { signals, links } = computeSignals(text);

  if (isPreviewEngine()) {
    await new Promise((r) => setTimeout(r, 650));
    return {
      id: uid(),
      createdAt: Date.now(),
      input: text,
      hasImage: Boolean(image),
      analysis: previewAnalysis(text, Boolean(image)),
      source: 'preview',
      mode,
      latencyMs: Math.round(performance.now() - started),
      redactions: 0,
    };
  }

  // Decide up front whether this prompt can stay on the device.
  const deviceReady = (await deviceAvailability(Boolean(image))) === 'available';
  const mayLeaveDevice = mode === 'cloud' || (mode === 'smart' && !deviceReady);
  if (mode === 'private' && !deviceReady) {
    throw new ChaukasError('Private mode needs Gemini Nano, which is not ready in this browser.', 'Most phones and browsers do not have it. Check with the cloud instead: personal details in text are removed first.', 'private-unavailable');
  }
  if (image && mayLeaveDevice && !settings.allowCloudImages) {
    throw new ChaukasError(
      'This screenshot needs the cloud to be checked.',
      "Gemini Nano isn't ready here, and personal details can't be removed from a picture. Check the text only (cleaned first), or send the picture once.",
      'image-cloud',
    );
  }

  const red = mayLeaveDevice && settings.redactBeforeCloud ? redact(text, settings.privateTerms) : null;
  log.debug('route', `mode=${mode} deviceReady=${deviceReady} mayLeaveDevice=${mayLeaveDevice} redacted=${red?.count ?? 0}`);
  const promptText = buildAnalysisPrompt(red ? red.text : text, signals, links, Boolean(image), settings.language, settings.casual);
  const parts: Part[] = [{ type: 'text', text: promptText }];
  if (image) parts.push({ type: 'inlineData', inlineData: { mimeType: image.mimeType, data: image.base64 } });

  try {
    const result = await withRetry(() => model('analysis', MODE_MAP[mode], Boolean(image), settings.language).generateContent(parts));
    const source = (result.response.inferenceSource ?? 'in_cloud') as Source;
    log.debug('analysis', `served ${source} in ${Math.round(performance.now() - started)} ms`);
    let analysis = normalizeAnalysis(extractJson(result.response.text()));
    if (red) analysis = restoreAnalysis(analysis, red.map);
    return {
      id: uid(),
      createdAt: Date.now(),
      input: text,
      hasImage: Boolean(image),
      analysis,
      source,
      mode,
      latencyMs: Math.round(performance.now() - started),
      redactions: source === 'in_cloud' && red ? red.count : 0,
      sentToCloud: source === 'in_cloud' ? (red ? red.text : text) : undefined,
    };
  } catch (err) {
    throw friendly(err);
  }
}

function restoreAnalysis(a: Analysis, map: Record<string, string>): Analysis {
  const r = (s: string) => restore(s, map);
  return {
    ...a,
    summary: r(a.summary),
    safeReply: r(a.safeReply),
    redFlags: a.redFlags.map((f) => ({ ...f, evidence: r(f.evidence), why: r(f.why) })),
    nextSteps: a.nextSteps.map(r),
    reassurances: a.reassurances.map(r),
  };
}

/**
 * Confidence-based escalation: when the on-device verdict is borderline, ask
 * the larger cloud model for an independent read of the redacted message.
 */
export async function secondOpinion(run: AnalysisRun, settings: EngineSettings): Promise<AnalysisRun['secondOpinion']> {
  const started = performance.now();
  if (isPreviewEngine()) {
    await new Promise((r) => setTimeout(r, 900));
    const a = previewAnalysis(run.input, run.hasImage, 1);
    return { analysis: a, latencyMs: Math.round(performance.now() - started), source: 'preview' };
  }
  const red = redact(run.input, settings.privateTerms);
  const { signals, links } = computeSignals(run.input);
  try {
    const result = await withRetry(() => model('analysis', InferenceMode.ONLY_IN_CLOUD, false, settings.language).generateContent(buildAnalysisPrompt(red.text, signals, links, false, settings.language, settings.casual)));
    const analysis = restoreAnalysis(normalizeAnalysis(extractJson(result.response.text())), red.map);
    return { analysis, latencyMs: Math.round(performance.now() - started), source: (result.response.inferenceSource ?? 'in_cloud') as Source };
  } catch (err) {
    throw friendly(err);
  }
}

/** Streams a plain-language answer to a follow-up question about a check. */
export async function askFollowUp(run: AnalysisRun, question: string, mode: PrivacyMode, settings: EngineSettings, onText: (t: string) => void): Promise<Source> {
  if (isPreviewEngine()) {
    const canned =
      'In preview mode answers come from built-in guidance only. In general: do not tap links or call numbers from the message. Contact the organisation using a number or app you already trust, and never share one-time codes.';
    let acc = '';
    for (const w of canned.split(' ')) {
      acc += (acc ? ' ' : '') + w;
      onText(acc);
      await new Promise((r) => setTimeout(r, 28));
    }
    return 'preview';
  }
  const deviceReady = (await deviceAvailability()) === 'available';
  const leaves = mode === 'cloud' || (mode === 'smart' && !deviceReady);
  if (mode === 'private' && !deviceReady) throw friendly(new Error('ONLY_ON_DEVICE'));
  const red = leaves && settings.redactBeforeCloud ? redact(`${run.input}\n@@\n${question}`, settings.privateTerms) : null;
  const [msg, q] = red ? red.text.split('\n@@\n') : [run.input, question];
  try {
    const stream = await withRetry(() => model('text', MODE_MAP[mode], false, settings.language).generateContentStream(buildFollowUpPrompt(msg, run.analysis, q, settings.language, settings.casual)));
    let acc = '';
    for await (const chunk of stream.stream) {
      acc += chunk.text();
      onText(red ? restore(acc, red.map) : acc);
    }
    const final = await stream.response;
    return (final.inferenceSource ?? 'in_cloud') as Source;
  } catch (err) {
    throw friendly(err);
  }
}

export interface DojoRound {
  brief: DojoBrief;
  sender: string;
  message: string;
  tells: string[];
  explanation: string;
  source: Source;
}

export async function generateDojoRound(brief: DojoBrief, mode: PrivacyMode): Promise<DojoRound> {
  if (isPreviewEngine()) {
    await new Promise((r) => setTimeout(r, 500));
    return { ...previewDojo(brief), brief, source: 'preview' };
  }
  try {
    // Practice rounds contain no personal data, so they can always fall back.
    const m = model('dojo', mode === 'private' ? InferenceMode.ONLY_ON_DEVICE : MODE_MAP[mode]);
    const result = await withRetry(() => m.generateContent(buildDojoPrompt(brief)));
    const o = extractJson(result.response.text()) as Record<string, unknown>;
    return {
      brief,
      sender: String(o.sender ?? 'Unknown').slice(0, 60),
      message: String(o.message ?? '').slice(0, 600),
      tells: Array.isArray(o.tells) ? o.tells.map(String).slice(0, 3) : [],
      explanation: String(o.explanation ?? '').slice(0, 400),
      source: (result.response.inferenceSource ?? 'in_cloud') as Source,
    };
  } catch (err) {
    throw friendly(err);
  }
}

/** Starts (or resumes) the Gemini Nano download. Must follow a user gesture. */
export async function prepareOnDeviceModel(onProgress: (fraction: number) => void): Promise<void> {
  if (isPreviewEngine()) throw new ChaukasError('Connect Firebase first.', 'Add your Firebase web config to .env.local.');
  try {
    await model('analysis', InferenceMode.PREFER_ON_DEVICE).initializeDeviceModel(onProgress);
  } catch (err) {
    throw friendly(err);
  }
}

/**
 * Turns a short voice recording into text with Gemini in the cloud. Used where the browser has
 * no working speech recognition (most phones, Brave, Firefox) or when it heard nothing. Only the
 * audio goes up; the check itself still runs in the privacy mode the person picked.
 */
export async function transcribeAudio(audio: { base64: string; mimeType: string }, language: string): Promise<string> {
  const instance = getAi();
  if (!instance) throw new ChaukasError('Voice input needs Firebase to be connected.');
  // Writing words down needs no reasoning: skip "thinking" so the text comes back sooner. A model
  // that refuses that setting gets the plain request instead.
  const fast = getGenerativeModel(instance, { model: currentRemoteConfig().cloudModel, generationConfig: { temperature: 0, maxOutputTokens: 800, thinkingConfig: { thinkingBudget: 0 } } });
  const plain = getGenerativeModel(instance, { model: currentRemoteConfig().cloudModel, generationConfig: { temperature: 0, maxOutputTokens: 800 } });
  let m = fast;
  const send = (parts: Part[]) => m.generateContent(parts).catch((err) => {
    if (m === fast && /\b400\b|thinking/i.test(String(err))) {
      m = plain;
      return m.generateContent(parts);
    }
    throw err;
  });
  try {
    const result = await withRetry(() => send([
      { type: 'inlineData', inlineData: { data: audio.base64, mimeType: audio.mimeType } },
      { type: 'text',
        text:
          `Write down exactly what is said in this recording. The speaker probably uses ${language}, but may mix in English or another Indian language. ` +
          'Keep each word in the language it was spoken, written in its usual script. Keep numbers, links and amounts as spoken. ' +
          'Reply with the words only, no notes or quotes. If there is no speech, reply with nothing.',
      },
    ]));
    return result.response.text().trim();
  } catch (err) {
    throw friendly(err);
  }
}
