import { useState } from 'react';
import type { AnalysisRun } from '../ai/types';
import { buildReport, recoverySteps, REGIONS } from '../lib/report';
import { Icon } from './Icon';
import { copyText } from './ui';

/** Turns a check into action: where to report it, with a ready-made summary. */
export function ReportCard({ run }: { run: AnalysisRun }) {
  const r = REGIONS[0]; // India official cyber helplines
  const [urgent, setUrgent] = useState(false);
  return (
    <section className="card card-pad report">
      <div className="card-head">
        <h3 className="card-title">
          <span className="ic peach"><Icon name="alert" /></span>Report it, protect others
        </h3>
      </div>
      <div className="report-grid">
        {r.places.map((p) =>
          p.href ? (
            <a key={p.label} className="report-link" href={p.href} target="_blank" rel="noreferrer">
              <b>{p.label}</b>
              <span className="xs subtle">{p.detail}</span>
              <Icon name="arrowRight" />
            </a>
          ) : (
            <div key={p.label} className="report-link static">
              <b>{p.label}</b>
              <span className="xs subtle">{p.detail}</span>
            </div>
          ),
        )}
      </div>
      <div className="row wrap" style={{ marginTop: 12 }}>
        <button className="btn sm soft" onClick={() => copyText(buildReport(run), 'Report copied, paste it into the form')}>
          <Icon name="copy" /> Copy a ready-made report
        </button>
        <button className={`btn sm ${urgent ? 'danger' : 'ghost'}`} onClick={() => setUrgent((u) => !u)} aria-expanded={urgent}>
          <Icon name="bolt" /> I already paid or shared a code
        </button>
      </div>
      {urgent && (
        <ol className="recovery" aria-label="What to do now">
          {recoverySteps('IN').map((s, i) => (
            <li key={s.title} style={{ animationDelay: `${i * 50}ms` }}>
              <b>{s.title}</b>
              <span className="small muted">{s.detail}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
