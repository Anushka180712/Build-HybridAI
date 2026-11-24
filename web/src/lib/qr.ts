/**
 * QR and UPI inspector. Decodes QR codes in screenshots with the browser's
 * built-in BarcodeDetector (fully on-device) and explains what scanning the
 * code would actually do, which is the heart of "scan to receive money" scams.
 */
export interface QrFinding {
  raw: string;
  kind: 'upi' | 'url' | 'text';
  /** Plain-language explanation of what the code does. */
  headline: string;
  details: { label: string; value: string }[];
  warnings: string[];
}

interface Detector {
  detect(source: ImageBitmapSource): Promise<{ rawValue: string }[]>;
}

export function qrSupported(): boolean {
  return 'BarcodeDetector' in globalThis;
}

/**
 * Decodes every QR code in an image, entirely on this device. Uses the native
 * BarcodeDetector where Chrome ships it (Android, macOS, ChromeOS) and falls
 * back to jsQR, a small pure-JS decoder, everywhere else (e.g. Windows, Linux).
 */
export async function decodeQr(blob: Blob): Promise<string[]> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(blob);
  } catch {
    return [];
  }
  if (qrSupported()) {
    try {
      const Ctor = (globalThis as unknown as { BarcodeDetector: new (o: { formats: string[] }) => Detector }).BarcodeDetector;
      const codes = await new Ctor({ formats: ['qr_code'] }).detect(bitmap);
      const found = Array.from(new Set(codes.map((c) => c.rawValue).filter(Boolean)));
      if (found.length) return found;
    } catch {
      /* fall through to jsQR */
    }
  }
  try {
    const { default: jsQR } = await import('jsqr');
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    const w = Math.max(1, Math.round(bitmap.width * scale));
    const h = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) return [];
    ctx.drawImage(bitmap, 0, 0, w, h);
    const data = ctx.getImageData(0, 0, w, h);
    const hit = jsQR(data.data, w, h, { inversionAttempts: 'attemptBoth' });
    return hit?.data ? [hit.data] : [];
  } catch {
    return [];
  }
}

export function inspectQr(raw: string): QrFinding {
  const text = raw.trim();
  if (/^upi:\/\//i.test(text)) return inspectUpi(text);
  if (/^https?:\/\//i.test(text)) {
    let host = '';
    try {
      host = new URL(text).hostname;
    } catch {
      /* ignore */
    }
    return {
      raw: text,
      kind: 'url',
      headline: `Opens a website: ${host || text}`,
      details: [{ label: 'Address', value: text }],
      warnings: [],
    };
  }
  return { raw: text, kind: 'text', headline: 'Contains text', details: [{ label: 'Text', value: text }], warnings: [] };
}

function inspectUpi(text: string): QrFinding {
  const q = new URLSearchParams(text.replace(/^upi:\/\/[^?]*\??/i, ''));
  const verb = (text.match(/^upi:\/\/([^?/]+)/i)?.[1] ?? 'pay').toLowerCase();
  const pa = q.get('pa') ?? '';
  const pn = q.get('pn') ?? '';
  const am = q.get('am');
  const tn = q.get('tn') ?? '';
  const amount = am ? `₹${Number(am).toLocaleString('en-IN', { maximumFractionDigits: 2 })}` : 'an amount you type';
  const warnings = [
    'Scanning a QR code and entering your UPI PIN always SENDS money. You never need to scan or enter a PIN to receive money.',
  ];
  if (am && Number(am) > 0) warnings.push(`The amount (${amount}) is already filled in.`);
  if (/refund|cashback|prize|reward|lottery|receive|kyc|verify/i.test(`${tn} ${pn}`)) {
    warnings.push(`The note says "${tn || pn}", which is how "scan to receive" scams disguise a payment.`);
  }
  if (pa && !/^[\w.-]+@[\w.-]+$/.test(pa)) warnings.push('The UPI ID looks malformed.');
  return {
    raw: text,
    kind: 'upi',
    headline: `Scanning this will SEND ${amount}${pn ? ` to ${pn}` : ''}${pa ? ` (${pa})` : ''}.`,
    details: [
      { label: 'Action', value: verb === 'pay' ? 'Payment from you' : verb },
      { label: 'UPI ID', value: pa || 'not given' },
      { label: 'Name shown', value: pn || 'not given' },
      { label: 'Amount', value: am ? amount : 'not set' },
      ...(tn ? [{ label: 'Note', value: tn }] : []),
    ],
    warnings,
  };
}
