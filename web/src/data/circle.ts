/**
 * Family Circle on Firestore: create, join, leave, share alerts and react. Firestore rules
 * enforce that people can only add or remove themselves.
 */
import {
  addDoc,
  arrayRemove,
  arrayUnion,
  deleteDoc,
  deleteField,
  collection,
  doc,
  getDoc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  getDocs,
  Timestamp,
} from 'firebase/firestore';
import { getFirebase } from '../firebase/app';
import { redact } from '../ai/redact';
import { excerpt } from '../lib/format';
import type { AnalysisRun } from '../ai/types';
import { engineSettings, settingsStore } from './settings';
import { log } from '../lib/log';

/**
 * Family Circle: a small group (for example a parent and their children)
 * who see each other's flagged messages. Only redacted excerpts are shared.
 */
export interface Circle {
  id: string;
  name: string;
  ownerUid: string;
  members: string[];
  memberNames: Record<string, string>;
  /** Each member's Chaukas ID (like riya-4821), written by that member. */
  memberHandles?: Record<string, string>;
  /** People added by ID who have not chosen Stay or Leave yet. */
  invited?: string[];
  invitedNames?: Record<string, string>;
}

export interface CircleAlert {
  id: string;
  byUid: string;
  byName: string;
  verdict: string;
  riskScore: number;
  category: string;
  summary: string;
  excerpt: string;
  note: string;
  createdAt: number;
  reactions: Record<string, string>;
}

const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
export function newCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join('');
}

function need() {
  const fb = getFirebase();
  const user = fb?.auth.currentUser;
  if (!fb || !user) throw new Error('Sign in to use Family Circle.');
  return { fb, user };
}

const displayName = (fallback: string) => (getFirebase()?.auth.currentUser?.displayName || fallback).slice(0, 60);

export async function createCircle(name: string): Promise<string> {
  const { fb, user } = need();
  // A code collision looks like a forbidden update to someone else's circle, so retry with a fresh code.
  let lastError: unknown;
  for (let attempt = 0; attempt < 3; attempt++) {
    const code = newCode();
    try {
      await setDoc(doc(fb.db, 'circles', code), {
        name: name.trim().slice(0, 40) || 'Family',
        ownerUid: user.uid,
        members: [user.uid],
        memberNames: { [user.uid]: displayName('Me') },
        invited: [],
        invitedNames: {},
        invitedBy: {},
        createdAt: serverTimestamp(),
      });
      return code;
    } catch (e) {
      lastError = e;
    }
  }
  throw lastError instanceof Error ? lastError : new Error('Could not create the circle.');
}

/** Leave a group. The last person out deletes it (when they created it). */
export async function leaveCircle(circle: Circle) {
  const { fb, user } = need();
  const ref = doc(fb.db, 'circles', circle.id);
  const alone = circle.members.every((m) => m === user.uid);
  if (alone && circle.ownerUid === user.uid) await deleteDoc(ref);
  else {
    const handle = circle.memberHandles?.[user.uid] ? { [`memberHandles.${user.uid}`]: deleteField() } : {};
    await updateDoc(ref, { members: arrayRemove(user.uid), [`memberNames.${user.uid}`]: deleteField(), ...handle });
  }
}

/** Delete a group completely (for owners/creators). */
export async function deleteCircle(circle: Circle) {
  const { fb, user } = need();
  const ref = doc(fb.db, 'circles', circle.id);
  if (circle.ownerUid === user.uid || circle.members.every((m) => m === user.uid)) {
    await deleteDoc(ref);
  } else {
    await leaveCircle(circle);
  }
}

/** A person can be in this many family groups at once. */
export const MAX_GROUPS = 2;

/** The message shown when someone already in MAX_GROUPS groups tries to join another. */
export const GROUP_LIMIT_MESSAGE = `You can be in ${MAX_GROUPS} groups at a time. Leave one group first, then try again.`;

/** Throws when joining `target` would put me in more than MAX_GROUPS groups. */
export async function checkGroupLimit(target: string) {
  const { fb, user } = need();
  const snap = await getDocs(query(collection(fb.db, 'circles'), where('members', 'array-contains', user.uid)));
  const others = snap.docs.filter((d) => d.id !== target).length;
  if (others >= MAX_GROUPS) throw new Error(GROUP_LIMIT_MESSAGE);
}

/** Shows my Chaukas ID to the rest of the group. Best effort: the group works without it. */
export async function setMyHandleIn(circleId: string, handle: string) {
  const { fb, user } = need();
  await updateDoc(doc(fb.db, 'circles', circleId), { [`memberHandles.${user.uid}`]: handle });
}

