/**
 * Renders the message with each red flag's quoted evidence highlighted inline.
 */
import { Fragment } from 'react';

interface Mark {
  start: number;
  end: number;
  kind: 'flag' | 'signal';
  title: string;
}

function findAll(haystack: string, needle: string): number[] {
  const out: number[] = [];
  if (needle.length < 3) return out;
  const h = haystack.toLowerCase();
  const n = needle.toLowerCase();
  let i = h.indexOf(n);
  while (i >= 0 && out.length < 4) {
    out.push(i);
    i = h.indexOf(n, i + n.length);
  }
  return out;
}

/**
 * Renders the original message with evidence from the model (and local
 * signals) highlighted, so people learn to spot the pattern themselves.
 */
export function Highlighted({ text, flags, signals }: { text: string; flags: { evidence: string; label: string }[]; signals: { label: string; matches: string[] }[] }) {
  const marks: Mark[] = [];
  for (const f of flags) {
    const ev = f.evidence.replace(/^["'“”]+|["'“”.…]+$/g, '').trim();
    for (const i of findAll(text, ev)) marks.push({ start: i, end: i + ev.length, kind: 'flag', title: f.label });
  }
  for (const s of signals) for (const m of s.matches) for (const i of findAll(text, m)) marks.push({ start: i, end: i + m.length, kind: 'signal', title: s.label });

  marks.sort((a, b) => a.start - b.start || (a.kind === 'flag' ? -1 : 1));
  const merged: Mark[] = [];
  for (const m of marks) {
    const last = merged[merged.length - 1];
    if (last && m.start < last.end) {
      if (m.end > last.end && m.kind === last.kind) last.end = m.end;
      continue;
    }
    merged.push({ ...m });
  }

  const parts: JSX.Element[] = [];
  let cur = 0;
  merged.forEach((m, idx) => {
    if (m.start > cur) parts.push(<Fragment key={`t${idx}`}>{text.slice(cur, m.start)}</Fragment>);
    parts.push(
      <mark key={`m${idx}`} className={`hl hl-${m.kind}`} title={m.title} data-label={m.title}>
        {text.slice(m.start, m.end)}
      </mark>,
    );
    cur = m.end;
  });
  if (cur < text.length) parts.push(<Fragment key="end">{text.slice(cur)}</Fragment>);
  return <div className="message-quote">{parts}</div>;
}
