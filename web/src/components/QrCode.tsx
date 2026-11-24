/**
 * A crisp QR code drawn as one SVG path, so it scales and prints sharply.
 */
import { useMemo } from 'react';
import qrcode from 'qrcode-generator';

export function QrCode({ text, size = 148, label }: { text: string; size?: number; label: string }) {
  const { path, n } = useMemo(() => {
    const qr = qrcode(0, 'M');
    qr.addData(text);
    qr.make();
    const count = qr.getModuleCount();
    let d = '';
    for (let r = 0; r < count; r++) {
      for (let c = 0; c < count; c++) if (qr.isDark(r, c)) d += `M${c + 2} ${r + 2}h1v1h-1z`;
    }
    return { path: d, n: count + 4 };
  }, [text]);
  return (
    <svg className="qr-code" width={size} height={size} viewBox={`0 0 ${n} ${n}`} role="img" aria-label={label} shapeRendering="crispEdges">
      <rect width={n} height={n} fill="#fff" rx="1.5" />
      <path d={path} fill="#26222f" />
    </svg>
  );
}
