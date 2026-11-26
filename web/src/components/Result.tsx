/**
 * The verdict screen: score dial, evidence, next steps, safe reply, second opinion, follow-up
 * questions and sharing.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import type { AnalysisRun } from '../ai/types';
import { askFollowUp, secondOpinion, ChaukasError } from '../ai/engine';
import { computeSignals } from '../ai/signals';
import { engineSettings, languageCode, settingsStore } from '../data/settings';
import { SPEECH_LOCALES } from '../lib/voice';
import { VERDICT_WORDS, spokenVerdict, uiLang } from '../lib/verdictWords';
import { renderWarningCard, shareWarningCard } from '../lib/warningCard';
import { saveRun } from '../data/history';
import { rcStore } from '../lib/appState';
import { CATEGORY_LABEL, VERDICT_LABEL, excerpt } from '../lib/format';
import { Highlighted } from './Highlighted';
import { Icon } from './Icon';
import { ScoreDial, SourceBadge, copyText, Modal, toast } from './ui';
import { ShareToCircle } from './ShareToCircle';
import { isShareable } from '../data/circle';
import { resultWhatsAppText, whatsappLink } from '../lib/invite';
import { redact } from '../ai/redact';
import { useT } from '../lib/i18n';
import { ReportCard } from './ReportCard';

const VERDICT_ART = {
  safe: { icon: 'shieldCheck', cls: 'v-safe' },
  suspicious: { icon: 'alert', cls: 'v-suspicious' },
  scam: { icon: 'shield', cls: 'v-scam' },
} as const;

function speak(run: AnalysisRun) {
  if (!('speechSynthesis' in window)) return toast('Read aloud is not supported here', 'x');
  speechSynthesis.cancel();
  const a = run.analysis;
  const lang = uiLang(settingsStore.get().language);
  // In an Indian language, speak the verdict, the reasons and three clear "don'ts" in that language.
  const text =
    lang === 'en'
      ? `${VERDICT_LABEL[a.verdict]}. ${a.summary}. ${a.nextSteps.length ? `What to do: ${a.nextSteps.join('. ')}.` : ''}`
      : spokenVerdict(a.verdict, a.summary, [], lang);
  const u = new SpeechSynthesisUtterance(text);
  u.lang = SPEECH_LOCALES[languageCode(settingsStore.get().language)] ?? 'en-IN';
  u.rate = 0.95;
  speechSynthesis.speak(u);
}

export function Result({
  run,
  onChange,
  savedToHistory = true,
  onSaveToHistory,
  isKnownContact = false,
  onDismiss,
}: {
  run: AnalysisRun;
  onChange?: (r: AnalysisRun) => void;
  savedToHistory?: boolean;
  onSaveToHistory?: (r?: AnalysisRun) => void;
  isKnownContact?: boolean;
  onDismiss?: () => void;
}) {
  const a = run.analysis;
  const rc = rcStore.use();
  const settings = settingsStore.use();
  const t = useT();
  const signals = useMemo(() => computeSignals(run.input).signals, [run.input]);
  const [showSent, setShowSent] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [cardOpen, setCardOpen] = useState(false);
  const [opinionBusy, setOpinionBusy] = useState(false);
  const [opinionErr, setOpinionErr] = useState<string | null>(null);
  const [expandedFlag, setExpandedFlag] = useState<typeof a.redFlags[0] | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    ref.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [run.id]);

  const borderline = a.riskScore >= rc.secondOpinionMin && a.riskScore <= rc.secondOpinionMax;
  const canEscalate = run.source !== 'in_cloud' && !run.secondOpinion && !run.hasImage && run.input.trim().length > 0;

  const escalate = async () => {
    setOpinionBusy(true);
    setOpinionErr(null);
    try {
      const so = await secondOpinion(run, engineSettings());
      const next = { ...run, secondOpinion: so };
      saveRun(next);
      onChange?.(next);
    } catch (e) {
      setOpinionErr(e instanceof ChaukasError ? `${e.message} ${e.hint ?? ''}` : String(e));
    } finally {
      setOpinionBusy(false);
    }
  };

  const art = VERDICT_ART[a.verdict];

  return (
    <div className="result" ref={ref}>
      <section className={`card verdict ${art.cls}`}>
        <div className="verdict-main">
          <ScoreDial score={a.riskScore} verdict={a.verdict} />
          <div className="verdict-copy">
            <div className="row wrap" style={{ gap: 6 }}>
              <span className="verdict-pill">
                <Icon name={art.icon} />
                {CATEGORY_LABEL[a.category]}
              </span>
            </div>
            <h2 className="display verdict-title">{t(VERDICT_LABEL[a.verdict])}</h2>
            {uiLang(settings.language) !== 'en' && <p className="verdict-local" lang={uiLang(settings.language)}>{VERDICT_WORDS[uiLang(settings.language)].verdict[a.verdict]}</p>}
            <p className="verdict-summary">{a.summary}</p>
            <div className="row wrap verdict-meta">
              <SourceBadge source={run.source} latencyMs={run.latencyMs} />
              {run.source === 'on_device' && (
                <span className="badge neutral">
                  <Icon name="lock" />
                  {t('Never left this device')}
                </span>
              )}
              {run.sentToCloud !== undefined && (
                <button className="badge lilac as-btn" onClick={() => setShowSent(true)}>
                  <Icon name="eye" />
                  {run.redactions > 0 ? `${run.redactions} detail${run.redactions === 1 ? '' : 's'} hidden from cloud` : t('See what was sent')}
                </button>
              )}
            </div>
          </div>
        </div>
        {isKnownContact && a.verdict !== 'safe' && (
          <div className="card" style={{ background: '#FFF8F0', border: '1px solid #FFB74D', padding: 14, margin: '12px 0', borderRadius: 12 }}>
            <div className="row" style={{ alignItems: 'center', gap: 8, color: '#E65100', fontWeight: 'bold' }}>
              <Icon name="alert" style={{ width: 18, height: 18 }} />
              <span>{t('Message from saved contact')}</span>
            </div>
            <p style={{ fontSize: 13, color: '#4A5568', margin: '6px 0 12px', lineHeight: '18px' }}>
              {t('This sender is in your contacts, but this message has red flags. Their account may be compromised. Choose what to do:')}
            </p>
            <div className="row wrap" style={{ gap: 8 }}>
              <button
                type="button"
                className="btn sm"
                style={{ background: '#C62828', color: '#FFF', whiteSpace: 'nowrap', fontSize: 12, padding: '6px 12px', flexShrink: 0 }}
                onClick={() => {
                  onSaveToHistory?.(run);
                  toast(t('Shared as scam to circle & history'), 'check');
                }}
                disabled={savedToHistory}
              >
                <Icon name="share" /> {savedToHistory ? t('Shared to circle & history') : t('Mark as scam')}
              </button>
              <button
                type="button"
                className="btn sm ghost"
                style={{ whiteSpace: 'nowrap', fontSize: 12, padding: '6px 12px', flexShrink: 0 }}
                onClick={() => {
                  onDismiss?.();
                  toast(t("Dismissed"), 'x');
                }}
              >
                <Icon name="shieldCheck" /> {t("It's okay")}
              </button>
            </div>
          </div>
        )}
        {!isKnownContact && a.verdict !== 'safe' && a.riskScore >= 65 && (
          <div className="card" style={{ background: 'var(--mint-50, #f0faf4)', border: '1px solid var(--mint-600, #2f8f6b)', padding: '10px 14px', margin: '10px 0', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div className="row" style={{ alignItems: 'center', gap: 8, color: 'var(--mint-700, #1e6140)', fontWeight: 600, fontSize: 13 }}>
              <Icon name="shield" style={{ width: 16, height: 16 }} />
              <span>{t('Auto-shared to Family Circle & saved to History')}</span>
            </div>
            <button
              type="button"
              className="btn sm ghost"
              style={{ padding: '3px 8px', fontSize: 12 }}
              onClick={() => {
                onDismiss?.();
                toast(t("Dismissed"), 'x');
              }}
            >
              <Icon name="shieldCheck" /> {t("It's okay")}
            </button>
          </div>
        )}
        <div className="verdict-actions">
          <button className="btn sm ghost" onClick={() => speak(run)}>
            <Icon name="volume" /> {t('Read aloud')}
          </button>
          {a.verdict !== 'safe' && settings.familyPhone && (
            <a className="btn sm soft" href={`tel:${settings.familyPhone}`}>
              <Icon name="phone" /> {t('Call my family')}
            </a>
          )}
          {a.verdict === 'scam' && (
            <a className="btn sm ghost" href="tel:1930" title="National Cyber Crime Helpline, if you already paid or shared a code">
              <Icon name="phone" /> {t('Call 1930')}
            </a>
          )}
          {isShareable(a) && (
            <a
              className="btn sm whatsapp"
              href={whatsappLink(resultWhatsAppText(a, excerpt(redact(run.input, engineSettings().privateTerms).text, 160)))}
              target="_blank"
              rel="noreferrer"
              title="Opens WhatsApp with this warning written. Pick yourself or family and tap Send."
            >
              <Icon name="message" /> {t('Send to WhatsApp')}
            </a>
          )}
          {!savedToHistory ? (
            <button className="btn sm primary" onClick={() => onSaveToHistory?.(run)}>
              <Icon name="bookmark" /> {t('Add to History')}
            </button>
          ) : (
            <span className="badge mint sm">
              <Icon name="check" /> {t('Saved to History')}
            </span>
          )}
          {isShareable(a) && (
            <button
              className="btn sm tell-circle"
              onClick={() => {
                if (!savedToHistory) {
                  onSaveToHistory?.(run);
                }
                setSharing(true);
              }}
              title={!savedToHistory ? t('Saves to history and shares with your circle') : t('Tell my circle')}
            >
              <Icon name="share" /> {t('Tell my circle')}
            </button>
          )}
          <button className="btn sm ghost" onClick={() => setCardOpen(true)}>
            <Icon name="card" /> {t('Alert picture')}
          </button>
          {canEscalate && (
            <button className={`btn sm ${borderline ? 'soft' : 'ghost'}`} onClick={escalate} disabled={opinionBusy}>
              <Icon name="scale" /> {opinionBusy ? 'Asking the cloud…' : t('Second opinion')}
            </button>
          )}
        </div>
        {canEscalate && borderline && !opinionBusy && (
          <div className="escalate-hint">
            <Icon name="sparkle" />
            <span>
              {run.source === 'on_device' ? 'Gemini Nano is not sure about this one.' : 'This one is borderline.'} A <b>second opinion</b> from the larger cloud model uses a redacted copy of the message.
            </span>
          </div>
        )}
        {opinionErr && <div className="callout err" style={{ marginTop: 12 }}><Icon name="alert" />{opinionErr}</div>}
      </section>

      {run.secondOpinion && <SecondOpinion run={run} />}

      <div className="result-grid">
        <section className="card card-pad">
          <div className="card-head">
            <h3 className="card-title">
              <span className="ic"><Icon name="message" /></span>{t('The message')}
            </h3>
            <span className="legend xs subtle">
              <i className="hl hl-flag">flag</i> <i className="hl hl-signal">pattern</i>
            </span>
          </div>
          {run.input.trim() ? <Highlighted text={run.input} flags={a.redFlags} signals={signals} /> : <p className="subtle small">Screenshot only.</p>}
        </section>

        <section className="card card-pad">
          <div className="card-head">
            <h3 className="card-title">
              <span className="ic rose"><Icon name="alert" /></span>
              {t('Red flags')}
            </h3>
            <span className="count">{a.redFlags.length}</span>
          </div>
          {a.redFlags.length === 0 ? (
            <p className="subtle small">{t('No red flags found.')}</p>
          ) : (
            <ol className="flags">
              {a.redFlags.map((f, i) => (
                <li
                  key={i}
                  style={{ animationDelay: `${i * 70}ms`, cursor: 'pointer' }}
                  className="flag-clickable"
                  onClick={() => setExpandedFlag(f)}
                  title={t('Click to see why it was flagged in detail')}
                >
                  <div className="row between items-center">
                    <div className="flag-label">{f.label}</div>
                    <span className="xs subtle" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <Icon name="search" /> {t('Details')}
                    </span>
                  </div>
                  {f.evidence && <q className="flag-ev">{f.evidence}</q>}
                  <div className="flag-why">{f.why}</div>
                </li>
              ))}
            </ol>
          )}
          {a.reassurances.length > 0 && (
            <>
              <hr className="divider" />
              <ul className="reassure">
                {a.reassurances.map((r, i) => (
                  <li key={i}>
                    <Icon name="check" />
                    {r}
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        <section className="card card-pad tint-mint">
          <div className="card-head">
            <h3 className="card-title">
              <span className="ic mint"><Icon name="shieldCheck" /></span>{t('What to do now')}
            </h3>
          </div>
          <Steps steps={a.nextSteps} runId={run.id} />
          {a.safeReply && (
            <div className="safe-reply">
              <div className="row between">
                <span className="eyebrow">{t('If you want to reply')}</span>
                <button className="btn sm ghost" onClick={() => copyText(a.safeReply, 'Reply copied')}>
                  <Icon name="copy" /> {t('Copy')}
                </button>
              </div>
              <p>{a.safeReply}</p>
            </div>
          )}
        </section>

        <FollowUp run={run} mode={settings.mode} />
      </div>

      {a.verdict !== 'safe' && <ReportCard run={run} />}

      {showSent && (
        <Modal label="What was sent to the cloud" onClose={() => setShowSent(false)}>
          <div className="stack">
            <span className="eyebrow">Privacy receipt</span>
            <h3 className="display" style={{ fontSize: 'var(--fs-xl)' }}>Exactly what left your device</h3>
            <p className="muted small">
              Gemini Nano was not available, so Chaukas fell back to the cloud. Personal details were replaced locally before sending, and put back
              into the answer on your device.
            </p>
            <pre className="receipt">{run.sentToCloud}</pre>
            <button className="btn primary" onClick={() => setShowSent(false)}>Done</button>
          </div>
        </Modal>
      )}
      {expandedFlag && (
        <Modal label={expandedFlag.label} onClose={() => setExpandedFlag(null)}>
          <div className="stack" style={{ gap: 16 }}>
            <div className="row between items-center">
              <span className="badge rose">
                <Icon name="alert" /> {t('Red Flag Detailed Breakdown')}
              </span>
              <span className="eyebrow">{CATEGORY_LABEL[a.category]}</span>
            </div>
            <h3 className="display" style={{ fontSize: 'var(--fs-xl)' }}>{expandedFlag.label}</h3>
            {expandedFlag.evidence && (
              <div style={{ background: 'var(--paper-2)', padding: '12px 16px', borderRadius: 12, borderLeft: '4px solid var(--rose-600)' }}>
                <span className="xs subtle" style={{ fontWeight: 600, textTransform: 'uppercase', display: 'block', marginBottom: 4 }}>
                  {t('Flagged message quote')}
                </span>
                <blockquote style={{ margin: 0, fontStyle: 'italic', fontSize: 'var(--fs-md)', color: 'var(--ink)' }}>
                  “{expandedFlag.evidence}”
                </blockquote>
              </div>
            )}
            <div>
              <h4 style={{ fontWeight: 600, fontSize: 'var(--fs-sm)', marginBottom: 6 }}>{t('Why this was flagged:')}</h4>
              <p style={{ margin: 0, color: 'var(--ink-2)', lineHeight: 1.5 }}>{expandedFlag.why}</p>
              <div style={{ marginTop: 12, padding: '12px 14px', borderRadius: 10, background: 'rgba(201, 75, 54, 0.08)', border: '1px solid rgba(201, 75, 54, 0.15)' }}>
                <b style={{ color: 'var(--rose-700)', fontSize: 'var(--fs-sm)', display: 'block', marginBottom: 4 }}>
                  {t('How scammers use this trick')}
                </b>
                <span style={{ fontSize: 'var(--fs-xs)', color: 'var(--ink-2)', lineHeight: 1.4, display: 'block' }}>
                  {t('Fraudsters use urgent pressure, threats of suspension, or fake rewards to rush you into acting before checking. Legitimate banks and services never ask for OTPs or payments via random texted links.')}
                </span>
              </div>
            </div>
            <hr className="divider" />
            <div className="row wrap between items-center" style={{ gap: 10 }}>
              <div className="row items-center" style={{ gap: 8 }}>
                {!savedToHistory ? (
                  <button
                    className="btn primary"
                    onClick={() => {
                      onSaveToHistory?.(run);
                    }}
                  >
                    <Icon name="bookmark" /> {t('Add to History')}
                  </button>
                ) : (
                  <span className="badge mint">
                    <Icon name="check" /> {t('Saved to History')}
                  </span>
                )}
                <button
                  className="btn tell-circle"
                  disabled={!savedToHistory}
                  onClick={() => {
                    setExpandedFlag(null);
                    setSharing(true);
                  }}
                  title={!savedToHistory ? t('Add to History first to share with circle') : ''}
                >
                  <Icon name="share" /> {t('Tell my circle')}
                </button>
              </div>
              <button className="btn ghost" onClick={() => setExpandedFlag(null)}>
                {t('Close')}
              </button>
            </div>
          </div>
        </Modal>
      )}
      {sharing && <ShareToCircle run={run} onClose={() => setSharing(false)} />}
      {cardOpen && <WarningCardModal run={run} onClose={() => setCardOpen(false)} />}
    </div>
  );
}

function Steps({ steps, runId }: { steps: string[]; runId: string }) {
  const [done, setDone] = useState<Set<number>>(new Set());
  useEffect(() => setDone(new Set()), [runId]);
  const t = useT();
  if (!steps.length) return <p className="subtle small">{t('No action needed.')}</p>;
  return (
    <ul className="steps">
      {steps.map((s, i) => (
        <li key={i}>
          <button
            className={`step ${done.has(i) ? 'done' : ''}`}
            onClick={() =>
              setDone((d) => {
                const n = new Set(d);
                if (n.has(i)) n.delete(i);
                else n.add(i);
                return n;
              })
            }
          >
            <span className="tick"><Icon name="check" /></span>
            <span>{s}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}

function SecondOpinion({ run }: { run: AnalysisRun }) {
  const so = run.secondOpinion!;
  const agree = so.analysis.verdict === run.analysis.verdict;
  return (
    <section className="card card-pad compare">
      <div className="card-head">
        <h3 className="card-title">
          <span className="ic"><Icon name="scale" /></span>Second opinion
        </h3>
        <span className={`badge ${agree ? 'device' : 'preview'}`}>{agree ? 'Both models agree' : 'Models disagree, take care'}</span>
      </div>
      <div className="compare-grid">
        {[
          { title: run.source === 'on_device' ? 'Gemini Nano (on device)' : 'First check', a: run.analysis, source: run.source, ms: run.latencyMs },
          { title: 'Gemini (cloud, redacted input)', a: so.analysis, source: so.source, ms: so.latencyMs },
        ].map((c) => (
          <div key={c.title} className={`compare-col v-${c.a.verdict}`}>
            <div className="row between">
              <span className="small" style={{ fontWeight: 600 }}>{c.title}</span>
              <SourceBadge source={c.source} latencyMs={c.ms} />
            </div>
            <div className="compare-score">
              <span className="display">{c.a.riskScore}</span>
              <span>{VERDICT_LABEL[c.a.verdict]}</span>
            </div>
            <p className="small muted">{c.a.summary}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

const SUGGESTED = ['How can I check if this is real?', 'I already clicked the link. What now?', 'How do I report this?'];

function FollowUp({ run, mode }: { run: AnalysisRun; mode: 'private' | 'smart' | 'cloud' }) {
  const [q, setQ] = useState('');
  const [answer, setAnswer] = useState('');
  const [asked, setAsked] = useState('');
  const [busy, setBusy] = useState(false);
  const [source, setSource] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    setAnswer('');
    setAsked('');
    setSource(null);
  }, [run.id]);

  const ask = async (question: string) => {
    if (!question.trim() || busy) return;
    setBusy(true);
    setErr(null);
    setAnswer('');
    setAsked(question);
    setQ('');
    try {
      const s = await askFollowUp(run, question, mode, engineSettings(), setAnswer);
      setSource(s);
    } catch (e) {
      setErr(e instanceof ChaukasError ? `${e.message} ${e.hint ?? ''}` : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card card-pad tint-lilac followup">
      <div className="card-head">
        <h3 className="card-title">
          <span className="ic"><Icon name="sparkle" /></span>Ask Chaukas
        </h3>
        {source && <SourceBadge source={source} />}
      </div>
      {asked ? (
        <div className="qa">
          <div className="q-bubble">{asked}</div>
          <div className="a-bubble">
            {answer || <span className="typing"><i /><i /><i /></span>}
            {busy && answer && <span className="caret" />}
          </div>
        </div>
      ) : (
        <div className="row wrap" style={{ gap: 6, marginBottom: 12 }}>
          {SUGGESTED.map((s) => (
            <button key={s} className="chip" onClick={() => ask(s)}>{s}</button>
          ))}
        </div>
      )}
      {err && <div className="callout err" style={{ marginBottom: 10 }}><Icon name="alert" />{err}</div>}
      <form
        className="ask-box"
        onSubmit={(e) => {
          e.preventDefault();
          ask(q);
        }}
      >
        <input className="input" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ask a follow-up question…" aria-label="Follow-up question" />
        <button className="btn lilac icon" disabled={!q.trim() || busy} aria-label="Ask">
          <Icon name="arrowUp" />
        </button>
      </form>
    </section>
  );
}

function WarningCardModal({ run, onClose }: { run: AnalysisRun; onClose: () => void }) {
  const [blob, setBlob] = useState<Blob | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    let made: string | null = null;
    const terms = engineSettings().privateTerms;
    renderWarningCard(run, terms)
      .then((b) => {
        if (!alive) return;
        made = URL.createObjectURL(b);
        setBlob(b);
        setUrl(made);
      })
      .catch(() => toast('Could not draw the picture', 'x'));
    return () => {
      alive = false;
      if (made) URL.revokeObjectURL(made);
    };
  }, [run]);
  const share = async () => {
    if (!blob) return;
    const r = await shareWarningCard(blob, VERDICT_LABEL[run.analysis.verdict]);
    if (r === 'downloaded') toast('Picture saved. Send it on WhatsApp or to your family group.');
    if (r !== 'cancelled') onClose();
  };
  return (
    <Modal onClose={onClose} label="Scam alert picture">
      <h3 className="card-title" style={{ marginTop: 0 }}><span className="ic peach"><Icon name="card" /></span>Scam alert picture</h3>
      <p className="small muted" style={{ margin: '0 0 12px' }}>Made on your device. Phone numbers, codes and names from the message are hidden, so it is safe to forward.</p>
      <div className="wc-preview">{url ? <img src={url} alt="Preview of the scam alert picture" /> : <span className="spinner" />}</div>
      <div className="row" style={{ justifyContent: 'flex-end', marginTop: 14 }}>
        <button className="btn ghost" onClick={onClose}>Close</button>
        <button className="btn primary" onClick={share} disabled={!blob}><Icon name="share" /> Share or save</button>
      </div>
    </Modal>
  );
}
