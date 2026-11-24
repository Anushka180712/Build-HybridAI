/**
 * Reads the words in a screenshot on this device (like Google Lens), so a screenshot can be
 * checked in the cloud as cleaned text instead of sending the picture. Uses Tesseract (English),
 * served by the app itself from /ocr and loaded only the first time it is needed. Gives up
 * after `timeoutMs` so a slow phone falls back to the normal choice.
 */
import { log } from './log';

export async function readTextOnDevice(imageUrl: string, timeoutMs = 9000): Promise<string | null> {
  let worker: { recognize(src: string): Promise<{ data: { text: string } }>; terminate(): Promise<unknown> } | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const started = performance.now();
  let gaveUp = false;
  try {
    const work = (async () => {
      const { createWorker } = await import('tesseract.js');
      const w = await createWorker('eng', 1, { workerPath: '/ocr/worker.min.js', corePath: '/ocr', langPath: '/ocr', gzip: true });
      worker = w;
      // Started too late: free the worker straight away.
      if (gaveUp) {
        void w.terminate().catch(() => undefined);
        return null;
      }
      const { data } = await w.recognize(imageUrl);
      return data.text;
    })();
    const timeout = new Promise<null>((resolve) => {
      timer = setTimeout(() => resolve(null), timeoutMs);
    });
    const text = await Promise.race([work, timeout]);
    log.debug('ocr', `read ${text?.length ?? 0} chars in ${Math.round(performance.now() - started)} ms`);
    const clean = text?.replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim() ?? '';
    // Too little text means it isn't a message screenshot (or the reading failed).
    return clean.replace(/[^A-Za-z0-9]/g, '').length >= 12 ? clean : null;
  } catch (e) {
    log.warn('ocr', 'could not read the screenshot', e);
    return null;
  } finally {
    gaveUp = true;
    clearTimeout(timer);
    void (worker as { terminate(): Promise<unknown> } | null)?.terminate().catch(() => undefined);
  }
}
