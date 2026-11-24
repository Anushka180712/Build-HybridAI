import { collection, deleteDoc, doc, limit, onSnapshot, orderBy, query, serverTimestamp, setDoc } from 'firebase/firestore';
import { getFirebase } from '../firebase/app';
import { log } from '../lib/log';
import { createStore } from '../lib/store';
import { redact } from '../ai/redact';
import { excerpt } from '../lib/format';
import type { AnalysisRun } from '../ai/types';
import { engineSettings, settingsStore } from './settings';

/**
 * History lives on the device. When the person is signed in and sync is on,
 * a minimal record is mirrored to Firestore: the verdict and a redacted
 * excerpt only, never the full message.
 */
export interface HistoryState {
  runs: AnalysisRun[];
}

export const historyStore = createStore<HistoryState>('chaukas.history.v1', { runs: [] });

/**
 * Running totals that only go up. Deleting a check from History or clearing it does not lower
 * them. A scam is a result of 65 or more that is not marked safe, the same line used for sharing.
 */
export const countsStore = createStore('chaukas.counts.v1', { checked: 0, scams: 0, since: 0 });
export const countsAsScam = (r: AnalysisRun) => r.analysis.verdict !== 'safe' && r.analysis.riskScore >= 65;

/** Starts the totals from whatever History already holds, the first time they are used. */
function seedCounts() {
  if (countsStore.get().since) return;
  const runs = historyStore.get().runs;
  countsStore.set({ checked: runs.length, scams: runs.filter(countsAsScam).length, since: Date.now() });
}

export interface SyncedCheck {
  id: string;
  verdict: string;
  riskScore: number;
  category: string;
  summary: string;
  excerpt: string;
  source: string;
  createdAt: number;
}

export function toSynced(run: AnalysisRun): SyncedCheck {
  return {
    id: run.id,
    verdict: run.analysis.verdict,
    riskScore: run.analysis.riskScore,
    category: run.analysis.category,
    summary: redact(run.analysis.summary, engineSettings().privateTerms).text.slice(0, 400),
    excerpt: excerpt(redact(run.input, engineSettings().privateTerms).text, 160),
    source: run.source,
    createdAt: run.createdAt,
  };
}

export function saveRun(run: AnalysisRun) {
  seedCounts();
  // Count each check once: a second opinion re-saves the same check.
  if (!historyStore.get().runs.some((r) => r.id === run.id)) {
    countsStore.set((c) => ({ ...c, checked: c.checked + 1, scams: c.scams + (countsAsScam(run) ? 1 : 0) }));
  }
  historyStore.set((s) => ({ runs: [run, ...s.runs.filter((r) => r.id !== run.id)].slice(0, 150) }));
  const fb = getFirebase();
  const user = fb?.auth.currentUser;
  if (fb && user && settingsStore.get().syncHistory) {
    setDoc(doc(fb.db, 'users', user.uid, 'checks', run.id), { ...toSynced(run), syncedAt: serverTimestamp() }).catch((e) =>
      log.warn('history', 'sync to Firestore failed', e),
    );
  }
}

export function deleteRun(id: string) {
  historyStore.set((s) => ({ runs: s.runs.filter((r) => r.id !== id) }));
  const fb = getFirebase();
  const user = fb?.auth.currentUser;
  if (fb && user) deleteDoc(doc(fb.db, 'users', user.uid, 'checks', id)).catch(() => undefined);
}

export function clearHistory() {
  historyStore.set({ runs: [] });
}

/** Checks synced from other devices (for example the Android app). */
export function listenSyncedChecks(uid: string, cb: (items: SyncedCheck[]) => void) {
  const fb = getFirebase();
  if (!fb) return () => undefined;
  const q = query(collection(fb.db, 'users', uid, 'checks'), orderBy('createdAt', 'desc'), limit(100));
  return onSnapshot(
    q,
    (snap) => cb(snap.docs.map((d) => d.data() as SyncedCheck)),
    () => cb([]),
  );
}
