/**
 * App shell: sidebar and bottom tab bar navigation, routes, toasts and global comfort
 * settings.
 */
import { useEffect } from 'react';
import { useT } from './lib/i18n';
import { useRoute, navigate, deviceStore, refreshDevice, initRemoteConfig, useOnline, type Route } from './lib/appState';
import { settingsStore } from './data/settings';
import { historyStore, countsAsScam, countsStore } from './data/history';
import { isPreviewEngine } from './ai/engine';
import { Icon, Logo, type IconName } from './components/Icon';
import { Toasts } from './components/ui';
import { CircleNotifier } from './components/CircleNotifier';
import { isFirebaseConfigured } from './firebase/config';
import { CheckView } from './views/Check';
import { HistoryView } from './views/History';
import { CircleView } from './views/Circle';
import { DojoView } from './views/Dojo';
import { EngineView } from './views/Engine';
import { SettingsView } from './views/Settings';

const NAV: { route: Route; label: string; icon: IconName; tint: string }[] = [
  { route: 'check', label: 'Check', icon: 'shieldCheck', tint: 'lilac' },
  { route: 'history', label: 'History', icon: 'history', tint: 'sky' },
  { route: 'circle', label: 'Family', icon: 'users', tint: 'peach' },
  { route: 'dojo', label: 'Dojo', icon: 'target', tint: 'mint' },
];

export function App() {
  const { route, param } = useRoute();
  const settings = settingsStore.use();
  const device = deviceStore.use();
  const { runs } = historyStore.use();
  const online = useOnline();
  const t = useT();

  useEffect(() => {
    refreshDevice();
    initRemoteConfig();
  }, []);

  // The whole page follows the language picked on the home screen.
  useEffect(() => {
    document.documentElement.lang = t.lang;
  }, [t.lang]);

  useEffect(() => {
    document.documentElement.dataset.largeText = String(settings.largeText);
    document.documentElement.dataset.reduceMotion = String(settings.reduceMotion);
  }, [settings.largeText, settings.reduceMotion]);

  const counts = countsStore.use();
  const scams = Math.max(counts.scams, runs.filter(countsAsScam).length);
  const deviceReady = device.availability === 'available';


  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand-row">
          <a className="brand" href="#/check">
            <Logo />
            <span>
              <b className="display">Chaukas</b>
              <span className="xs subtle">{t('Scam shield')}</span>
            </span>
          </a>
          <a className={`btn icon ghost gear ${route === 'settings' ? 'on' : ''}`} href="#/settings" aria-label={t('Settings')} title={t('Settings')}>
            <Icon name="settings" />
          </a>
        </div>
        <nav className="nav" aria-label="Main">
          {NAV.map((n) => (
            <a key={n.route} href={`#/${n.route}`} className={`nav-item t-${n.tint} ${route === n.route ? 'on' : ''}`} aria-current={route === n.route ? 'page' : undefined}>
              <span className="nav-ic"><Icon name={n.icon} /></span>
              {t(n.label === 'Family' ? 'Family Circle' : n.label === 'Dojo' ? 'Scam Dojo' : n.label)}
              {n.route === 'history' && runs.length > 0 && <span className="nav-count">{runs.length}</span>}
            </a>
          ))}
        </nav>

        <button className={`engine-mini ${deviceReady ? 'ready' : ''}`} onClick={() => navigate('engine')}>
          <span className="engine-mini-ic"><Icon name={deviceReady ? 'laptop' : online ? 'cloud' : 'wifiOff'} /></span>
          <span className="grow" style={{ textAlign: 'left' }}>
            <b className="small">{isPreviewEngine() ? 'Preview mode' : deviceReady ? 'On-device ready' : device.availability === 'downloading' ? 'Downloading model' : 'Cloud fallback'}</b>
            <span className="xs subtle">{deviceReady ? 'Gemini Nano in Chrome' : isPreviewEngine() ? 'Connect Firebase' : 'Set up Gemini Nano'}</span>
          </span>
          {device.progress !== null && <span className="mono xs">{Math.round(device.progress * 100)}%</span>}
        </button>
        {scams > 0 && (
          <div className="caught">
            <span className="display">{scams}</span>
            <span className="xs">scam{scams === 1 ? '' : 's'} caught</span>
          </div>
        )}
      </aside>

      <header className="topbar">
        <a className="brand" href="#/check">
          <Logo size={28} />
          <b className="display">Chaukas</b>
        </a>
        <div className="row">
          <span className={`badge ${deviceReady ? 'device' : isPreviewEngine() ? 'preview' : 'cloud'}`}>
            <Icon name={deviceReady ? 'laptop' : isPreviewEngine() ? 'eye' : 'cloud'} />
            {deviceReady ? 'On device' : isPreviewEngine() ? 'Preview' : 'Cloud'}
          </span>
          <a className="btn icon ghost" href="#/settings" aria-label="Settings"><Icon name="settings" /></a>
        </div>
      </header>

      <main className="main" id="main">
        {isPreviewEngine() && route === 'check' && (
          <div className="preview-note">
            <Icon name="info" />
            <span>
              <b>Preview mode.</b> No Firebase project is connected, so answers come from built-in pattern checks. Add your config to <code>web/.env.local</code> to use Gemini.
            </span>
          </div>
        )}
        {!online && (
          <div className="preview-note offline">
            <Icon name="wifiOff" />
            <span>You are offline. {deviceReady ? 'Checks keep working on this device.' : 'Download Gemini Nano in Engine so checks work offline.'}</span>
          </div>
        )}
        <div key={route + (param ?? '')} className="route-fade">
          {route === 'check' && <CheckView />}
          {route === 'history' && <HistoryView param={param} />}
          {route === 'circle' && <CircleView param={param} />}
          {route === 'dojo' && <DojoView />}
          {route === 'engine' && <EngineView />}
          {route === 'settings' && <SettingsView />}
        </div>
      </main>

      <nav className="tabbar" aria-label="Main">
        {NAV.map((n) => (
          <a key={n.route} href={`#/${n.route}`} className={`tab t-${n.tint} ${route === n.route ? 'on' : ''}`}>
            <span className="tab-ic"><Icon name={n.icon} /></span>
            {t(n.label)}
          </a>
        ))}
      </nav>
      {isFirebaseConfigured && <CircleNotifier />}
      <Toasts />
    </div>
  );
}
