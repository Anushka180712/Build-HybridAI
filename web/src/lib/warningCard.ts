/**
 * Renders a shareable "scam alert" picture for WhatsApp and family groups.
 * Drawn entirely on a canvas in the browser; the original message is redacted
 * first so the picture never carries anyone's phone number, code or name.
 */
import { redact } from '../ai/redact';
import type { AnalysisRun } from '../ai/types';
import { CATEGORY_LABEL, VERDICT_LABEL, excerpt } from './format';

const W = 1080;
const H = 1350;

const THEME = {
  scam: { bg: '#fff4f2', blob1: '#ffe2dc', blob2: '#d8eeeb', ink: '#9c3524', pill: '#c94b36' },
  suspicious: { bg: '#fffaeb', blob1: '#fff0c7', blob2: '#ffe8d4', ink: '#845b00', pill: '#b07b0c' },
  safe: { bg: '#f0faf4', blob1: '#dcf3e6', blob2: '#ddebfb', ink: '#226b47', pill: '#2f8a5c' },
} as const;

const SANS = "'Instrument Sans', system-ui, -apple-system, 'Segoe UI', sans-serif";
const SERIF = "'Fraunces', Georgia, serif";

function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
  // Split very long tokens (links, IDs) so they can wrap too.
  const words = text
    .split(/\s+/)
    .filter(Boolean)
    .flatMap((w) => {
      if (ctx.measureText(w).width <= maxWidth) return [w];
      const parts: string[] = [];
      let cur = '';
      for (const ch of w) {
        if (ctx.measureText(cur + ch).width > maxWidth) {
          parts.push(cur);
          cur = ch;
        } else cur += ch;
      }
      if (cur) parts.push(cur);
      return parts;
    });
  const lines: string[] = [];
  let line = '';
  for (const w of words) {
    const test = line ? `${line} ${w}` : w;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = w;
      if (lines.length === maxLines) break;
    } else line = test;
  }
  if (lines.length < maxLines && line) lines.push(line);
  if (lines.length === maxLines && words.join(' ').length > lines.join(' ').length) {
    lines[maxLines - 1] = `${lines[maxLines - 1].replace(/\s+\S*$/, '')}…`;
  }
  return lines;
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

