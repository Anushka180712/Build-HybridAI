/**
 * Voice input. Someone can say a message out loud ("they told me my SIM will be blocked…")
 * and have it checked like any other text.
 *
 * Two engines:
 * - The browser's Web Speech API writes your words live as you speak, in the chosen language, on
 *   Chrome (desktop and Android), Edge and Safari (Mac and iPhone).
 * - A short recording, written down by Gemini in the cloud, when live recognition is missing or
 *   not allowed (Brave, Firefox, some in-app browsers). On desktop it is also kept as a backup.
 */
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort?(): void;
}

type Ctor = new () => Recognition;

function ctor(): Ctor | null {
  const g = globalThis as unknown as { SpeechRecognition?: Ctor; webkitSpeechRecognition?: Ctor };
  return g.SpeechRecognition ?? g.webkitSpeechRecognition ?? null;
}

/** Brave ships the API but blocks the speech service behind it, so treat it as unsupported. */
function isBrave(): boolean {
  if (typeof navigator === 'undefined') return false;
  return !!(navigator as Navigator & { brave?: unknown }).brave;
}

function isMobile(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  return /Android|iPhone|iPad|iPod|Mobile/i.test(ua) || (ua.includes('Macintosh') && (navigator.maxTouchPoints ?? 0) > 1);
}

/** Desktop browsers with live recognition, where a backup recording can run alongside it. */
const liveSpeechUsable = () => ctor() !== null && !isBrave() && !isMobile();

const recordingUsable = () => typeof MediaRecorder !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;

/** Voice input can work here at all, given whether cloud transcription is available. */
export const voiceSupported = (cloud: boolean) => liveSpeechUsable() || (cloud && recordingUsable()) || (ctor() !== null && !isBrave());

/** Why voice input is not available here, in plain words. */
export function voiceUnavailableReason(): string {
  if (isBrave()) return 'Brave blocks speech-to-text. Open Chaukas in Google Chrome to use your voice.';
  return 'This browser cannot use the microphone. Try Google Chrome, or paste or type the message.';
}

/** A plain-language message for a voice input error, or '' when there is nothing to say. */
export function voiceErrorMessage(error?: string): string {
  switch (error) {
    case undefined:
    case 'aborted':
      return '';
    case 'no-speech':
      return "Didn't catch any words. Tap Speak, hold the phone close and speak clearly.";
    case 'not-allowed':
    case 'service-not-allowed':
    case 'NotAllowedError':
    case 'SecurityError':
      return isMobile()
        ? 'The microphone is blocked. Allow Microphone for this site in your browser settings, then try again.'
        : 'The microphone is blocked. Click the icon at the left of the address bar, allow Microphone, and try again.';
    case 'audio-capture':
    case 'NotFoundError':
    case 'NotReadableError':
      return 'No microphone was found, or another app is using it.';
    case 'network':
      return isBrave() ? voiceUnavailableReason() : 'Voice input needs an internet connection.';
    case 'language-not-supported':
      return 'This browser cannot listen in that language yet. Try English or Hindi.';
    case 'transcribe-failed':
      return 'Could not write down what you said. Check your connection and try again.';
    default:
      return 'Voice input stopped. Please try again.';
  }
}

export type VoiceState = 'listening' | 'writing' | 'idle';

export interface VoiceSession {
  /** Stops listening. If live recognition heard nothing, the recording is written down by the cloud. */
  stop(): void;
  /** Stops without writing anything down. */
  cancel(): void;
}

export interface VoiceOptions {
  /** BCP 47 tag for live recognition, e.g. hi-IN. */
  lang: string;
  /** Receives the full transcript so far every time it changes. */
  onText: (t: string) => void;
  onState: (s: VoiceState) => void;
  /** Called once at the end with an error code for voiceErrorMessage, or nothing. */
  onEnd: (error?: string) => void;
  /** Microphone loudness from 0 to 1, about 30 times a second, for a level meter. */
  onLevel?: (level: number) => void;
  /** Cloud transcription of a WAV recording, or null when it is not available. */
  transcribe: ((audio: { base64: string; mimeType: string }) => Promise<string>) | null;
}

const MAX_SECONDS = 90;
/** Pause after speech that ends a recording by itself. Short, so the answer comes sooner. */
const SILENCE_MS = 1400;

/** Errors after which live recognition will not work here, so recording takes over. */
const LIVE_UNAVAILABLE = new Set(['service-not-allowed', 'language-not-supported', 'network', 'audio-capture', 'bad-grammar']);

/**
 * Joins the recogniser's results into one transcript. Android Chrome repeats the whole sentence
 * in every new result, so a result that already contains the text so far replaces it.
 */
export function joinResults(parts: string[]): string {
  let acc = '';
  for (const raw of parts) {
    const t = raw.trim();
    if (!t) continue;
    if (!acc || t.toLowerCase().startsWith(acc.toLowerCase())) acc = t;
    else if (!acc.toLowerCase().endsWith(t.toLowerCase())) acc = `${acc} ${t}`;
  }
  return acc;
}