export async function joinCircle(codeRaw: string): Promise<string> {
  const { fb, user } = need();
  const code = codeRaw.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (code.length !== 6) throw new Error('Circle codes have 6 characters.');
  const ref = doc(fb.db, 'circles', code);
  // Rules allow a signed-in user to add only themselves to `members`. A wrong
  // code fails the rule check (there is no document), so both errors mean the same.
  try {
    await checkGroupLimit(code);
    await updateDoc(ref, { members: arrayUnion(user.uid), [`memberNames.${user.uid}`]: displayName('Member') });
    const snap = await getDoc(ref);
    if (!snap.exists()) throw new Error('not-found');
  } catch (e) {
    if (e instanceof Error && e.message === GROUP_LIMIT_MESSAGE) throw e;
    const reason = (e as { code?: string }).code ?? (e as Error).message;
    if (/not-found|permission-denied|insufficient/i.test(reason)) throw new Error('No circle with that code. Check the letters and try again.');
    throw e;
  }
  return code;
}

export function listenMyCircles(uid: string, cb: (c: Circle[]) => void) {
  const fb = getFirebase();
  if (!fb) return () => undefined;
  const q = query(collection(fb.db, 'circles'), where('members', 'array-contains', uid));
  return onSnapshot(
    q,
    (snap) => cb(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Circle, 'id'>) }))),
    (err) => { log.warn('circle', 'circles listener stopped', err); cb([]); },
  );
}

/** Only messages this risky go to the family group: scam, or suspicious at 65 or more. */
export const SHARE_MIN_RISK = 65;
export const isShareable = (a: { verdict: string; riskScore: number }) => a.verdict !== 'safe' && a.riskScore >= SHARE_MIN_RISK;

/** Group warnings disappear after 3 days. Firestore's TTL policy on `expireAt` deletes them. */
export const ALERT_DAYS = 3;
const ALERT_MS = ALERT_DAYS * 24 * 60 * 60 * 1000;

export function listenAlerts(circleId: string, cb: (a: CircleAlert[]) => void) {
  const fb = getFirebase();
  if (!fb) return () => undefined;
  const q = query(collection(fb.db, 'circles', circleId, 'alerts'), orderBy('createdAt', 'desc'), limit(50));
  let stop: () => void = () => undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let closed = false;
  // A circle that was just created or joined can reach this listener before the server has
  // the membership, which the rules see as "not a member". Retry a few times before giving up.
  const start = (attempt: number) => {
    stop = onSnapshot(
      q,
      (snap) =>
        cb(
          snap.docs
            .map((d) => {
              const v = d.data();
              const ts = v.createdAt as Timestamp | null;
              return { id: d.id, ...(v as Omit<CircleAlert, 'id' | 'createdAt'>), createdAt: ts?.toMillis?.() ?? Date.now(), reactions: v.reactions ?? {} };
            })
            // Hidden as soon as they are 3 days old, even before the TTL policy removes them.
            .filter((a) => Date.now() - a.createdAt < ALERT_MS),
        ),
      (err) => {
        if (closed) return;
        if ((err as { code?: string }).code === 'permission-denied' && attempt < 5) {
          timer = setTimeout(() => !closed && start(attempt + 1), 600 * 2 ** attempt);
          return;
        }
        log.warn('circle', 'alerts listener stopped', err);
        cb([]);
      },
    );
  };
  start(0);
  return () => {
    closed = true;
    clearTimeout(timer);
    stop();
  };
}

export async function shareToCircle(circleId: string, run: AnalysisRun, note: string) {
  const { fb, user } = need();
  const terms = engineSettings().privateTerms;
  await addDoc(collection(fb.db, 'circles', circleId, 'alerts'), {
    byUid: user.uid,
    byName: displayName('Someone'),
    verdict: run.analysis.verdict,
    riskScore: run.analysis.riskScore,
    category: run.analysis.category,
    summary: redact(run.analysis.summary, terms).text.slice(0, 400),
    excerpt: excerpt(redact(run.input, terms).text, 220),
    note: note.trim().slice(0, 280),
    createdAt: serverTimestamp(),
    expireAt: Timestamp.fromMillis(Date.now() + ALERT_MS),
    reactions: {},
  });
}

export async function reactToAlert(circleId: string, alertId: string, reaction: string) {
  const { fb, user } = need();
  await updateDoc(doc(fb.db, 'circles', circleId, 'alerts', alertId), { [`reactions.${user.uid}`]: reaction });
}

/**
 * Shares a scam or suspicious result with every group I'm in, when that setting is on (it is by
 * default and can be turned off in Family Circle). Only the redacted excerpt and summary go to
 * the group, exactly as with "Tell my circle". Returns the names of the groups it went to.
 */
export async function autoShareToCircles(run: AnalysisRun): Promise<string[]> {
  if (!settingsStore.get().autoShareCircle || !isShareable(run.analysis)) return [];
  const fb = getFirebase();
  const user = fb?.auth.currentUser;
  if (!fb || !user) return [];
  const snap = await getDocs(query(collection(fb.db, 'circles'), where('members', 'array-contains', user.uid)));
  const circles = snap.docs.map((d) => ({ id: d.id, name: String(d.data().name ?? 'Family') }));
  const done = await Promise.allSettled(circles.map((c) => shareToCircle(c.id, run, '')));
  return circles.filter((_, i) => done[i].status === 'fulfilled').map((c) => c.name);
}
