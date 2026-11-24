import { useState } from 'react';
import { useT } from '../lib/i18n';
import { settingsStore } from '../data/settings';
import { Icon, Logo, type IconName } from './Icon';
import { Modal } from './ui';

const STEPS: { icon: IconName; tint: string; title: string; body: string }[] = [
  { icon: 'clipboard', tint: 'lilac', title: 'Check anything that feels off', body: 'Tap Speak and say it, paste a text or WhatsApp message, or add a screenshot. You can also share straight into Chaukas from your phone.' },
  { icon: 'lock', tint: 'mint', title: 'It stays on your device', body: 'Gemini Nano runs inside Chrome. If the cloud is ever needed, your numbers, emails and codes are removed first.' },
  { icon: 'users', tint: 'peach', title: 'Look after each other', body: 'Warn your family in one tap, and practise spotting scams in the Dojo.' },
];

/** First-run welcome. Short, friendly, skippable. */
export function Welcome({ onTry }: { onTry: () => void }) {
  const t = useT();
  const { onboarded } = settingsStore.use();
  const [step, setStep] = useState(0);
  if (onboarded) return null;
  const done = () => settingsStore.set({ onboarded: true });
  const s = STEPS[step];
  return (
    <Modal label="Welcome to Chaukas" onClose={done}>
      <div className="welcome">
        <div className="welcome-top">
          <Logo size={40} />
          <button className="btn ghost sm" onClick={done}>{t('Skip')}</button>
        </div>
        <div key={step} className={`welcome-art t-${s.tint}`}>
          <span className="welcome-ic"><Icon name={s.icon} /></span>
        </div>
        <h2 className="display welcome-title">{t(s.title)}</h2>
        <p className="muted">{t(s.body)}</p>
        <div className="welcome-dots" aria-hidden="true">
          {STEPS.map((_, i) => (
            <i key={i} className={i === step ? 'on' : ''} />
          ))}
        </div>
        <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
          {step < STEPS.length - 1 ? (
            <button className="btn primary lg" onClick={() => setStep(step + 1)}>
              {t('Next')} <Icon name="arrowRight" />
            </button>
          ) : (
            <>
              <button className="btn lg" onClick={done}>{t("I'll paste my own")}</button>
              <button
                className="btn primary lg"
                onClick={() => {
                  done();
                  onTry();
                }}
              >
                Try an example <Icon name="sparkle" />
              </button>
            </>
          )}
        </div>
      </div>
    </Modal>
  );
}