/** Starts voice input. Resolves once the microphone is open (or failed, via onEnd). */
export async function startVoice(opts: VoiceOptions): Promise<VoiceSession> {
  let ended = false;
  let stopping = false;
  let heard = '';
  let liveError: string | undefined;
  let lastWordsAt = 0;
  let rec: Recognition | null = null;
  let media: MediaRecorder | null = null;
  let stream: MediaStream | null = null;
  let hadLive = false;
  const chunks: Blob[] = [];
  const timer: { id?: ReturnType<typeof setTimeout>; quiet?: ReturnType<typeof setInterval> } = {};
  const meter: { ctx?: AudioContext; raf?: number } = {};

  const release = () => {
    clearTimeout(timer.id);
    clearInterval(timer.quiet);
    if (meter.raf) cancelAnimationFrame(meter.raf);
    void meter.ctx?.close().catch(() => undefined);
    meter.ctx = undefined;
    opts.onLevel?.(0);
    stream?.getTracks().forEach((t) => t.stop());
  };
  const finish = (error?: string) => {
    if (ended) return;
    ended = true;
    release();
    opts.onState('idle');
    opts.onEnd(error);
  };

  const canRecord = !!opts.transcribe && recordingUsable();
  const liveHere = ctor() !== null && !isBrave();
  // Desktop: live words plus a recording kept in case live recognition hears nothing.
  // Phones: live words alone (a phone can't give the microphone to both at once), and the
  // recording takes over only if live recognition is not available in this browser.
  const desktop = liveSpeechUsable();

  /** Opens the microphone for a recording that Gemini writes down. */
  const startRecording = async (): Promise<string | undefined> => {
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } });
      media = new MediaRecorder(stream);
      media.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      media.start();
      watchLevel();
      return undefined;
    } catch (err) {
      media = null;
      return err instanceof DOMException ? err.name : 'audio-capture';
    }
  };

  /** Starts live recognition. Returns false if it could not start. */
  const startLive = (): boolean => {
    const C = ctor()!;
    const r = new C();
    rec = r;
    hadLive = true;
    r.lang = opts.lang;
    r.continuous = true;
    r.interimResults = true;
    r.onresult = (e) => {
      const parts: string[] = [];
      for (let i = 0; i < e.results.length; i++) parts.push(e.results[i][0].transcript);
      const next = joinResults(parts);
      if (next && next !== heard) {
        heard = next;
        lastWordsAt = performance.now();
        opts.onText(heard);
      }
    };
    r.onerror = (e) => {
      if (e.error !== 'aborted' && e.error !== 'no-speech') liveError = e.error;
    };
    r.onend = () => {
      if (rec === r) rec = null;
      if (ended || stopping || media) return;
      // Live recognition is not available in this browser: switch to a recording, once.
      if (!heard && canRecord && liveError && LIVE_UNAVAILABLE.has(liveError)) {
        liveError = undefined;
        void startRecording().then((err) => err && finish(err));
        return;
      }
      finish(heard ? undefined : liveError ?? 'no-speech');
    };
    try {
      r.start();
      return true;
    } catch {
      rec = null;
      return false;
    }
  };

  if (desktop) {
    if (canRecord) {
      const err = await startRecording();
      if (err === 'NotAllowedError' || err === 'SecurityError') {
        finish(err);
        return { stop() {}, cancel() {} };
      }
    }
    startLive();
  } else if (liveHere) {
    if (!startLive() && canRecord) {
      const err = await startRecording();
      if (err) {
        finish(err);
        return { stop() {}, cancel() {} };
      }
    }
  } else if (canRecord) {
    const err = await startRecording();
    if (err) {
      finish(err);
      return { stop() {}, cancel() {} };
    }
  }

  if (!media && !rec) {
    finish(ended ? undefined : 'audio-capture');
    return { stop() {}, cancel() {} };
  }

  opts.onState('listening');

  function stop() {
    if (ended || stopping) return;
    stopping = true;
    rec?.stop();
    if (!media) {
      // Give live recognition a moment to deliver its last words.
      setTimeout(() => finish(heard ? undefined : liveError ?? 'no-speech'), 400);
      return;
    }
    const m = media;
    m.onstop = async () => {
      release();
      // Live words arrived: use them, nothing goes to the cloud. Only wait for them when live
      // recognition was running; otherwise go straight to writing the recording down.
      if (hadLive && desktop) await new Promise((r) => setTimeout(r, 300));
      if (heard) return finish();
      if (!chunks.length) return finish('no-speech');
      opts.onState('writing');
      try {
        const wav = await toWav(new Blob(chunks, { type: m.mimeType || chunks[0].type }));
        const text = await opts.transcribe!(wav);
        if (ended) return;
        if (!text) return finish('no-speech');
        opts.onText(text);
        finish();
      } catch {
        finish('transcribe-failed');
      }
    };
    if (m.state !== 'inactive') m.stop();
    else void m.onstop?.(new Event('stop'));
  }

  timer.id = setTimeout(stop, MAX_SECONDS * 1000);

  // Live words with no recording: a pause of about two seconds after the last new word ends it.
  timer.quiet = setInterval(() => {
    if (!media && heard && performance.now() - lastWordsAt > SILENCE_MS + 600) stop();
  }, 250);

  // Level meter and auto-stop for a recording: once someone has spoken, a short pause ends the
  // recording by itself, so nobody has to find the Stop button.
  function watchLevel() {
    if (!stream) return;
    try {
      const AC = (globalThis as unknown as { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }).AudioContext
        ?? (globalThis as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AC();
      meter.ctx = ctx;
      void ctx.resume();
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      ctx.createMediaStreamSource(stream).connect(analyser);
      const buf = new Float32Array(analyser.fftSize);
      let spoke = false;
      let quietSince = performance.now();
      const startedAt = quietSince;
      const tick = () => {
        if (ended || stopping) return;
        analyser.getFloatTimeDomainData(buf);
        let sum = 0;
        for (let i = 0; i < buf.length; i++) sum += buf[i] * buf[i];
        const rms = Math.sqrt(sum / buf.length);
        opts.onLevel?.(Math.min(1, rms * 9));
        const now = performance.now();
        if (rms > 0.025) {
          spoke = true;
          quietSince = now;
        } else if (spoke && now - quietSince > SILENCE_MS && now - startedAt > 1200) {
          stop();
          return;
        }
        meter.raf = requestAnimationFrame(tick);
      };
      meter.raf = requestAnimationFrame(tick);
    } catch {
      /* no meter: the Stop button still works */
    }
  }

  return {
    stop,
    cancel: () => {
      if (ended) return;
      stopping = true;
      rec?.abort?.();
      const m = media as MediaRecorder | null;
      if (m && m.state !== 'inactive') {
        m.onstop = null;
        m.stop();
      }
      finish('aborted');
    },
  };
}

