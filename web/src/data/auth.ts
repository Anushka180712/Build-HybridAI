/**
 * Firebase Authentication: anonymous by default, with optional Google sign-in for Family
 * Circle and sync.
 */
import { useEffect, useState } from 'react';
import { GoogleAuthProvider, linkWithPopup, onAuthStateChanged, signInAnonymously, signInWithPopup, signOut, updateProfile, type User } from 'firebase/auth';
import { getFirebase } from '../firebase/app';

const NAME_EVENT = 'chaukas-name';

export function useUser(): { user: User | null; ready: boolean } {
  const fb = getFirebase();
  const [user, setUser] = useState<User | null>(fb?.auth.currentUser ?? null);
  const [ready, setReady] = useState(!fb);
  const [, setNameVersion] = useState(0);
  useEffect(() => {
    if (!fb) return;
    // Firebase does not announce a new display name, so signInWithName does.
    const renamed = () => setNameVersion((n) => n + 1);
    window.addEventListener(NAME_EVENT, renamed);
    const stop = onAuthStateChanged(fb.auth, (u) => {
      setUser(u);
      setReady(true);
    });
    return () => {
      stop();
      window.removeEventListener(NAME_EVENT, renamed);
    };
  }, [fb]);
  return { user, ready };
}

/** Lightweight sign-in with just a first name, upgradeable to Google later. */
export async function signInWithName(name: string): Promise<User> {
  const fb = getFirebase();
  if (!fb) throw new Error('Firebase is not configured.');
  const cred = fb.auth.currentUser ? { user: fb.auth.currentUser } : await signInAnonymously(fb.auth);
  if (name.trim()) {
    await updateProfile(cred.user, { displayName: name.trim().slice(0, 40) });
    window.dispatchEvent(new Event(NAME_EVENT));
  }
  return cred.user;
}

export async function signInWithGoogle(): Promise<User> {
  const fb = getFirebase();
  if (!fb) throw new Error('Firebase is not configured.');
  const provider = new GoogleAuthProvider();
  const current = fb.auth.currentUser;
  if (current?.isAnonymous) {
    try {
      return (await linkWithPopup(current, provider)).user;
    } catch {
      /* already linked elsewhere: fall through to a normal sign-in */
    }
  }
  return (await signInWithPopup(fb.auth, provider)).user;
}

export async function signOutUser() {
  const fb = getFirebase();
  if (fb) await signOut(fb.auth);
}

export function displayName(user: User | null): string {
  return user?.displayName || (user?.isAnonymous ? 'Guest' : user?.email?.split('@')[0]) || 'Guest';
}
