/**
 * The home screen: paste, screenshot or dictate a message, pick a privacy mode and check it.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { analyzeMessage, isPreviewEngine, transcribeAudio, ChaukasError, type ImageInput } from '../ai/engine';
import { computeSignals } from '../ai/signals';
import type { AnalysisRun, PrivacyMode } from '../ai/types';
import { engineSettings, languageCode, LANGUAGES, settingsStore } from '../data/settings';
import { saveRun } from '../data/history';
import { autoShareToCircles } from '../data/circle';
import { cameFromShare, deviceStore, navigate, rcStore, takeSharedImage, takeSharedText } from '../lib/appState';
import { fileToImageInput } from '../lib/image';
import { decodeQr, inspectQr, type QrFinding } from '../lib/qr';
import { SPEECH_LOCALES, startVoice, voiceErrorMessage, voiceSupported, voiceUnavailableReason, type VoiceSession, type VoiceState } from '../lib/voice';
import { SAMPLES } from '../lib/samples';
import { readTextOnDevice } from '../lib/ocr';
import { Icon, type IconName } from '../components/Icon';
import { Segmented, toast } from '../components/ui';
import { Result } from '../components/Result';
import { useT } from '../lib/i18n';
import { Welcome } from '../components/Welcome';

const MODE_COPY: Record<PrivacyMode, { icon: IconName; title: string; line: string }> = {
  private: { icon: 'lock', title: 'Private', line: 'Stays on this device, always. Works offline once the model is downloaded.' },
  smart: { icon: 'shieldCheck', title: 'Smart', line: 'On this device when possible. If not, personal details are removed before it goes to the cloud.' },
  cloud: { icon: 'cloud', title: 'Most accurate', line: 'Uses the larger cloud model first, with personal details removed. Falls back to on-device if offline.' },
};

/** The languages people here use most, one tap away. The rest are in Settings. */
const QUICK_LANGS = ['English', 'Hindi', 'Bengali', 'Tamil', 'Telugu', 'Marathi'];

const STAGES = ['Reading the message', 'Inspecting links and patterns', 'Weighing the evidence'];