/** Decodes any recording the browser made and re-encodes it as 16 kHz mono WAV, which Gemini always accepts. */
async function toWav(blob: Blob): Promise<{ base64: string; mimeType: string }> {
  const AC = (globalThis as unknown as { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }).AudioContext
    ?? (globalThis as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  const ctx = new AC();
  try {
    const decoded = await ctx.decodeAudioData(await blob.arrayBuffer());
    const rate = 16000;
    const offline = new OfflineAudioContext(1, Math.max(1, Math.ceil(decoded.duration * rate)), rate);
    const src = offline.createBufferSource();
    src.buffer = decoded;
    src.connect(offline.destination);
    src.start();
    const mono = await offline.startRendering();
    // Less audio to upload and listen to: cut the quiet before and after the words.
    return { base64: bytesToBase64(encodeWav(trimSilence(mono.getChannelData(0), rate), rate)), mimeType: 'audio/wav' };
  } finally {
    void ctx.close();
  }
}

/** Drops leading and trailing quiet, keeping a short margin so no word is clipped. */
export function trimSilence(samples: Float32Array, rate: number, threshold = 0.02): Float32Array {
  const win = Math.round(rate / 50); // 20 ms
  const loud = (i: number) => {
    let peak = 0;
    for (let j = i; j < Math.min(samples.length, i + win); j++) peak = Math.max(peak, Math.abs(samples[j]));
    return peak > threshold;
  };
  let start = 0;
  while (start < samples.length && !loud(start)) start += win;
  let end = samples.length;
  while (end > start && !loud(Math.max(0, end - win))) end -= win;
  if (start >= end) return samples;
  const pad = Math.round(rate * 0.25);
  return samples.subarray(Math.max(0, start - pad), Math.min(samples.length, end + pad));
}

/** 16-bit PCM WAV bytes for mono float samples in [-1, 1]. */
export function encodeWav(samples: Float32Array, sampleRate: number): Uint8Array {
  const buf = new ArrayBuffer(44 + samples.length * 2);
  const v = new DataView(buf);
  const str = (o: number, s: string) => [...s].forEach((ch, i) => v.setUint8(o + i, ch.charCodeAt(0)));
  str(0, 'RIFF');
  v.setUint32(4, 36 + samples.length * 2, true);
  str(8, 'WAVE');
  str(12, 'fmt ');
  v.setUint32(16, 16, true);
  v.setUint16(20, 1, true);
  v.setUint16(22, 1, true);
  v.setUint32(24, sampleRate, true);
  v.setUint32(28, sampleRate * 2, true);
  v.setUint16(32, 2, true);
  v.setUint16(34, 16, true);
  str(36, 'data');
  v.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    const x = Math.max(-1, Math.min(1, samples[i]));
    v.setInt16(44 + i * 2, x < 0 ? x * 0x8000 : x * 0x7fff, true);
  }
  return new Uint8Array(buf);
}

function bytesToBase64(bytes: Uint8Array): string {
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(bin);
}

/** BCP 47 tags with a region, which speech recognisers prefer. */
export const SPEECH_LOCALES: Record<string, string> = {
  en: 'en-IN', hi: 'hi-IN', bn: 'bn-IN', ta: 'ta-IN', te: 'te-IN', mr: 'mr-IN', kn: 'kn-IN', ml: 'ml-IN', gu: 'gu-IN', pa: 'pa-IN',
  es: 'es-ES', ja: 'ja-JP', fr: 'fr-FR', pt: 'pt-BR',
};
