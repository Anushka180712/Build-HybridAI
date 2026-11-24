/**
 * Privacy, language, comfort and data settings, with a live redaction preview.
 */
import { LANGUAGES, languageCode, ON_DEVICE_LANGUAGES, settingsStore } from '../data/settings';
import { useT } from '../lib/i18n';
import { clearHistory } from '../data/history';
import { redact } from '../ai/redact';
import { Icon } from '../components/Icon';
import { SwitchRow, toast } from '../components/ui';
import { cleanPhone } from '../lib/verdictWords';

const DEMO = 'Hi Rose, your code is 482913. Call me on +44 7700 900123 or email rose.k@example.com';

export function SettingsView() {
  const t = useT();
  const s = settingsStore.use();
  const preview = redact(DEMO, s.privateTerms.split(',').map((t) => t.trim()).filter(Boolean));
  return (
    <div className="page">
      <header className="page-head">
        <div>
          <span className="eyebrow">{t('Make it yours')}</span>
          <h1 className="display page-title">{t('Settings')}</h1>
        </div>
      </header>

      <section className="card card-pad settings-group">
        <h3 className="card-title"><span className="ic"><Icon name="lock" /></span>{t('Privacy')}</h3>
        <SwitchRow
          title={t('Remove personal details before using the cloud')}
          hint={t('Phone numbers, emails, card numbers, codes and names are swapped for placeholders on your device.')}
          checked={s.redactBeforeCloud}
          onChange={(v) => settingsStore.set({ redactBeforeCloud: v })}
        />
        <SwitchRow
          title={t('Allow screenshots to be checked in the cloud')}
          hint={t('Screenshots cannot be redacted. When off, screenshots are only checked on this device.')}
          checked={s.allowCloudImages}
          onChange={(v) => settingsStore.set({ allowCloudImages: v })}
        />
        <div className="field" style={{ marginTop: 8 }}>
          <label htmlFor="terms">{t('Always hide these words')}</label>
          <input id="terms" className="input" placeholder="Your name, your street, your bank…" value={s.privateTerms} onChange={(e) => settingsStore.set({ privateTerms: e.target.value })} />
          <span className="hint">Separate with commas. Try typing “Rose”.</span>
        </div>
        <div className="redact-demo">
          <div>
            <span className="eyebrow">{t('You see')}</span>
            <p className="mono small">{DEMO}</p>
          </div>
          <Icon name="arrowRight" />
          <div>
            <span className="eyebrow">{t('The cloud sees')}</span>
            <p className="mono small">{preview.text}</p>
          </div>
        </div>
      </section>

      <section className="card card-pad settings-group">
        <h3 className="card-title"><span className="ic lilac"><Icon name="message" /></span>{t('Explain in my language')}</h3>
        <p className="small muted" style={{ margin: '0 0 12px' }}>
          Chaukas reads messages in any language. Pick the language you want the verdict, the reasons and the next steps written in.
        </p>
        <div className="lang-grid" role="radiogroup" aria-label={t('Explanation language')}>
          {LANGUAGES.map((l) => (
            <button
              key={l.code}
              role="radio"
              aria-checked={s.language === l.name}
              className={`lang-pill${s.language === l.name ? ' on' : ''}`}
              onClick={() => settingsStore.set({ language: l.name })}
            >
              <span className="lang-native" lang={l.code}>{l.native}</span>
              <span className="xs subtle">{l.name}</span>
            </button>
          ))}
        </div>
        {!ON_DEVICE_LANGUAGES.includes(languageCode(s.language)) && (
          <p className="hint" style={{ marginTop: 10 }}>
            Gemini Nano on your device is tuned for English, Spanish and Japanese. For {s.language}, Smart and Most accurate modes give the best wording; Private mode will try its best.
          </p>
        )}
        <div style={{ marginTop: 12 }}>
          <SwitchRow
            title={t('Casual tone')}
            hint={s.language === 'Hindi' ? 'Answers in friendly Hinglish, like "Bhai, yeh pakka fraud hai. Link mat kholna."' : 'Friendly, everyday wording, like a family member texting back.'}
            checked={s.casualTone}
            onChange={(v) => settingsStore.set({ casualTone: v })}
          />
        </div>
      </section>

      <section className="card card-pad settings-group">
        <h3 className="card-title"><span className="ic peach"><Icon name="eye" /></span>{t('Comfort')}</h3>
        <SwitchRow title={t('Larger text')} hint={t('Makes everything easier to read.')} checked={s.largeText} onChange={(v) => settingsStore.set({ largeText: v })} />
        <SwitchRow title={t('Reduce motion')} hint={t('Turns off animations.')} checked={s.reduceMotion} onChange={(v) => settingsStore.set({ reduceMotion: v })} />
        <div className="field" style={{ marginTop: 12 }}>
          <label htmlFor="family-phone">{t('Family member to call')}</label>
          <input
            id="family-phone"
            className="input"
            type="tel"
            inputMode="tel"
            placeholder="e.g. +91 98765 43210"
            defaultValue={s.familyPhone}
            onBlur={(e) => {
              const p = cleanPhone(e.target.value);
              if (e.target.value.trim() && !p) return toast('That does not look like a phone number', 'x');
              settingsStore.set({ familyPhone: p });
              if (p) toast('Saved. A "Call my family" button appears when a message looks risky.');
            }}
          />
          <span className="hint">Shows a one-tap <b>{t('Call my family')}</b> button on risky results. Stays on this device.</span>
        </div>
      </section>

      <section className="card card-pad settings-group">
        <h3 className="card-title"><span className="ic mint"><Icon name="device" /></span>{t('Data')}</h3>
        <SwitchRow
          title={t('Sync history across my devices')}
          hint="When signed in, only the verdict and a redacted excerpt are stored in your private Firestore space."
          checked={s.syncHistory}
          onChange={(v) => settingsStore.set({ syncHistory: v })}
        />
        <div className="row" style={{ marginTop: 8 }}>
          <button
            className="btn danger"
            onClick={() => {
              if (window.confirm('Delete all checks stored on this device?')) {
                clearHistory();
                toast('History cleared');
              }
            }}
          >
            <Icon name="trash" /> Clear history on this device
          </button>
        </div>
      </section>

      <a className="card card-pad settings-group engine-link" href="#/engine">
        <span className="ic butter"><Icon name="chip" /></span>
        <span className="grow">
          <b>{t('AI engine')}</b>
          <span className="xs subtle">Gemini Nano status and download, how each check was routed, speed. For the curious.</span>
        </span>
        <Icon name="arrowRight" />
      </a>

      <p className="xs subtle" style={{ textAlign: 'center', margin: '24px 0' }}>
        Chaukas is open source under the Apache 2.0 licence. It gives guidance, not guarantees. If money has already been sent, contact your bank straight away.
      </p>
    </div>
  );
}
