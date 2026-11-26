/**
 * Sends a redacted alert to a Family Circle, or straight to someone on WhatsApp or SMS.
 */
import { useEffect, useState } from 'react';
import type { AnalysisRun } from '../ai/types';
import { listenMyCircles, shareToCircle, type Circle } from '../data/circle';
import { useUser } from '../data/auth';
import { isFirebaseConfigured } from '../firebase/config';
import { navigate } from '../lib/appState';
import { excerpt } from '../lib/format';
import { smsLink, warningText, whatsappLink } from '../lib/invite';
import { redact } from '../ai/redact';
import { engineSettings } from '../data/settings';
import { Icon } from './Icon';
import { Modal, toast } from './ui';

export function ShareToCircle({ run, onClose }: { run: AnalysisRun; onClose: () => void }) {
  const { user } = useUser();
  const [circles, setCircles] = useState<Circle[]>([]);
  const [pick, setPick] = useState<string>('');
  const [note, setNote] = useState(run.analysis.verdict === 'scam' ? 'Watch out, I got this one today.' : 'Does this look OK to you?');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!user) return;
    return listenMyCircles(user.uid, (c) => {
      setCircles(c);
      setPick((p) => p || c[0]?.id || '');
    });
  }, [user]);

  const preview = excerpt(redact(run.input, engineSettings().privateTerms).text, 220);
  const forward = warningText(run, excerpt(preview, 140));

  return (
    <Modal label="Share with your circle" onClose={onClose}>
      <div className="stack" style={{ gap: 16 }}>
        <div>
          <span className="eyebrow">Family Circle</span>
          <h3 className="display" style={{ fontSize: 'var(--fs-xl)', marginTop: 4 }}>Warn the people you care about</h3>
        </div>
        {!isFirebaseConfigured || !user || circles.length === 0 ? (
          <>
            <p className="muted">Create or join a circle first. It takes ten seconds and you only need a first name.</p>
            <div className="row">
              <button className="btn primary" onClick={() => { onClose(); navigate('circle'); }}>
                <Icon name="users" /> Set up my circle
              </button>
              <button className="btn ghost" onClick={onClose}>Not now</button>
            </div>
          </>
        ) : (
          <>
            <div className="field">
              <label>Send to</label>
              <div className="row wrap">
                {circles.map((c) => (
                  <button
                    key={c.id}
                    className={`chip ${pick === c.id ? 'on' : ''}`}
                    onClick={() => {
                      setPick(c.id);
                      if (typeof localStorage !== 'undefined') localStorage.setItem('chaukas_active_circle', c.id);
                    }}
                  >
                    <Icon name="users" /> {c.name}
                  </button>
                ))}
              </div>
            </div>
            <div className="field">
              <label htmlFor="note">Add a note</label>
              <input id="note" className="input" value={note} onChange={(e) => setNote(e.target.value)} maxLength={280} />
            </div>
            <div className="share-preview">
              <span className="eyebrow">They will see (personal details removed)</span>
              <p className="mono small">{preview}</p>
            </div>
            <div className="row">
              <button
                className="btn primary"
                disabled={busy || !pick}
                onClick={async () => {
                  setBusy(true);
                  try {
                    await shareToCircle(pick, run, note);
                    toast('Shared with your circle');
                    onClose();
                  } catch (e) {
                    toast(e instanceof Error ? e.message : 'Could not share', 'x');
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                <Icon name="share" /> {busy ? 'Sharing…' : 'Share alert'}
              </button>
              <button className="btn ghost" onClick={onClose}>Cancel</button>
            </div>
          </>
        )}
        <div className="forward">
          <span className="eyebrow">Or send the warning directly (personal details removed)</span>
          <div className="row wrap">
            <a className="btn whatsapp sm" href={whatsappLink(forward)} target="_blank" rel="noreferrer">
              <Icon name="message" /> WhatsApp
            </a>
            <a className="btn soft sm" href={smsLink(forward)}>
              <Icon name="phone" /> SMS
            </a>
          </div>
        </div>
      </div>
    </Modal>
  );
}
