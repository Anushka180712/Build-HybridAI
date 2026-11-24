/**
 * Engine dashboard: Gemini Nano status and download, live routing diagram, measured latency
 * and Firebase status.
 */
import { useEffect, useMemo, useState } from 'react';
import { isPreviewEngine } from '../ai/engine';
import { browserLabel } from '../ai/device';
import { historyStore } from '../data/history';
import { settingsStore } from '../data/settings';
import { isFirebaseConfigured, appCheckDebug, recaptchaSiteKey } from '../firebase/config';
import { deviceStore, downloadDeviceModel, rcStore, refreshDevice, useOnline } from '../lib/appState';
import { Icon } from '../components/Icon';
import { copyText } from '../components/ui';
import { getFirebase, readAppCheckDebugToken } from '../firebase/app';

const AVAIL_COPY: Record<string, { title: string; body: string; tone: 'ok' | 'warn' | 'info' | 'err' }> = {
  checking: { title: 'Checking…', body: 'Asking Chrome about its built-in model.', tone: 'info' },
  available: { title: 'Gemini Nano is ready', body: 'Messages are checked right here, on this device. Works offline too.', tone: 'ok' },
  downloadable: { title: 'Ready to download', body: 'Chrome can download Gemini Nano once (about 2 GB, on Wi-Fi). After that, checks are private and instant.', tone: 'warn' },
  downloading: { title: 'Downloading Gemini Nano…', body: 'You can keep using Chaukas. Until it finishes, Smart mode uses the cloud with personal details removed.', tone: 'info' },
  unavailable: { title: 'Not available on this device', body: 'This device or Chrome build cannot run Gemini Nano. Smart mode will use the cloud with personal details removed.', tone: 'err' },
  unsupported: { title: 'Browser has no built-in AI', body: 'Use Chrome 139 or newer on desktop to run checks on-device. Everything else still works through the cloud.', tone: 'err' },
};

export function EngineView() {
  const device = deviceStore.use();
  const rc = rcStore.use();
  const { runs } = historyStore.use();
  const mode = settingsStore.use().mode;
  const online = useOnline();

  useEffect(() => {
    refreshDevice();
  }, []);

  // Debug builds: show this browser's App Check debug token so it can be registered.
  const [debugToken, setDebugToken] = useState<string | null>(null);
  useEffect(() => {
    if (!appCheckDebug || !getFirebase()) return;
    let tries = 0;
    const look = () => readAppCheckDebugToken().then((t) => (t ? setDebugToken(t) : ++tries < 8 && setTimeout(look, 1000)));
    look();
  }, []);

  const perf = useMemo(() => {
    const by = (src: string) => runs.filter((r) => r.source === src);
    const avg = (xs: number[]) => (xs.length ? Math.round(xs.reduce((a, b) => a + b, 0) / xs.length) : null);
    return {
      device: { n: by('on_device').length, ms: avg(by('on_device').map((r) => r.latencyMs)) },
      cloud: { n: by('in_cloud').length, ms: avg(by('in_cloud').map((r) => r.latencyMs)) },
    };
  }, [runs]);

  const a = AVAIL_COPY[device.availability] ?? AVAIL_COPY.unavailable;
  const pct = device.progress !== null ? Math.round(device.progress * 100) : null;

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <span className="eyebrow">Under the hood</span>
          <h1 className="display page-title">Engine</h1>
        </div>
        <span className={`badge ${online ? 'device' : 'preview'}`}>
          <Icon name={online ? 'bolt' : 'wifiOff'} /> {online ? 'Online' : 'Offline'}
        </span>
      </header>

      {debugToken && (
        <section className="card card-pad callout warn debug-token">
          <div className="grow">
            <b>App Check debug token for this browser</b>
            <p className="xs subtle">Debug mode is on. Add this token in the Firebase console (App Check → Apps → your web app → ⋮ → Manage debug tokens) so cloud checks work here.</p>
            <code className="mono debug-token-value">{debugToken}</code>
          </div>
          <button className="btn soft sm" onClick={() => copyText(debugToken, 'Token copied')}>
            <Icon name="copy" /> Copy
          </button>
        </section>
      )}

      <section className={`card card-pad engine-hero e-${a.tone}`}>
        <div className="engine-chip-art" aria-hidden="true">
          <Icon name="chip" />
        </div>
        <div className="grow stack" style={{ gap: 6 }}>
          <span className="eyebrow">On-device model · {browserLabel()}</span>
          <h2 className="display" style={{ fontSize: 'var(--fs-2xl)' }}>{a.title}</h2>
          <p className="muted">{a.body}</p>
          {pct !== null && (
            <div style={{ marginTop: 8 }}>
              <div className="progress"><i style={{ width: `${pct}%` }} /></div>
              <span className="xs subtle mono">{pct}%</span>
            </div>
          )}
          {device.error && <div className="callout err"><Icon name="alert" />{device.error}</div>}
          <div className="row wrap" style={{ marginTop: 10 }}>
            {(device.availability === 'downloadable' || device.availability === 'downloading') && (
              <button className="btn lilac" onClick={downloadDeviceModel} disabled={pct !== null && pct < 100}>
                <Icon name="download" /> {device.availability === 'downloading' ? 'Resume download' : 'Download Gemini Nano'}
              </button>
            )}
            <button className="btn ghost" onClick={refreshDevice}>Re-check</button>
          </div>
        </div>
      </section>

      <section className="card card-pad">
        <div className="card-head">
          <h3 className="card-title"><span className="ic"><Icon name="arrowRight" /></span>How your message is routed</h3>
          <span className="badge lilac">Mode: {mode === 'private' ? 'Private' : mode === 'smart' ? 'Smart' : 'Most accurate'}</span>
        </div>
        <Flow mode={mode} deviceReady={device.availability === 'available'} />
      </section>

      <div className="engine-grid">
        <section className="card card-pad">
          <h3 className="card-title" style={{ marginBottom: 14 }}><span className="ic mint"><Icon name="bolt" /></span>Your speed</h3>
          <div className="perf">
            <div className="perf-col t-mint">
              <span className="xs subtle">On device</span>
              <span className="display">{perf.device.ms !== null ? `${perf.device.ms}` : '–'}<small>ms</small></span>
              <span className="xs subtle">{perf.device.n} checks</span>
            </div>
            <div className="perf-col t-sky">
              <span className="xs subtle">Cloud</span>
              <span className="display">{perf.cloud.ms !== null ? `${perf.cloud.ms}` : '–'}<small>ms</small></span>
              <span className="xs subtle">{perf.cloud.n} checks</span>
            </div>
          </div>
        </section>

        <section className="card card-pad">
          <h3 className="card-title" style={{ marginBottom: 14 }}><span className="ic"><Icon name="settings" /></span>Firebase services</h3>
          <ul className="svc">
            <Svc ok={!isPreviewEngine()} name="AI Logic (hybrid)" detail={isPreviewEngine() ? 'Preview mode, no config' : `Cloud model: ${rc.cloudModel}`} />
            <Svc ok={isFirebaseConfigured && (appCheckDebug || Boolean(recaptchaSiteKey))} name="App Check" detail={recaptchaSiteKey ? 'reCAPTCHA Enterprise' : appCheckDebug ? 'Debug token' : 'Not configured'} />
            <Svc ok={rc.loaded && isFirebaseConfigured} name="Remote Config" detail={rc.loaded && isFirebaseConfigured ? 'Live values' : 'Using built-in defaults'} />
            <Svc ok={isFirebaseConfigured} name="Auth + Firestore" detail={isFirebaseConfigured ? 'Family Circle and sync' : 'Off'} />
          </ul>
        </section>
      </div>

      <section className="card card-pad">
        <h3 className="card-title" style={{ marginBottom: 6 }}><span className="ic peach"><Icon name="wand" /></span>Turn on Gemini Nano in Chrome</h3>
        <p className="muted small" style={{ marginBottom: 14 }}>One-time setup for developers and judges running locally.</p>
        <ol className="setup">
          <li>Use <b>Chrome 139 or newer</b> on Windows, macOS, Linux or ChromeOS with at least 22 GB free disk space.</li>
          <li>
            Open <FlagLink href="chrome://flags/#prompt-api-for-gemini-nano-multimodal-input" /> and set it to <b>Enabled</b>.
          </li>
          <li>Restart Chrome, come back here and press <b>Download Gemini Nano</b>.</li>
          <li>Optional: check from DevTools with <code>await LanguageModel.availability()</code>.</li>
        </ol>
      </section>
    </div>
  );
}

