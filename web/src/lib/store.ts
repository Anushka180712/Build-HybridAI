import { useSyncExternalStore } from 'react';

/** Tiny observable store persisted to localStorage (wrapped in try/catch). */
export function createStore<T>(key: string, initial: T) {
  let state: T = initial;
  const persist = !key.endsWith('.ephemeral');
  try {
    if (!persist) throw 0;
    const raw = localStorage.getItem(key);
    if (raw) state = { ...initial, ...JSON.parse(raw) };
  } catch {
    /* storage unavailable: keep defaults */
  }
  const listeners = new Set<() => void>();
  const get = () => state;
  const set = (patch: Partial<T> | ((s: T) => Partial<T>)) => {
    const next = typeof patch === 'function' ? patch(state) : patch;
    state = { ...state, ...next };
    try {
      if (persist) localStorage.setItem(key, JSON.stringify(state));
    } catch {
      /* ignore */
    }
    listeners.forEach((l) => l());
  };
  const subscribe = (l: () => void) => {
    listeners.add(l);
    return () => listeners.delete(l);
  };
  const use = () => useSyncExternalStore(subscribe, get, get);
  return { get, set, subscribe, use };
}
