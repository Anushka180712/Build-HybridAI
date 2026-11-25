/**
 * Scam Dojo: a practice game. The app decides locally whether each round is a scam, so the
 * model cannot bias the answer.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { generateDojoRound, type DojoRound } from '../ai/engine';
import type { DojoBrief } from '../ai/prompts';
import { settingsStore } from '../data/settings';
import { createStore } from '../lib/store';
import { rcStore } from '../lib/appState';
import { Icon } from '../components/Icon';
import { SourceBadge } from '../components/ui';
import { useT } from '../lib/i18n';
import { getInstantDojoRound } from '../data/dojoCurated';

const THEMES = [
  'a parcel delivery',
  'a bank account alert',
  'a streaming subscription renewal',
  'a tax refund',
  'a job opportunity',
  'a grandchild needing help',
  'a doctor appointment',
  'a school announcement',
  'an online marketplace sale',
  'a mobile phone bill',
  'a prize draw',
  'a utility company payment',
  'a password reset',
  'a crypto investment',
  'a friend sharing a photo link',
  'a bank KYC update in India',
  'a UPI payment or refund in India',
  'an Indian electricity bill',
  'a courier parcel held by customs in India',
  'an Aadhaar or PAN card notice',
];
const CHANNELS = ['text message', 'email', 'WhatsApp message'];

const dojoStore = createStore('chaukas.dojo.v1', { played: 0, correct: 0, streak: 0, best: 0 });

function randomBrief(): DojoBrief {
  return {
    isScam: Math.random() < 0.55,
    theme: THEMES[Math.floor(Math.random() * THEMES.length)],
    channel: CHANNELS[Math.floor(Math.random() * CHANNELS.length)],
  };
}

const RANKS = [
  { at: 0, name: 'Curious beginner' },
  { at: 3, name: 'Sharp eye' },
  { at: 6, name: 'Scam spotter' },
  { at: 10, name: 'Fraud fighter' },
  { at: 15, name: 'Chaukas sensei' },
];

export function DojoView() {
  const t = useT();
  const stats = dojoStore.use();
  const rc = rcStore.use();
  const settings = settingsStore.use();
  const lang = settings.language;
  const [round, setRound] = useState<DojoRound | null>(() => getInstantDojoRound(lang));
  const [guess, setGuess] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const next = useRef<Promise<DojoRound> | null>(null);

  const fetchRound = useCallback(
    () => generateDojoRound({ ...randomBrief(), language: lang }, settings.mode === 'private' ? 'private' : 'smart'),
    [settings.mode, lang]
  );

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    setGuess(null);
    try {
      // If next round was prefetched, use it; otherwise fetch or fallback to instant round
      const r = next.current ? await next.current : await Promise.race([
        fetchRound(),
        new Promise<DojoRound>((_, reject) => setTimeout(() => reject(new Error('timeout')), 2500)),
      ]);
      next.current = null;
      setRound(r);
    } catch {
      next.current = null;
      setRound(getInstantDojoRound(lang));
    } finally {
      setBusy(false);
    }
  }, [fetchRound, lang]);

  // Preload next round in background on mount
  useEffect(() => {
    if (!next.current) {
      next.current = fetchRound().catch(() => getInstantDojoRound(lang));
    }
  }, [fetchRound, lang]);

  const answer = (saysScam: boolean) => {
    if (!round || guess !== null) return;
    setGuess(saysScam);
    const right = saysScam === round.brief.isScam;
    dojoStore.set((s) => ({
      played: s.played + 1,
      correct: s.correct + (right ? 1 : 0),
      streak: right ? s.streak + 1 : 0,
      best: Math.max(s.best, right ? s.streak + 1 : 0),
    }));
    // Prepare the next round in background while the person reads the explanation
    next.current = fetchRound().catch(() => getInstantDojoRound(lang));
  };

  const rank = [...RANKS].reverse().find((r) => stats.correct >= r.at)!;
  const nextRank = RANKS.find((r) => r.at > stats.correct);
  const right = round && guess !== null ? guess === round.brief.isScam : null;

  if (!rc.dojoEnabled) {
    return (
      <div className="page">
        <div className="card empty"><h3>{t('Scam Dojo is resting')}</h3><p className="small">{t('Practice rounds are paused right now. Check back soon.')}</p></div>
      </div>
    );
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <span className="eyebrow">{t('Practice makes you scam-proof')}</span>
          <h1 className="display page-title">{t('Scam Dojo')}</h1>
        </div>
      </header>

      <section className="dojo-stats">
        <div className="rank">
          <span className="rank-badge"><Icon name="target" /></span>
          <div className="grow">
            <b>{t(rank.name)}</b>
            <div className="progress" style={{ marginTop: 6 }}>
              <i style={{ width: `${nextRank ? ((stats.correct - rank.at) / (nextRank.at - rank.at)) * 100 : 100}%` }} />
            </div>
            <span className="xs subtle">{nextRank ? `${nextRank.at - stats.correct} ${t('more to')} ${t(nextRank.name)}` : t('Top rank reached')}</span>
          </div>
        </div>
        <div className="mini-stat"><span className="display">{stats.streak}</span><span>{t('streak')} <Icon name="flame" /></span></div>
        <div className="mini-stat"><span className="display">{stats.played ? Math.round((stats.correct / stats.played) * 100) : 0}%</span><span>{t('accuracy')}</span></div>
        <div className="mini-stat"><span className="display">{stats.best}</span><span>{t('best')}</span></div>
      </section>

      <section className="dojo-stage">
        <div className="phone">
          <div className="phone-top">
            <span className="phone-avatar">{round?.sender.charAt(0) ?? '·'}</span>
            <div className="grow">
              <b className="small">{busy ? '…' : round?.sender}</b>
              <div className="xs subtle">{round?.brief.channel ? t(round.brief.channel) : ''}</div>
            </div>
          </div>
          <div className="phone-body">
            {busy || !round ? (
              <div className="bubble skeleton-bubble">
                <div className="skeleton" style={{ height: 12, width: '90%' }} />
                <div className="skeleton" style={{ height: 12, width: '75%', marginTop: 8 }} />
                <div className="skeleton" style={{ height: 12, width: '50%', marginTop: 8 }} />
              </div>
            ) : (
              <div className={`bubble ${right === null ? '' : right ? 'ok' : 'nope'}`}>{round.message}</div>
            )}
          </div>
        </div>

        <div className="dojo-side">
          {error ? (
            <div className="callout err"><Icon name="alert" /><div>{error}<div style={{ marginTop: 8 }}><button className="btn sm" onClick={load}>{t('Try again')}</button></div></div></div>
          ) : guess === null ? (
            <>
              <h2 className="display" style={{ fontSize: 'var(--fs-2xl)' }}>{t('Scam, or the real thing?')}</h2>
              <p className="muted">{t('Read it like you would on your phone. Trust your gut, then see how you did.')}</p>
              <div className="guess">
                <button className="guess-btn scam" disabled={busy || !round} onClick={() => answer(true)}>
                  <Icon name="shield" /> {t('Scam')}
                </button>
                <button className="guess-btn legit" disabled={busy || !round} onClick={() => answer(false)}>
                  <Icon name="check" /> {t('Legit')}
                </button>
              </div>
              {round && <span className="xs subtle row" style={{ gap: 6 }}>{t('Written by')} <SourceBadge source={round.source} /></span>}
            </>
          ) : (
            round && (
              <div className={`reveal ${right ? 'ok' : 'nope'}`}>
                <span className="reveal-tag">{right ? t('Nice catch!') : t('Not quite')}</span>
                <h2 className="display">{round.brief.isScam ? t('This was a scam') : t('This one was legit')}</h2>
                <p>{round.explanation}</p>
                {round.tells.length > 0 && (
                  <ul className="tells">
                    {round.tells.map((tell, i) => (
                      <li key={i}><Icon name="eye" />{tell}</li>
                    ))}
                  </ul>
                )}
                <button className="btn primary" onClick={load}>
                  {t('Next message')} <Icon name="arrowRight" />
                </button>
              </div>
            )
          )}
        </div>
      </section>
    </div>
  );
}
