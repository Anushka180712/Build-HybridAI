/**
 * Hash routing and small shared stores for the device model and Remote Config.
 */
import { useEffect, useState } from 'react';
import { createStore } from './store';
import { deviceAvailability } from '../ai/device';
import { prepareOnDeviceModel } from '../ai/engine';
import { RC_DEFAULTS, loadRemoteConfig, type ChaukasRemoteConfig } from '../firebase/remoteConfig';
import type { DeviceAvailability } from '../ai/types';

export type Route = 'check' | 'history' | 'circle' | 'dojo' | 'engine' | 'settings';
const ROUTES: Route[] = ['check', 'history', 'circle', 'dojo', 'engine', 'settings'];

function parseHash(): { route: Route; param?: string } {
  const [, r, param] = location.hash.replace(/^#/, '').split('/');
  return { route: ROUTES.includes(r as Route) ? (r as Route) : 'check', param };
}

export function useRoute() {
  const [state, setState] = useState(parseHash);
  useEffect(() => {
    const on = () => {
      setState(parseHash());
      window.scrollTo({ top: 0 });
    };
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return state;
}

export const navigate = (route: Route, param?: string) => {
  location.hash = `/${route}${param ? `/${param}` : ''}`;
};

/* ---------- On-device model status ---------- */
export interface DeviceState {
  availability: DeviceAvailability | 'checking';
  progress: number | null;
  error: string | null;
}
export const deviceStore = createStore<DeviceState>('chaukas.device.ephemeral', { availability: 'checking', progress: null, error: null });
deviceStore.set({ availability: 'checking', progress: null, error: null });

export async function refreshDevice() {
  const availability = await deviceAvailability();
  deviceStore.set({ availability });
  return availability;
}

export async function downloadDeviceModel() {
  deviceStore.set({ progress: 0, error: null, availability: 'downloading' });
  try {
    await prepareOnDeviceModel((p) => deviceStore.set({ progress: p }));
    deviceStore.set({ progress: 1 });
  } catch (e) {
    deviceStore.set({ error: e instanceof Error ? e.message : String(e) });
  } finally {
    await refreshDevice();
    setTimeout(() => deviceStore.set({ progress: null }), 1500);
  }
}

/* ---------- Remote Config ---------- */
export const rcStore = createStore<ChaukasRemoteConfig & { loaded: boolean }>('chaukas.rc.ephemeral', { ...RC_DEFAULTS, loaded: false });
export async function initRemoteConfig() {
  const rc = await loadRemoteConfig();
  rcStore.set({ ...rc, loaded: true });
}

/* ---------- Online status ---------- */
export function useOnline() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const on = () => setOnline(navigator.onLine);
    window.addEventListener('online', on);
    window.addEventListener('offline', on);
    return () => {
      window.removeEventListener('online', on);
      window.removeEventListener('offline', on);
    };
  }, []);
  return online;
}

/**
 * What another app shared into Chaukas through the PWA share target (or a hand-off link).
 * Read once at startup, then the query string is cleared so a reload does not re-run it.
 */
const shared = (() => {
  const params = new URLSearchParams(location.search);
  const text = [params.get('title'), params.get('text'), params.get('url')].filter(Boolean).join('\n');
  const image = params.get('image') === '1';
  const auto = params.get('shared') === '1';
  if (text || image || auto) history.replaceState(null, '', location.pathname + location.hash);
  return { text, image, auto };
})();

/** True when this visit carries shared content, so start-up redirects should leave it alone. */
export const arrivedWithContent = !!(shared.text || shared.image || shared.auto);

/** Prefill text from a share or hand-off link. */
export function takeSharedText(): string {
  const t = shared.text;
  shared.text = '';
  return t;
}

/** True when the content came from the share sheet, so the check can start by itself. */
export function cameFromShare(): boolean {
  const a = shared.auto;
  shared.auto = false;
  return a;
}

/** A screenshot shared from another app, parked in the service worker's cache. */
export async function takeSharedImage(): Promise<Blob | null> {
  if (!shared.image || !('caches' in window)) return null;
  shared.image = false;
  try {
    const cache = await caches.open('chaukas-share');
    const res = await cache.match('/shared-image');
    await cache.delete('/shared-image');
    return res ? await res.blob() : null;
  } catch {
    return null;
  }
}