export function CheckView() {
  const settings = settingsStore.use();
  const t = useT();
  const device = deviceStore.use();
  const rc = rcStore.use();
  const [text, setText] = useState(() => takeSharedText());
  const [image, setImage] = useState<ImageInput | null>(null);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState(0);
  const [error, setError] = useState<ChaukasError | null>(null);
  const [run, setRun] = useState<AnalysisRun | null>(null);
  const [dragging, setDragging] = useState(false);
  const [bannerHidden, setBannerHidden] = useState(false);
  const [qr, setQr] = useState<QrFinding[]>([]);
  const [voice, setVoice] = useState<VoiceState>('idle');
  const listening = voice !== 'idle';
  const [secs, setSecs] = useState(0);
  useEffect(() => {
    if (voice !== 'listening') return setSecs(0);
    const t = setInterval(() => setSecs((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [voice]);
  const dictation = useRef<VoiceSession | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);
  const composerRef = useRef<HTMLElement>(null);
  const fromShare = useRef(cameFromShare());
  // Someone who shared a message in wants the answer, not the tour.
  const [arrivedByShare] = useState(() => fromShare.current);
  const [shareReady, setShareReady] = useState(false);

  const live = useMemo(() => computeSignals(text), [text]);
  const riskSignals = live.signals.filter((s) => s.tone === 'risk');

  useEffect(() => {
    if (!busy) return;
    setStage(0);
    const t = setInterval(() => setStage((s) => Math.min(STAGES.length - 1, s + 1)), 700);
    return () => clearInterval(t);
  }, [busy]);

  // Auto-grow the textarea.
  useEffect(() => {
    const ta = taRef.current;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = `${Math.min(420, Math.max(150, ta.scrollHeight))}px`;
  }, [text]);

  // Private mode can't work without Gemini Nano (most phones and browsers). The first time
  // that happens, move to Smart, which still prefers the device and redacts before the cloud.
  const autoSwitched = useRef(false);
  useEffect(() => {
    if (autoSwitched.current || isPreviewEngine() || settings.mode !== 'private' || device.availability !== 'unavailable') return;
    autoSwitched.current = true;
    settingsStore.set({ mode: 'smart', privateFallbackNoted: true });
    toast("Switched to Smart: this browser doesn't have Gemini Nano, so Private mode can't run here. Smart removes personal details before using the cloud.", 'info');
    // Only when the device state first settles.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [device.availability]);

  const willRunLocally = device.availability === 'available' && settings.mode !== 'cloud';
  const engineName = isPreviewEngine() ? 'Preview checks' : willRunLocally ? 'Gemini Nano on this device' : settings.mode === 'private' ? 'Gemini Nano (not ready)' : 'Gemini in the cloud';

  /**
   * `once.cloud` sends this one check to the cloud after the person tapped a "Check with cloud"
   * button. `once.noImage` leaves the picture out: only the text and the QR code decoded on this
   * device go, with personal details removed.
   */
  const run_ = async (once?: { cloud: true; noImage?: boolean }) => {
    if (busy || (!text.trim() && !image)) return;
    dictation.current?.stop();
    setBusy(true);
    setError(null);
    setRun(null);
    try {
      const qrNote = qr.length ? `\n\n[QR code in the screenshot: ${qr.map((f) => `${f.raw} (${f.headline})`).join('; ')}]` : '';
      const base = engineSettings();
      const mode = once && settings.mode === 'private' ? 'smart' : settings.mode;
      let r;
      try {
        r = await analyzeMessage({
          text: `${text}${qrNote}`.trim(),
          image: once?.noImage ? null : image,
          mode,
          settings: once ? { ...base, allowCloudImages: true } : base,
        });
      } catch (e) {
        // A screenshot that would have to go to the cloud: read its words on this phone first,
        // and check only that text, with personal details removed. The picture is not sent.
        if (!(e instanceof ChaukasError && e.code === 'image-cloud' && image)) throw e;
        toast(t('Reading the words in your screenshot on this phone…'), 'info');
        const read = await readTextOnDevice(image.previewUrl);
        if (!read) throw e;
        const words = [text.trim(), read].filter(Boolean).join('\n');
        if (!text.trim()) setText(read);
        r = await analyzeMessage({ text: `${words}${qrNote}`.trim(), image: null, mode, settings: base });
        toast(t('Read on this phone. Only the words were checked, with personal details removed.'));
      }
      setRun(r);
      saveRun(r);
      autoShareToCircles(r).then((names) => names.length && toast(`Shared with ${names.join(', ')}`), () => undefined);
    } catch (e) {
      setError(e instanceof ChaukasError ? e : new ChaukasError(String(e)));
    } finally {
      setBusy(false);
    }
  };

  const addImage = async (file: File | Blob | null | undefined) => {
    if (!file || !file.type.startsWith('image/')) return;
    try {
      setImage(await fileToImageInput(file));
      // QR codes are decoded on-device with the browser's BarcodeDetector.
      const codes = await decodeQr(file);
      setQr(codes.map(inspectQr));
      if (codes.length) toast('Found a QR code in the screenshot', 'info');
    } catch {
      toast('That image could not be read', 'x');
    }
  };

  // Shared from another app: load a shared screenshot, then start the check by itself.
  useEffect(() => {
    if (!fromShare.current) return;
    void takeSharedImage()
      .then((blob) => (blob ? addImage(blob) : undefined))
      .finally(() => setShareReady(true));
  }, []);

  useEffect(() => {
    if (!shareReady || !fromShare.current) return;
    fromShare.current = false;
    if (text.trim() || image) void run_();
    // Fires once, after the shared content is in place.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shareReady]);

  const pasteFromClipboard = async () => {
    try {
      if (navigator.clipboard.read) {
        const items = await navigator.clipboard.read();
        for (const it of items) {
          const type = it.types.find((t) => t.startsWith('image/'));
          if (type) return addImage(await it.getType(type));
        }
      }
      const t = await navigator.clipboard.readText();
      if (t) setText(t);
      else toast('Your clipboard is empty', 'info');
    } catch {
      taRef.current?.focus();
      toast('Press Ctrl+V (or ⌘V) to paste', 'info');
    }
  };

  const toggleVoice = async () => {
    if (voice === 'writing') return;
    if (dictation.current) {
      dictation.current.stop();
      return;
    }
    if (!voiceSupported(!isPreviewEngine())) {
      toast(voiceUnavailableReason(), 'info');
      return;
    }
    const before = text.trim();
    setVoice('listening');
    dictation.current = await startVoice({
      lang: SPEECH_LOCALES[languageCode(settings.language)] ?? 'en-IN',
      onText: (t) => setText(before ? `${before}\n${t}` : t),
      onState: setVoice,
      onEnd: (err) => {
        dictation.current = null;
        const msg = voiceErrorMessage(err);
        if (msg) toast(msg, err === 'no-speech' ? 'info' : 'x');
      },
      transcribe: isPreviewEngine() ? null : (audio) => transcribeAudio(audio, settings.language),
    });
  };

  useEffect(() => () => dictation.current?.cancel(), []);

  const removeImage = () => {
    setImage(null);
    setQr([]);
  };

  const reset = () => {
    setText('');
    setImage(null);
    setQr([]);
    setRun(null);
    setError(null);
    taRef.current?.focus();
  };

  return (
    <div className="page check-page">
      {!arrivedByShare && <Welcome onTry={() => setText(SAMPLES[0].text)} />}
      <header className="hero">
        <span className="eyebrow hero-eyebrow">
          <span className="dot safe pulse" /> {engineName}
        </span>
        <h1 className="display hero-title">
          {t('Is this message a scam?')}
        </h1>
        <p className="hero-sub">{t('Speak it, paste it or add a screenshot. You get a clear answer in seconds, the reasons, and exactly what to do next.')}</p>
        <div className="lang-row" role="radiogroup" aria-label="Answer language">
          <span className="xs subtle">{t('Language')}</span>
          {QUICK_LANGS.map((name) => {
            const l = LANGUAGES.find((x) => x.name === name)!;
            const on = settings.language === name;
            return (
              <button key={name} role="radio" aria-checked={on} lang={l.code} className={`chip lang-chip${on ? ' on' : ''}`} onClick={() => settingsStore.set({ language: name })}>
                {l.native}
              </button>
            );
          })}
          {!QUICK_LANGS.includes(settings.language) && <span className="chip lang-chip on">{LANGUAGES.find((x) => x.name === settings.language)?.native ?? settings.language}</span>}
          <a className="xs lang-more" href="#/settings" onClick={(e) => { e.preventDefault(); navigate('settings'); }}>{t('More')}</a>
        </div>
        <GoingAround onPick={(t) => { setText(t); composerRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }} />
      </header>

      {rc.trendBanner && !bannerHidden && (
        <div className="trend">
          <span className="trend-tag"><Icon name="flame" /> {t('This week')}</span>
          <span className="grow">{t(rc.trendBanner)}</span>
          {rc.trendBannerLink && (
            <a href={rc.trendBannerLink} target="_blank" rel="noreferrer" className="trend-link">{t('Learn more')}</a>
          )}
          <button className="btn icon sm ghost" aria-label="Hide" onClick={() => setBannerHidden(true)}>
            <Icon name="x" />
          </button>
        </div>
      )}

      <section
        ref={composerRef}
        className={`card composer ${dragging ? 'dragging' : ''} ${busy ? 'is-busy' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          addImage(e.dataTransfer.files[0]);
        }}
      >
        <div className="composer-body">
          <label htmlFor="msg" className="sr-only">Message to check</label>
          <textarea
            id="msg"
            ref={taRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onPaste={(e) => {
              const f = Array.from(e.clipboardData.files).find((x) => x.type.startsWith('image/'));
              if (f) {
                e.preventDefault();
                addImage(f);
              }
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) run_();
            }}
            placeholder={t('Paste a text, email, WhatsApp or DM here…')}
            spellCheck={false}
          />
          {image && (
            <div className="thumb-wrap">
              <img src={image.previewUrl} alt="Attached screenshot" />
              <button className="thumb-x" onClick={removeImage} aria-label="Remove screenshot">
                <Icon name="x" />
              </button>
            </div>
          )}
          {qr.map((f) => <QrCard key={f.raw} finding={f} />)}
          {listening && (
            <div className="listening" aria-live="polite">
              <span className="rec-dot" aria-hidden="true" />
              {voice === 'writing'
                ? t('Writing down what you said…')
                : <>{t('Listening')} · 0:{String(secs).padStart(2, '0')}. {t('Speak now. It stops by itself when you pause.')}</>}
            </div>
          )}
          {!text && !image && !listening && (
            <div className="examples">
              <span className="xs subtle">{t('Or try one:')}</span>
              <button
                className="chip sample s-scam"
                onClick={async () => {
                  try {
                    const blob = await (await fetch(`${import.meta.env.BASE_URL}samples/upi-qr-scam.png`)).blob();
                    await addImage(new File([blob], 'upi-qr-scam.png', { type: 'image/png' }));
                    setText('Congratulations! Your Flipkart cashback refund of Rs 4,999 is approved. Scan this QR to receive the money in your account now.');
                  } catch {
                    toast('Could not load the example', 'x');
                  }
                }}
              >
                <Icon name="qr" style={{ width: 14, height: 14 }} /> {t('QR "refund" (India)')}
              </button>
              {SAMPLES.map((s) => (
                <button key={s.label} className={`chip sample s-${s.tone}`} onClick={() => setText(s.text)}>
                  <span className={`dot ${s.tone}`} /> {t(s.label)}
                </button>
              ))}
            </div>
          )}
          {riskSignals.length > 0 && !run && (
            <div className="live-signals" aria-live="polite">
              <span className="xs subtle"><Icon name="bolt" style={{ width: 12, height: 12, verticalAlign: -1 }} /> {t('Spotted instantly:')}</span>
              {riskSignals.map((s) => (
                <span key={s.id} className="live-chip" title={s.detail}>{s.label}</span>
              ))}
            </div>
          )}
        </div>

        <div className="composer-bar">
          <div className="row">
            <button
              className={`btn tool speak sm${voice === 'listening' ? ' rec' : ''}`}
              onClick={() => void toggleVoice()}
              aria-pressed={listening}
              disabled={voice === 'writing'}
              title="Say the message out loud, or what a caller told you"
            >
              <Icon name={voice === 'listening' ? 'x' : 'mic'} /> {t(voice === 'writing' ? 'Writing…' : listening ? 'Stop' : 'Speak')}
            </button>
            <button className="btn tool paste sm" onClick={pasteFromClipboard}>
              <Icon name="clipboard" /> {t('Paste')}
            </button>
            <button className="btn tool shot sm" onClick={() => fileRef.current?.click()}>
              <Icon name="image" /> {t('Screenshot')}
            </button>
            <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(e) => addImage(e.target.files?.[0])} />
            {(text || image) && (
              <button className="btn ghost sm" onClick={reset}>
                {t('Clear')}
              </button>
            )}
          </div>
          <span className="xs subtle hide-sm">{text.length > 0 && `${text.length} characters`}</span>
        </div>
      </section>

      <section className="mode-row">
        <div className="mode-left">
          <Segmented<PrivacyMode>
            label="Privacy mode"
            value={settings.mode}
            onChange={(mode) => settingsStore.set({ mode })}
            options={(['private', 'smart', 'cloud'] as PrivacyMode[]).map((m) => ({ value: m, label: t(MODE_COPY[m].title), icon: MODE_COPY[m].icon }))}
          />
          <p className="mode-line">{t(MODE_COPY[settings.mode].line)}</p>
        </div>
        <button className="btn primary lg check-btn" onClick={() => void run_()} disabled={busy || (!text.trim() && !image)}>
          {busy ? (
            <>
              <span className="spinner" /> {t('Checking…')}
            </>
          ) : (
            <>
              <Icon name="shieldCheck" /> {t('Check it')}
              <kbd className="hide-sm">Ctrl ↵</kbd>
            </>
          )}
        </button>
      </section>

      {settings.mode === 'private' && device.availability !== 'available' && device.availability !== 'checking' && !isPreviewEngine() && (
        <div className="callout warn">
          <Icon name="info" />
          <span>
            {device.availability === 'unavailable'
              ? <>Private mode needs Gemini Nano, which this browser doesn't have (most phones don't). Use <b>{t('Smart')}</b>: it removes personal details before anything goes to the cloud.</>
              : <>Private mode needs the on-device model. <a href="#/engine" onClick={(e) => { e.preventDefault(); navigate('engine'); }}>Download it in Settings → AI engine</a> or use Smart.</>}{' '}
            <button className="btn soft sm" onClick={() => settingsStore.set({ mode: 'smart' })}>Use Smart</button>
          </span>
        </div>
      )}

      {busy && (
        <section className="card card-pad thinking">
          <div className="thinking-orb" aria-hidden="true"><i /><i /><i /></div>
          <ol className="stages">
            {STAGES.map((s, i) => (
              <li key={s} className={i < stage ? 'done' : i === stage ? 'now' : ''}>
                <span className="tick"><Icon name="check" /></span>
                {t(s)}
              </li>
            ))}
          </ol>
        </section>
      )}

      {error && (
        <div className="callout err">
          <Icon name="alert" />
          <div>
            <b>{error.message}</b>
            {error.hint && <div style={{ marginTop: 2 }}>{error.hint}</div>}
            {error.code === 'private-unavailable' && !image && (
              <button className="btn primary sm" style={{ marginTop: 10 }} onClick={() => void run_({ cloud: true })}>
                <Icon name="cloud" /> {t('Check with cloud')}
              </button>
            )}
            {(error.code === 'image-cloud' || (error.code === 'private-unavailable' && image)) && (
              <div className="row wrap" style={{ gap: 8, marginTop: 10 }}>
                {(text.trim() || qr.length > 0) && (
                  <button className="btn primary sm" onClick={() => void run_({ cloud: true, noImage: true })}>
                    <Icon name="shield" /> {t('Check the text only')}
                  </button>
                )}
                <button className="btn ghost sm" onClick={() => void run_({ cloud: true })}>
                  <Icon name="cloud" /> {t('Send the picture anyway')}
                </button>
                <div className="xs" style={{ flexBasis: '100%' }}>
                  {text.trim() || qr.length > 0
                    ? 'Check the text only sends your typed text and any QR code read on this phone, with personal details removed. The picture is not sent.'
                    : 'Tip: paste or type the words from the screenshot to check them with personal details removed.'}{' '}
                  The picture itself cannot be cleaned, so send it only if it shows nothing private.
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {run && <Result run={run} onChange={setRun} />}

      {!run && !busy && <HowItWorks />}
    </div>
  );
}

function QrCard({ finding }: { finding: QrFinding }) {
  const danger = finding.kind === 'upi';
  return (
    <div className={`qr-card${danger ? ' danger' : ''}`}>
      <span className="qr-ic"><Icon name="qr" /></span>
      <div className="grow">
        <span className="eyebrow">{danger ? 'UPI payment QR' : 'QR code'} · decoded on this device</span>
        <p className="qr-headline">{finding.headline}</p>
        <dl className="qr-details">
          {finding.details.map((d) => (
            <div key={d.label}>
              <dt>{d.label}</dt>
              <dd className="mono">{d.value}</dd>
            </div>
          ))}
        </dl>
        {finding.warnings.length > 0 && (
          <ul className="qr-warn">
            {finding.warnings.map((w) => (
              <li key={w}><Icon name="alert" /> {w}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function HowItWorks() {
  const t = useT();
  const items: { icon: IconName; tint: string; title: string; body: string }[] = [
    { icon: 'laptop', tint: 'mint', title: 'Checked on your device', body: 'Gemini Nano runs inside Chrome, so your messages stay with you, even offline.' },
    { icon: 'eye', tint: 'lilac', title: 'Shows its working', body: 'Every warning points at the exact words that gave the scam away.' },
    { icon: 'users', tint: 'peach', title: 'Looks after family', body: 'Share a warning with your circle in one tap. Personal details are removed.' },
  ];
  return (
    <section className="how">
      {items.map((it, i) => (
        <div key={it.title} className={`how-item t-${it.tint}`} style={{ animationDelay: `${i * 80}ms` }}>
          <span className="how-ic"><Icon name={it.icon} /></span>
          <h3>{t(it.title)}</h3>
          <p>{t(it.body)}</p>
        </div>
      ))}
    </section>
  );
}

/** Desktop side panel: real scams doing the rounds, one tap to see how Chaukas reads them. */
function GoingAround({ onPick }: { onPick: (text: string) => void }) {
  const t = useT();
  const picks = [
    { label: 'Electricity bill (India)', title: 'Power cut tonight', note: 'Fake bill SMS with a personal number' },
    { label: 'KYC update (India)', title: 'KYC expired', note: 'Bank "account blocked" text with a link' },
    { label: 'UPI refund (India)', title: 'Refund on UPI', note: 'Asks for your UPI PIN to "receive" money' },
  ];
  return (
    <aside className="going-around" aria-label="Scams going around">
      <b className="ga-title">{t('Going around right now')}</b>
      <ul>
        {picks.map((p) => {
          const sample = SAMPLES.find((x) => x.label === p.label);
          return (
            <li key={p.label}>
              <button onClick={() => sample && onPick(sample.text)}>
                <span className="ga-dot" aria-hidden="true" />
                <span className="grow">
                  <b>{t(p.title)}</b>
                  <span>{t(p.note)}</span>
                </span>
                <span className="ga-try">{t('Try it')}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}
