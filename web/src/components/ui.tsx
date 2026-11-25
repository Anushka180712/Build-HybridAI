import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useT } from '../lib/i18n';
import { createStore } from '../lib/store';
import { Icon, type IconName } from './Icon';
import { sourceLabel } from '../lib/format';

/* ---------------- Toasts ---------------- */
const toastStore = createStore<{ items: { id: number; text: string; icon: IconName }[] }>('chaukas.toasts.ephemeral', { items: [] });
toastStore.set({ items: [] });
let toastId = 0;
export function toast(text: string, icon: IconName = 'check') {
  const id = ++toastId;
  toastStore.set((s) => ({ items: [...s.items, { id, text, icon }] }));
  setTimeout(() => toastStore.set((s) => ({ items: s.items.filter((t) => t.id !== id) })), 2600);
}
export function Toasts() {
  const { items } = toastStore.use();
  return (
    <div className="toasts" role="status" aria-live="polite">
      {items.map((t) => (
        <div key={t.id} className="toast">
          <Icon name={t.icon} />
          {t.text}
        </div>
      ))}
    </div>
  );
}

/* ---------------- Segmented control ---------------- */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: { value: T; label: string; icon?: IconName }[];
  onChange: (v: T) => void;
  label: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [thumb, setThumb] = useState({ x: 0, w: 0 });
  useLayoutEffect(() => {
    const el = ref.current?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]');
    if (el) setThumb({ x: el.offsetLeft, w: el.offsetWidth });
  }, [value]);
  useEffect(() => {
    const onResize = () => {
      const el = ref.current?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]');
      if (el) setThumb({ x: el.offsetLeft, w: el.offsetWidth });
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return (
    <div className="segmented" ref={ref} role="group" aria-label={label}>
      <span className="thumb" style={{ width: thumb.w, transform: `translateX(${thumb.x - 4}px)`, left: 4 }} />
      {options.map((o) => (
        <button key={o.value} type="button" aria-pressed={o.value === value} onClick={() => onChange(o.value)}>
          {o.icon && <Icon name={o.icon} />}
          {o.label}
        </button>
      ))}
    </div>
  );
}

/* ---------------- Switch row ---------------- */
export function SwitchRow({ title, hint, checked, onChange }: { title: string; hint?: ReactNode; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="switch-row">
      <div className="grow">
        <div className="switch-title">{title}</div>
        {hint && <div className="switch-hint">{hint}</div>}
      </div>
      <span className="switch">
        <input type="checkbox" role="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} />
        <span />
      </span>
    </label>
  );
}

/* ---------------- Source badge ---------------- */
export function SourceBadge({ source, latencyMs }: { source: string; latencyMs?: number }) {
  const t = useT();
  const cls = source === 'on_device' ? 'device' : source === 'in_cloud' ? 'cloud' : 'preview';
  const icon: IconName = source === 'on_device' ? 'laptop' : source === 'in_cloud' ? 'cloud' : 'eye';
  return (
    <span className={`badge ${cls}`} title={source === 'on_device' ? 'Answered by Gemini Nano, running locally in your browser' : source === 'in_cloud' ? 'Answered by Gemini via Firebase AI Logic' : 'Preview mode: local pattern checks only'}>
      <Icon name={icon} />
      {t(sourceLabel(source))}
      {latencyMs !== undefined && <span className="mono" style={{ opacity: 0.75 }}>· {latencyMs < 1000 ? `${latencyMs} ms` : `${(latencyMs / 1000).toFixed(1)} s`}</span>}
    </span>
  );
}

/* ---------------- Score dial ---------------- */
export function ScoreDial({ score, verdict, size = 132 }: { score: number; verdict: 'safe' | 'suspicious' | 'scam'; size?: number }) {
  const t = useT();
  const [shown, setShown] = useState(0);
  useEffect(() => {
    let raf = 0;
    const start = performance.now();
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / 900);
      const eased = 1 - Math.pow(1 - p, 3);
      setShown(Math.round(score * eased));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [score]);
  const r = 52;
  const c = 2 * Math.PI * r;
  const arc = 0.75; // 270 degree gauge
  const color = verdict === 'scam' ? 'var(--rose-600)' : verdict === 'suspicious' ? 'var(--butter-600)' : 'var(--mint-600)';
  const track = verdict === 'scam' ? 'var(--rose-100)' : verdict === 'suspicious' ? 'var(--butter-100)' : 'var(--mint-100)';
  return (
    <div className="dial" style={{ width: size, height: size }}>
      <svg viewBox="0 0 120 120" width={size} height={size}>
        <g transform="rotate(135 60 60)">
          <circle cx="60" cy="60" r={r} fill="none" stroke={track} strokeWidth="10" strokeLinecap="round" strokeDasharray={`${c * arc} ${c}`} />
          <circle
            cx="60"
            cy="60"
            r={r}
            fill="none"
            stroke={color}
            strokeWidth="10"
            strokeLinecap="round"
            strokeDasharray={`${(c * arc * shown) / 100} ${c}`}
          />
        </g>
      </svg>
      <div className="dial-label">
        <span className="dial-num display">{shown}</span>
        <span className="dial-cap">{t('RISK')}</span>
      </div>
    </div>
  );
}

/* ---------------- Modal ---------------- */
export function Modal({ onClose, children, label }: { onClose: () => void; children: ReactNode; label: string }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={label}>
        {children}
      </div>
    </div>
  );
}

export function copyText(text: string, what = 'Copied') {
  navigator.clipboard?.writeText(text).then(
    () => toast(what),
    () => toast('Could not copy', 'x'),
  );
}

export function Avatar({ name, size = 30 }: { name: string; size?: number }) {
  const hues = ['#d8eeeb', '#dcf3e6', '#fff0c7', '#ffe2dc', '#ddebfb', '#ffe8d4'];
  const ink = ['#145651', '#226b47', '#845b00', '#9c3524', '#2b5f99', '#8a4a17'];
  const i = Array.from(name).reduce((n, ch) => n + ch.charCodeAt(0), 0) % hues.length;
  return (
    <span className="avatar" style={{ width: size, height: size, background: hues[i], color: ink[i], fontSize: size * 0.42 }} title={name}>
      {name.trim().charAt(0).toUpperCase() || '?'}
    </span>
  );
}
