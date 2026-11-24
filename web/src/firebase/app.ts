/**
 * Firebase initialisation: App Check (debug token or reCAPTCHA Enterprise), Auth and Firestore
 * with an offline cache. Returns null when no config is present, which switches the app to
 * preview mode.
 */
import { log } from '../lib/log';
import { initializeApp, type FirebaseApp } from 'firebase/app';
import { initializeAppCheck, ReCaptchaEnterpriseProvider, CustomProvider, type AppCheck } from 'firebase/app-check';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import { connectFirestoreEmulator, getFirestore, initializeFirestore, persistentLocalCache, persistentMultipleTabManager, type Firestore } from 'firebase/firestore';
import { firebaseConfig, isFirebaseConfigured, recaptchaSiteKey, appCheckDebug } from './config';

declare global {
  // eslint-disable-next-line no-var
  var FIREBASE_APPCHECK_DEBUG_TOKEN: boolean | string | undefined;
}

export interface FirebaseHandles {
  app: FirebaseApp;
  appCheck: AppCheck | null;
  auth: Auth;
  db: Firestore;
}

let handles: FirebaseHandles | null = null;

/**
 * Initialises Firebase once. Returns null when the app is running without a
 * Firebase config (preview mode), so every caller must handle that case.
 */
export function getFirebase(): FirebaseHandles | null {
  if (!isFirebaseConfigured) return null;
  if (handles) return handles;

  const app = initializeApp(firebaseConfig as Record<string, string>);

  // App Check protects the Gemini API behind Firebase AI Logic. In local
  // development a debug token is used; register it in the console.
  let appCheck: AppCheck | null = null;
  try {
    if (appCheckDebug) self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
    const provider = recaptchaSiteKey
      ? new ReCaptchaEnterpriseProvider(recaptchaSiteKey)
      : // With the debug token flag set, the SDK never calls this provider.
        new CustomProvider({
          getToken: () => Promise.reject(new Error('Set VITE_RECAPTCHA_ENTERPRISE_SITE_KEY or enable the App Check debug token.')),
        });
    appCheck = initializeAppCheck(app, { provider, isTokenAutoRefreshEnabled: true });
  } catch (err) {
    log.warn('app-check', 'could not start, cloud calls may be refused', err);
  }

  let db: Firestore;
  try {
    db = initializeFirestore(app, {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    });
  } catch {
    db = getFirestore(app);
  }

  const auth = getAuth(app);
  // Local testing against the Firebase emulators (firebase emulators:start --only auth,firestore).
  if (import.meta.env.DEV && import.meta.env.VITE_USE_EMULATORS === 'true') {
    connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
    connectFirestoreEmulator(db, '127.0.0.1', 8080);
  }

  handles = { app, appCheck, auth, db };
  return handles;
}

/**
 * With VITE_APPCHECK_DEBUG=true, the App Check SDK creates one debug token per browser and keeps
 * it in IndexedDB. This reads it back so the Engine page can show it with a Copy button (handy
 * on a phone, where the browser console is out of reach). Resolves null when debug mode is off
 * or the token does not exist yet.
 */
export function readAppCheckDebugToken(): Promise<string | null> {
  if (!appCheckDebug || typeof indexedDB === 'undefined') return Promise.resolve(null);
  return new Promise((resolve) => {
    const open = indexedDB.open('firebase-app-check-database');
    open.onerror = () => resolve(null);
    open.onsuccess = () => {
      const db = open.result;
      try {
        const req = db.transaction('firebase-app-check-store', 'readonly').objectStore('firebase-app-check-store').get('debug-token');
        req.onsuccess = () => resolve((req.result as { value?: string } | undefined)?.value ?? null);
        req.onerror = () => resolve(null);
      } catch {
        resolve(null);
      } finally {
        db.close();
      }
    };
  });
}