function FlagLink({ href }: { href: string }) {
  return (
    <button className="flag-link mono" onClick={() => copyText(href, 'Flag address copied, paste it in the address bar')}>
      {href.replace('chrome://flags/', '')} <Icon name="copy" />
    </button>
  );
}

function Svc({ ok, name, detail }: { ok: boolean; name: string; detail: string }) {
  return (
    <li>
      <span className={`svc-dot ${ok ? 'ok' : ''}`}>{ok ? <Icon name="check" /> : <Icon name="x" />}</span>
      <span className="grow">
        <b className="small">{name}</b>
        <span className="xs subtle">{detail}</span>
      </span>
    </li>
  );
}

function Flow({ mode, deviceReady }: { mode: string; deviceReady: boolean }) {
  const localActive = mode !== 'cloud' && deviceReady;
  const cloudActive = mode === 'cloud' || (mode === 'smart' && !deviceReady);
  return (
    <div className="flow">
      <div className="flow-node">
        <Icon name="message" />
        <b>Your message</b>
        <span className="xs subtle">+ instant pattern checks</span>
      </div>
      <div className="flow-split">
        <div className={`flow-lane ${localActive ? 'on' : ''}`}>
          <span className="flow-line" />
          <div className="flow-node t-mint">
            <Icon name="laptop" />
            <b>Gemini Nano</b>
            <span className="xs subtle">in Chrome, private</span>
          </div>
        </div>
        <div className={`flow-lane ${cloudActive ? 'on' : ''} ${mode === 'private' ? 'off' : ''}`}>
          <span className="flow-line" />
          <div className="flow-node t-butter">
            <Icon name="eye" />
            <b>Redact</b>
            <span className="xs subtle">names, numbers, codes</span>
          </div>
          <span className="flow-line short" />
          <div className="flow-node t-sky">
            <Icon name="cloud" />
            <b>Gemini in the cloud</b>
            <span className="xs subtle">via Firebase AI Logic</span>
          </div>
        </div>
      </div>
      <p className="xs subtle" style={{ marginTop: 10 }}>
        {mode === 'private'
          ? 'Private: only the on-device path is allowed (InferenceMode.ONLY_ON_DEVICE).'
          : mode === 'smart'
            ? 'Smart: on-device first, cloud fallback with redaction (InferenceMode.PREFER_ON_DEVICE).'
            : 'Most accurate: cloud first with redaction, on-device if offline (InferenceMode.PREFER_IN_CLOUD).'}
      </p>
    </div>
  );
}