export async function renderWarningCard(run: AnalysisRun, privateTerms: string[] = []): Promise<Blob> {
  if (document.fonts?.ready) await document.fonts.ready;
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas is not available');
  const a = run.analysis;
  const t = THEME[a.verdict];

  // Background with soft blobs.
  ctx.fillStyle = t.bg;
  ctx.fillRect(0, 0, W, H);
  const blob = (x: number, y: number, r: number, c: string) => {
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, c);
    g.addColorStop(1, `${c}00`);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  };
  blob(920, 140, 420, t.blob1);
  blob(120, 1220, 480, t.blob2);

  // Brand.
  ctx.fillStyle = '#24212c';
  ctx.font = `600 44px ${SERIF}`;
  ctx.fillText('Chaukas', 88, 128);
  const brandWidth = ctx.measureText('Chaukas').width;
  ctx.font = `500 26px ${SANS}`;
  ctx.fillStyle = '#565266';
  ctx.fillText('Scam check', 88 + brandWidth + 22, 126);

  // Verdict pill.
  const pillText = a.verdict === 'safe' ? 'CHECKED' : a.verdict === 'suspicious' ? 'BE CAREFUL' : 'SCAM ALERT';
  ctx.font = `700 26px ${SANS}`;
  const pw = ctx.measureText(pillText).width + 56;
  roundRect(ctx, W - 88 - pw, 88, pw, 56, 28);
  ctx.fillStyle = t.pill;
  ctx.fill();
  ctx.fillStyle = '#fff';
  ctx.fillText(pillText, W - 88 - pw + 28, 126);

  // Headline.
  ctx.fillStyle = t.ink;
  ctx.font = `600 104px ${SERIF}`;
  ctx.fillText(VERDICT_LABEL[a.verdict], 88, 300);
  ctx.font = `600 32px ${SANS}`;
  ctx.fillStyle = '#565266';
  ctx.fillText(`${CATEGORY_LABEL[a.category] ?? a.category} · risk ${a.riskScore}/100`, 92, 360);

  // Summary.
  ctx.fillStyle = '#24212c';
  ctx.font = `500 42px ${SANS}`;
  let y = 450;
  for (const l of wrap(ctx, a.summary, W - 176, 3)) {
    ctx.fillText(l, 88, y);
    y += 58;
  }

  // Message card (redacted).
  const qrMatch = run.input.match(/\[QR code in the screenshot: [\s\S]*?\((Scanning this will[\s\S]*?|Opens a website[\s\S]*?)\)(?:;|\]\s*$)/);
  const body = run.input.replace(/\s*\[QR code in the screenshot:[\s\S]*\]\s*$/, '').trim();
  const quote = excerpt(redact(body || (qrMatch ? `Screenshot with a QR code. ${qrMatch[1]}` : '(screenshot)'), privateTerms).text, 170);
  ctx.font = `400 32px ${SANS}`;
  const qLines = wrap(ctx, `“${quote}”`, W - 256, 4);
  const cardY = y + 24;
  const cardH = 92 + qLines.length * 46;
  ctx.save();
  ctx.shadowColor = 'rgba(36,33,44,0.10)';
  ctx.shadowBlur = 40;
  ctx.shadowOffsetY = 12;
  roundRect(ctx, 88, cardY, W - 176, cardH, 36);
  ctx.fillStyle = '#ffffff';
  ctx.fill();
  ctx.restore();
  ctx.font = `600 24px ${SANS}`;
  ctx.fillStyle = '#8b8799';
  ctx.fillText('THE MESSAGE (personal details hidden)', 128, cardY + 58);
  ctx.font = `400 32px ${SANS}`;
  ctx.fillStyle = '#565266';
  qLines.forEach((l, i) => ctx.fillText(l, 128, cardY + 108 + i * 46));
  y = cardY + cardH + 76;

  // Red flags or next steps.
  const items = (a.verdict === 'safe' ? a.reassurances : a.redFlags.map((f) => f.label)).slice(0, 3);
  if (items.length) {
    ctx.font = `700 26px ${SANS}`;
    ctx.fillStyle = t.ink;
    ctx.fillText(a.verdict === 'safe' ? 'WHY IT LOOKS FINE' : 'HOW TO SPOT IT', 88, y);
    y += 56;
    ctx.font = `500 36px ${SANS}`;
    for (const it of items) {
      ctx.fillStyle = t.pill;
      ctx.beginPath();
      ctx.arc(104, y - 12, 9, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#24212c';
      ctx.fillText(wrap(ctx, it, W - 236, 1)[0] ?? '', 136, y);
      y += 58;
    }
  }

  // Footer tip.
  const tip = a.verdict === 'safe' ? 'When in doubt, call the sender on a number you already trust.' : 'Do not tap links, share codes or enter your UPI PIN. Forward this to warn others.';
  roundRect(ctx, 88, H - 210, W - 176, 122, 32);
  ctx.fillStyle = 'rgba(255,255,255,0.7)';
  ctx.fill();
  ctx.font = `500 30px ${SANS}`;
  ctx.fillStyle = '#24212c';
  wrap(ctx, tip, W - 256, 2).forEach((l, i) => ctx.fillText(l, 128, H - 160 + i * 40));
  ctx.font = `500 22px ${SANS}`;
  ctx.fillStyle = '#8b8799';
  ctx.fillText(`Checked with Chaukas · ${run.source === 'on_device' ? 'on-device AI' : run.source === 'in_cloud' ? 'hybrid AI' : 'preview'} · ${new Date(run.createdAt).toLocaleDateString()}`, 88, H - 40);

  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not draw the card'))), 'image/png'));
}

/** Shares via the system sheet when files are supported, otherwise downloads. */
export async function shareWarningCard(blob: Blob, title: string): Promise<'shared' | 'downloaded' | 'cancelled'> {
  const file = new File([blob], 'chaukas-scam-alert.png', { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title, text: `${title}. Checked with Chaukas.` });
      return 'shared';
    } catch (e) {
      if ((e as Error).name === 'AbortError') return 'cancelled';
    }
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
  return 'downloaded';
}
