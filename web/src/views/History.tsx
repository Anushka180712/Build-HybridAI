/**
 * Past checks with filters, CSV export and private insights.
 */
import { useEffect, useMemo, useState } from 'react';
import { useT } from '../lib/i18n';
import { clearHistory, deleteRun, historyStore, listenSyncedChecks, type SyncedCheck, countsAsScam, countsStore } from '../data/history';
import { useUser } from '../data/auth';
import { navigate } from '../lib/appState';
import { CATEGORY_LABEL, VERDICT_LABEL, excerpt, timeAgo } from '../lib/format';
import type { AnalysisRun, Verdict } from '../ai/types';
import { Icon } from '../components/Icon';
import { SourceBadge } from '../components/ui';
import { Result } from '../components/Result';
import { downloadFile } from '../lib/report';

type Filter = 'all' | Verdict;

export function HistoryView({ param }: { param?: string }) {
  const t = useT();
  const { runs } = historyStore.use();
  const { user } = useUser();
  const [filter, setFilter] = useState<Filter>('all');
  const [synced, setSynced] = useState<SyncedCheck[]>([]);

  useEffect(() => {
    if (!user) return;
    return listenSyncedChecks(user.uid, setSynced);
  }, [user]);

  const counts = countsStore.use();
  const open = param ? runs.find((r) => r.id === param) : undefined;
  const stats = useMemo(() => {
    // Totals never go down when checks are deleted (see countsStore).
    const total = Math.max(counts.checked, runs.length);
    const scams = Math.max(counts.scams, runs.filter(countsAsScam).length);
    const local = runs.filter((r) => r.source === 'on_device').length;
    const hidden = runs.reduce((n, r) => n + r.redactions, 0);
    return { total, scams, localPct: total ? Math.round((local / total) * 100) : 0, hidden };
  }, [runs, counts]);

  const otherDevices = synced.filter((s) => !runs.some((r) => r.id === s.id));
  const list = runs.filter((r) => filter === 'all' || r.analysis.verdict === filter);

  if (open) {
    return (
      <div className="page">
        <button className="btn ghost sm back" onClick={() => navigate('history')}>
          <Icon name="arrowRight" style={{ transform: 'rotate(180deg)' }} /> {t('All checks')}
        </button>
        <Result run={open} />
      </div>
    );
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <span className="eyebrow">{t('Your checks')}</span>
          <h1 className="display page-title">{t('History')}</h1>
        </div>
        {runs.length > 0 && (
          <div className="row">
          <button className="btn ghost sm" onClick={() => exportCsv(runs)}>
            <Icon name="download" /> {t('Export')}
          </button>
          <button
            className="btn ghost sm danger"
            onClick={() => {
              if (confirmClear()) clearHistory();
            }}
          >
            <Icon name="trash" /> {t('Clear all')}
          </button>
          </div>
        )}
      </header>

      <section className="stats">
        <Stat label={t('Messages checked')} value={stats.total} tint="lilac" />
        <Stat label={t('Scams caught')} value={stats.scams} tint="rose" />
        <Stat label={t('Checked on device')} value={`${stats.localPct}%`} tint="mint" />
        <Stat label={t('Details kept private')} value={stats.hidden} tint="butter" />
      </section>

      {runs.length >= 2 && <Insights runs={runs} />}

      <div className="row wrap" style={{ margin: '20px 0 12px' }}>
        {(['all', 'scam', 'suspicious', 'safe'] as Filter[]).map((f) => (
          <button key={f} className={`chip ${filter === f ? 'on' : ''}`} onClick={() => setFilter(f)}>
            {f !== 'all' && <span className={`dot ${f}`} />}
            {f === 'all' ? t('All') : t(VERDICT_LABEL[f])}
          </button>
        ))}
      </div>

      {list.length === 0 ? (
        <div className="card empty">
          <EmptyArt />
          <h3>{t('Nothing here yet')}</h3>
          <p className="small">{t('Messages you check will appear here. Stored on this phone only.')}</p>
          <button className="btn primary" style={{ marginTop: 16 }} onClick={() => navigate('check')}>
            {t('Check a message')}
          </button>
        </div>
      ) : (
        <ul className="history-list">
          {list.map((r, i) => (
            <li key={r.id} style={{ animationDelay: `${Math.min(i, 10) * 35}ms` }}>
              <button className="history-item" onClick={() => navigate('history', r.id)}>
                <span className={`h-score v-${r.analysis.verdict}`}>{r.analysis.riskScore}</span>
                <span className="grow">
                  <span className="row" style={{ gap: 8 }}>
                    <b>{t(VERDICT_LABEL[r.analysis.verdict])}</b>
                    <span className="subtle xs">· {t(CATEGORY_LABEL[r.analysis.category] ?? r.analysis.category)}</span>
                  </span>
                  <span className="h-excerpt">{r.hasImage && !r.input ? t('Screenshot') : excerpt(r.input, 110)}</span>
                </span>
                <span className="h-meta">
                  <SourceBadge source={r.source} />
                  <span className="xs subtle">{timeAgo(r.createdAt)}</span>
                </span>
              </button>
              <button className="btn icon sm ghost h-del" aria-label="Delete" onClick={() => deleteRun(r.id)}>
                <Icon name="trash" />
              </button>
            </li>
          ))}
        </ul>
      )}

      {otherDevices.length > 0 && (
        <>
          <h2 className="section-title">
            <Icon name="device" /> {t('From your other devices')}
          </h2>
          <ul className="history-list">
            {otherDevices.map((s) => (
              <li key={s.id}>
                <div className="history-item static">
                  <span className={`h-score v-${s.verdict}`}>{s.riskScore}</span>
                  <span className="grow">
                    <b>{VERDICT_LABEL[s.verdict as Verdict] ?? s.verdict}</b>
                    <span className="h-excerpt">{s.excerpt}</span>
                  </span>
                  <span className="h-meta">
                    <SourceBadge source={s.source} />
                    <span className="xs subtle">{timeAgo(s.createdAt)}</span>
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function confirmClear() {
  // A native confirm keeps this destructive action deliberate.
  return window.confirm('Delete all checks stored on this device?');
}

function Stat({ label, value, tint }: { label: string; value: number | string; tint: string }) {
  return (
    <div className={`stat t-${tint}`}>
      <span className="stat-value display">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

export function EmptyArt() {
  return (
    <svg className="art" viewBox="0 0 120 90" aria-hidden="true">
      <ellipse cx="60" cy="80" rx="42" ry="5" fill="#ede8e1" />
      <rect x="22" y="14" width="62" height="44" rx="12" fill="#d8eeeb" />
      <rect x="36" y="30" width="62" height="40" rx="12" fill="#fff" stroke="#24212c" strokeWidth="1.6" />
      <path d="M48 44h34 M48 52h22" stroke="#8fcac2" strokeWidth="3" strokeLinecap="round" />
      <circle cx="96" cy="22" r="7" fill="#dcf3e6" />
      <path d="M93 22l2 2 4-4" stroke="#2f8a5c" strokeWidth="1.6" fill="none" strokeLinecap="round" />
    </svg>
  );
}

function exportCsv(runs: { createdAt: number; analysis: { verdict: string; riskScore: number; category: string; summary: string }; source: string; input: string }[]) {
  const esc = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
  const rows = [
    ['date', 'verdict', 'risk', 'category', 'source', 'summary', 'message'],
    ...runs.map((r) => [new Date(r.createdAt).toISOString(), r.analysis.verdict, r.analysis.riskScore, r.analysis.category, r.source, r.analysis.summary, r.input]),
  ];
  downloadFile(`chaukas-history-${new Date().toISOString().slice(0, 10)}.csv`, rows.map((r) => r.map(esc).join(',')).join('\n'), 'text/csv');
}

/** Small, private analytics computed from the checks on this device. */
function Insights({ runs }: { runs: AnalysisRun[] }) {
  const t = useT();
  const data = useMemo(() => {
    const cats = new Map<string, number>();
    const flags = new Map<string, number>();
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date();
      d.setHours(0, 0, 0, 0);
      d.setDate(d.getDate() - (6 - i));
      return { start: d.getTime(), label: d.toLocaleDateString(undefined, { weekday: 'narrow' }), safe: 0, suspicious: 0, scam: 0 };
    });
    for (const r of runs) {
      const a = r.analysis;
      if (a.verdict !== 'safe') cats.set(a.category, (cats.get(a.category) ?? 0) + 1);
      for (const f of a.redFlags) flags.set(f.label, (flags.get(f.label) ?? 0) + 1);
      const day = [...days].reverse().find((d) => r.createdAt >= d.start);
      if (day && r.createdAt < day.start + 86_400_000) day[a.verdict]++;
    }
    const top = [...cats.entries()].sort((x, y) => y[1] - x[1]).slice(0, 5);
    const topFlags = [...flags.entries()].sort((x, y) => y[1] - x[1]).slice(0, 4);
    const max = Math.max(1, ...days.map((d) => d.safe + d.suspicious + d.scam));
    const catMax = Math.max(1, ...top.map((t) => t[1]));
    const onDevice = runs.filter((r) => r.source === 'on_device');
    const avgMs = onDevice.length ? Math.round(onDevice.reduce((n, r) => n + r.latencyMs, 0) / onDevice.length) : null;
    return { top, topFlags, days, max, catMax, avgMs };
  }, [runs]);

  return (
    <section className="insights">
      <div className="card card-pad ins-card">
        <h3 className="card-title"><span className="ic"><Icon name="chart" /></span>{t('Last 7 days')}</h3>
        <div className="ins-week" role="img" aria-label="Checks per day for the last week, by verdict">
          {data.days.map((d) => {
            const total = d.safe + d.suspicious + d.scam;
            return (
              <div key={d.start} className="ins-day">
                <div className="ins-stack" style={{ height: `${(total / data.max) * 100}%` }}>
                  {(['scam', 'suspicious', 'safe'] as Verdict[]).map((v) => d[v] > 0 && <i key={v} className={`seg ${v}`} style={{ flex: d[v] }} title={`${d[v]} ${t(VERDICT_LABEL[v]).toLowerCase()}`} />)}
                </div>
                <span className="xs subtle">{d.label}</span>
              </div>
            );
          })}
        </div>
        <div className="ins-legend xs subtle">
          {(['scam', 'suspicious', 'safe'] as Verdict[]).map((v) => <span key={v}><span className={`dot ${v}`} /> {t(VERDICT_LABEL[v])}</span>)}
        </div>
      </div>
      <div className="card card-pad ins-card">
        <h3 className="card-title"><span className="ic rose"><Icon name="target" /></span>{t('What is targeting you')}</h3>
        {data.top.length === 0 ? (
          <p className="small muted">{t('No scams yet. Keep it that way.')}</p>
        ) : (
          <ul className="ins-bars">
            {data.top.map(([cat, n]) => (
              <li key={cat}>
                <span className="small">{t(CATEGORY_LABEL[cat] ?? cat)}</span>
                <span className="ins-bar"><i style={{ width: `${(n / data.catMax) * 100}%` }} /></span>
                <b className="small">{n}</b>
              </li>
            ))}
          </ul>
        )}
        {data.topFlags.length > 0 && (
          <>
            <span className="eyebrow" style={{ display: 'block', margin: '14px 0 8px' }}>{t('Tricks you have seen most')}</span>
            <div className="row wrap">
              {data.topFlags.map(([f, n]) => <span key={f} className="chip">{f} <b>×{n}</b></span>)}
            </div>
          </>
        )}
        {data.avgMs !== null && <p className="xs subtle" style={{ marginTop: 12 }}>{t(`On-device checks took ${(data.avgMs / 1000).toFixed(1)} s on average, with nothing sent anywhere.`)}</p>}
      </div>
    </section>
  );
}
