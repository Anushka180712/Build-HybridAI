/**
 * Tagged console logging. Debug lines (routing decisions, timings) appear only
 * in development builds; warnings always do, because each one explains why a
 * feature is running in a degraded mode.
 */
const TAG = '[Chaukas]';

export const log = {
  debug(scope: string, message: string, data?: unknown) {
    if (import.meta.env.DEV) console.debug(`${TAG} ${scope}: ${message}`, data ?? '');
  },
  warn(scope: string, message: string, error?: unknown) {
    console.warn(`${TAG} ${scope}: ${message}`, error ?? '');
  },
};
