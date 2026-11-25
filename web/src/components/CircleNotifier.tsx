/**
 * Group notifications. While Chaukas is open or in a background tab, it listens to the
 * person's circles and shows a notification when someone else shares a scam warning or adds
 * them to a group. Opt-in, using the browser's own permission prompt.
 */
import { useEffect, useState } from 'react';
import { listenAlerts, listenMyCircles, type Circle } from '../data/circle';
import { listenMyInvites } from '../data/people';
import { useUser } from '../data/auth';
import { VERDICT_LABEL } from '../lib/format';
import type { Verdict } from '../ai/types';
import { Icon } from './Icon';
import { toast } from './ui';
import { useT } from '../lib/i18n';

const KEY = 'chaukas.notifyCircle';
const supported = () => typeof Notification !== 'undefined';

function enabled(): boolean {
  try {
    return localStorage.getItem(KEY) === '1' && supported() && Notification.permission === 'granted';
  } catch {
    return false;
  }
}

async function notify(title: string, body: string, tag: string) {
  // Always show an in-app note; add a system notification when allowed.
  toast(title, 'info');
  if (!enabled() || document.visibilityState === 'visible') return;
  const options: NotificationOptions = { body, tag, icon: '/icon.svg', badge: '/icon.svg', data: { url: '/#/circle' } };
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) return void (await reg.showNotification(title, options));
    new Notification(title, options);
  } catch {
    /* the toast is enough */
  }
}

/** Mounted once in the app shell. Renders nothing. */
export function CircleNotifier() {
  const { user } = useUser();
  const [circles, setCircles] = useState<Circle[]>([]);

  useEffect(() => {
    if (!user) return;
    return listenMyCircles(user.uid, setCircles);
  }, [user]);

  const ids = circles.map((c) => c.id).sort().join(',');
  useEffect(() => {
    if (!user) return;
    // Only alerts created after this moment are news (a little slack for clock differences).
    const since = Date.now() - 10_000;
    const told = new Set<string>();
    const stops = circles.map((c) =>
      listenAlerts(c.id, (alerts) => {
        for (const a of alerts) {
          if (told.has(a.id) || a.byUid === user.uid || a.createdAt < since) continue;
          told.add(a.id);
          const verdict = VERDICT_LABEL[a.verdict as Verdict] ?? a.verdict;
          void notify(`${a.byName} warned ${c.name}`, `${verdict}: ${a.note || a.summary}`.slice(0, 180), `alert-${a.id}`);
        }
      }),
    );
    return () => stops.forEach((s) => s());
    // Re-subscribe only when the set of circles changes, not on every member update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, ids]);

  useEffect(() => {
    if (!user) return;
    let seen: Set<string> | null = null;
    return listenMyInvites(user.uid, (invites) => {
      // Anything waiting when the app opens, or arriving later, gets a heads-up. The Keep-or-not
      // popup itself opens on the Family screen.
      for (const i of invites) {
        if (seen?.has(i.id)) continue;
        if (seen) void notify(`${i.by} wants to keep you in ${i.name}`, 'Open Family Circle to keep it or not.', `invite-${i.id}`);
        if (!location.hash.startsWith('#/circle')) toast(`${i.by} wants to keep you in ${i.name}. Open Family to answer.`, 'users');
      }
      seen = new Set(invites.map((i) => i.id));
    });
  }, [user]);

  return null;
}

/** The opt-in switch, shown in Family Circle. */
export function AlertNotifyToggle() {
  const t = useT();
  const [on, setOn] = useState(enabled);
  if (!supported()) {
    return <p className="xs subtle notify-note"><Icon name="info" style={{ width: 13, height: 13, verticalAlign: -2 }} /> {t('On iPhone, add Chaukas to your Home Screen to get group notifications.')}</p>;
  }
  const denied = Notification.permission === 'denied';
  const toggle = async () => {
    if (on) {
      try { localStorage.setItem(KEY, '0'); } catch { /* ignore */ }
      setOn(false);
      return;
    }
    const p = await Notification.requestPermission();
    if (p !== 'granted') return toast(t('Notifications are blocked. Allow them for this site in your browser settings.'), 'x');
    try { localStorage.setItem(KEY, '1'); } catch { /* ignore */ }
    setOn(true);
    toast(t("You'll be notified when your group shares a warning"));
  };
  return (
    <button className={`card card-pad notify-toggle ${on ? 'on' : ''}`} onClick={toggle} aria-pressed={on} disabled={denied && !on}>
      <Icon name="alert" />
      <span className="grow">
        <b>{on ? t('Group notifications on') : t('Notify me about group warnings')}</b>
        <span className="xs subtle">{denied ? t('Blocked in browser settings.') : on ? t('Tap to turn off.') : t('Get a notification when someone in your group shares a warning.')}</span>
      </span>
    </button>
  );
}
