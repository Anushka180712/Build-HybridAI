/**
 * Family Circle screen: join with just a name (no email) and get a personal Chaukas ID and QR.
 * Scanning someone's QR or typing their ID asks "Keep them?"; Keep puts them in my family group
 * (made on the spot if I have none) and they get the same Keep-or-not choice. Each person can be
 * in up to two groups, can Keep or Leave an invite any time later, and only scam warnings are shared.
 */
import { useEffect, useRef, useState } from 'react';
import { MAX_GROUPS, joinCircle, leaveCircle, listenAlerts, listenMyCircles, reactToAlert, setMyHandleIn, type Circle, type CircleAlert } from '../data/circle';
import { displayName, signInWithGoogle, signInWithName, signOutUser, useUser } from '../data/auth';
import { isFirebaseConfigured } from '../firebase/config';
import { getFirebase } from '../firebase/app';
import { settingsStore } from '../data/settings';
import { CATEGORY_LABEL, VERDICT_LABEL, timeAgo } from '../lib/format';
import { alertForwardText, codeFromParam, handleFromParam, inviteText, inviteUrl, normalizeHandle, personUrl, smsLink, whatsappLink } from '../lib/invite';
import { ensureMyHandle, findPerson, keepPerson, leaveInvite, listenMyInvites, stayInCircle, type Invite } from '../data/people';
import { decodeQr } from '../lib/qr';
import { QrCode } from '../components/QrCode';
import { AlertNotifyToggle } from '../components/CircleNotifier';
import type { Verdict } from '../ai/types';
import { Icon } from '../components/Icon';
import { useT } from '../lib/i18n';
import { Avatar, Modal, copyText, toast } from '../components/ui';

const REACTIONS = [
  { key: 'got', label: 'I got this too', icon: 'alert' },
  { key: 'scam', label: 'Agree, scam', icon: 'shield' },
  { key: 'seen', label: 'Seen it', icon: 'eye' },
  { key: 'call', label: "I'll call you", icon: 'phone' },
  { key: 'love', label: 'Thanks', icon: 'heart' },
] as const;

export function CircleView({ param }: { param?: string }) {
  const t = useT();
  const { user, ready } = useUser();
  const [circles, setCircles] = useState<Circle[]>([]);
  const [active, setActive] = useState<string>('');
  const [loaded, setLoaded] = useState(false);
  const inviteCode = codeFromParam(param);
  const personHandle = handleFromParam(param);
  const joining = useRef('');
  const [invites, setInvites] = useState<Invite[]>([]);
  const [personDone, setPersonDone] = useState(false);
  const [skipped, setSkipped] = useState<string[]>([]);

  // Opened from an invite link (#/circle/ABC123): join as soon as the person has a name.
  useEffect(() => {
    if (!user || !loaded || !inviteCode || joining.current === inviteCode) return;
    joining.current = inviteCode;
    if (circles.some((c) => c.id === inviteCode)) {
      setActive(inviteCode);
      history.replaceState(null, '', '#/circle');
      return;
    }
    joinCircle(inviteCode).then(
      (id) => {
        setActive(id);
        toast('You joined the circle');
        history.replaceState(null, '', '#/circle');
      },
      (err) => {
        toast(err instanceof Error ? err.message : 'Could not join', 'x');
        history.replaceState(null, '', '#/circle');
      },
    );
    // circles is read once, at the moment the list first loads.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, loaded, inviteCode]);

  useEffect(() => {
    if (!user) return;
    return listenMyInvites(user.uid, setInvites);
  }, [user]);

  useEffect(() => {
    if (!user) return;
    return listenMyCircles(user.uid, (c) => {
      setCircles(c);
      setLoaded(true);
      setActive((a) => (a && c.some((x) => x.id === a) ? a : c[0]?.id ?? ''));
    });
  }, [user]);

  if (!isFirebaseConfigured) {
    return (
      <div className="page">
        <CircleHeader />
        <div className="callout info">
          <Icon name="info" />
          <span>Family Circle syncs through Cloud Firestore. Add your Firebase config to <code>.env.local</code> to turn it on.</span>
        </div>
        <CirclePitch />
      </div>
    );
  }
  if (!ready) return <div className="page"><CircleHeader /><div className="skeleton" style={{ height: 180 }} /></div>;
  if (!user) {
    return (
      <div className="page">
        <CircleHeader />
        <SignIn inviteCode={inviteCode} addingSomeone={!!personHandle} />
        <CirclePitch />
      </div>
    );
  }

  const circle = circles.find((c) => c.id === active);

  return (
    <div className="page">
      <CircleHeader>
        <div className="row">
          <Avatar name={displayName(user)} />
          <span className="small">{displayName(user)}</span>
          {!user.isAnonymous && (
            <button className="btn ghost sm" onClick={signOutUser} aria-label="Sign out">
              <Icon name="logout" />
            </button>
          )}
        </div>
      </CircleHeader>

      {personHandle && !personDone && loaded && (
        <KeepDialog
          handle={personHandle}
          group={circle ?? circles[0]}
          onClose={(id) => {
            if (id) setActive(id);
            setPersonDone(true);
            history.replaceState(null, '', '#/circle');
          }}
        />
      )}
      {(() => {
        const inv = invites.find((i) => !skipped.includes(i.id) && !circles.some((c) => c.id === i.id));
        return inv && (!personHandle || personDone) ? (
          <InvitePopup
            key={inv.id}
            invite={inv}
            groups={circles}
            onDone={(stayed) => {
              if (stayed) setActive(inv.id);
              else setSkipped((x) => [...x, inv.id]);
            }}
          />
        ) : null;
      })()}

      <div className="family">
        <WaitingInvites invites={invites.filter((i) => !circles.some((c) => c.id === i.id))} groups={circles} onKept={setActive} />
        {circles.length > 1 && (
          <div className="group-switch" role="tablist" aria-label="Your groups">
            {circles.map((c) => (
              <button key={c.id} role="tab" aria-selected={c.id === active} className={`chip ${c.id === active ? 'on' : ''}`} onClick={() => setActive(c.id)}>
                <Icon name="users" /> {c.name}
              </button>
            ))}
          </div>
        )}
        {circle ? (
          <>
            <GroupCard circle={circle} uid={user.uid} myName={user.displayName ?? ''} />
            <CircleFeed circle={circle} uid={user.uid} />
          </>
        ) : (
          <>
            <AddById />
            <CirclePitch />
          </>
        )}
        <details className="card fold" open={!circle}>
          <summary><Icon name="qr" /> {t('Your Chaukas ID')}</summary>
          <MyId name={user.displayName ?? ''} />
        </details>
        <details className="card fold">
          <summary><Icon name="settings" /> {t('Notifications and sharing')}</summary>
          <div className="fold-body">
            <AlertNotifyToggle />
            <AutoShareToggle />
          </div>
        </details>
        {!circle && (
          <details className="card fold">
            <summary><Icon name="users" /> {t('Got a group code?')}</summary>
            <JoinWithCode onDone={setActive} />
          </details>
        )}
      </div>
    </div>
  );
}

function CircleHeader({ children }: { children?: React.ReactNode }) {
  const t = useT();
  return (
    <header className="page-head">
      <div>
        <span className="eyebrow">{t('Look out for each other')}</span>
        <h1 className="display page-title">{t('Family Circle')}</h1>
      </div>
      {children}
    </header>
  );
}

function SignIn({ inviteCode, addingSomeone }: { inviteCode?: string; addingSomeone?: boolean }) {
  const t = useT();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  // The name is fixed once confirmed (the Chaukas ID is made from it), so ask once more first.
  const [confirming, setConfirming] = useState(false);
  const confirm = async () => {
    setBusy(true);
    try {
      await signInWithName(name);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Sign in failed', 'x');
      setBusy(false);
    }
  };
  return (
    <section className="card card-pad tint-peach signin">
      <div className="signin-art" aria-hidden="true">
        <Avatar name="Maya" size={44} />
        <Avatar name="Dad" size={52} />
        <Avatar name="Leo" size={44} />
      </div>
      {inviteCode && <span className="badge lilac">You're invited to circle {inviteCode}</span>}
      {addingSomeone && <span className="badge lilac">Add your name first, then pick a group for them</span>}
      <h2 className="display" style={{ fontSize: 'var(--fs-xl)' }}>What should your family call you?</h2>
      <p className="muted small">Just a name. No email, no password, no phone number.</p>
      <p className="callout info name-lock">
        <Icon name="lock" />
        <span>{t('Choose carefully. Once you confirm your name, it cannot be changed later. Your Chaukas ID is made from it.')}</span>
      </p>
      {confirming ? (
        <div className="name-confirm" role="group" aria-label="Confirm your name">
          <p className="small">Your family will see you as <b>{name.trim()}</b>. Is that right?</p>
          <div className="row" style={{ gap: 8, marginTop: 10 }}>
            <button className="btn ghost" disabled={busy} onClick={() => setConfirming(false)}>{t('Edit')}</button>
            <button className="btn primary" disabled={busy} onClick={confirm}>{busy ? '…' : t('Confirm')}</button>
          </div>
        </div>
      ) : (
        <form
          className="row wrap"
          style={{ marginTop: 14 }}
          onSubmit={(e) => {
            e.preventDefault();
            if (name.trim()) setConfirming(true);
          }}
        >
          <input className="input grow" placeholder="e.g. Nani, Papa, Riya" value={name} onChange={(e) => setName(e.target.value)} aria-label="Your name" required maxLength={40} />
          <button className="btn primary" disabled={!name.trim()}>{inviteCode ? 'Join' : 'Continue'}</button>
        </form>
      )}
      <button type="button" className="btn ghost sm signin-alt" onClick={() => signInWithGoogle().catch((err) => toast(err.message, 'x'))}>
        <Icon name="google" /> Or use a Google account to keep circles on all your devices
      </button>
    </section>
  );
}

/** For family who were sent a group code or link instead of a QR. */
function JoinWithCode({ onDone }: { onDone: (id: string) => void }) {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  return (
    <div className="card card-pad create-join">
      <span className="eyebrow">Got a group code?</span>
      <form
        className="row"
        style={{ gap: 8, marginTop: 8 }}
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            onDone(await joinCircle(value));
            setValue('');
            toast('You joined the group');
          } catch (err) {
            toast(err instanceof Error ? err.message : 'Something went wrong', 'x');
          } finally {
            setBusy(false);
          }
        }}
      >
        <input className="input grow mono code-input" placeholder="ABC123" value={value} maxLength={6} onChange={(e) => setValue(e.target.value.toUpperCase())} aria-label="Group code" />
        <button className="btn primary sm" disabled={busy || value.length !== 6}>Join</button>
      </form>
    </div>
  );
}

/** The group at a glance: who is in it, and the three things you can do with it. */
function GroupCard({ circle, uid, myName }: { circle: Circle; uid: string; myName: string }) {
  const t = useT();
  const [open, setOpen] = useState<'' | 'add' | 'link'>('');
  const mine = circle.memberHandles?.[uid];
  // Show my Chaukas ID to the group. Best effort: the group works the same without it.
  useEffect(() => {
    if (mine || !myName || myName === 'Guest') return;
    ensureMyHandle(myName)
      .then((h) => setMyHandleIn(circle.id, h))
      .catch(() => undefined);
  }, [circle.id, mine, myName]);
  const waiting = (circle.invited ?? []).map((u) => circle.invitedNames?.[u] ?? 'Someone');
  return (
    <section className="card card-pad group-card">
      <div className="circle-tab on">
        <span className="grow">
          <b className="group-name">{circle.name}</b>
          <span className="xs subtle">{circle.members.length} member{circle.members.length === 1 ? '' : 's'}</span>
        </span>
      </div>
      <ul className="group-people">
        {circle.members.map((m) => (
          <li key={m}>
            <Avatar name={circle.memberNames?.[m] ?? '?'} size={36} />
            <span>{m === uid ? t('You') : circle.memberNames?.[m] ?? '?'}</span>
            {circle.memberHandles?.[m] && <span className="xs subtle mono member-id">{circle.memberHandles[m]}</span>}
          </li>
        ))}
      </ul>
      {waiting.length > 0 && <p className="xs subtle">Waiting for: {waiting.join(', ')}.</p>}
      <div className="group-actions">
        <button className={`btn sm ${open === 'add' ? 'primary' : 'soft'}`} onClick={() => setOpen(open === 'add' ? '' : 'add')}>
          <Icon name="plus" /> {t('Add person')}
        </button>
        <button className={`btn sm ${open === 'link' ? 'primary' : 'ghost'}`} onClick={() => setOpen(open === 'link' ? '' : 'link')}>
          <Icon name="message" /> {t('Invite link')}
        </button>
        <button
          className="btn sm ghost group-leave"
          onClick={() => {
            if (!window.confirm(`Leave ${circle.name}? You will stop seeing its scam warnings.`)) return;
            leaveCircle(circle).then(
              () => toast('You left the group'),
              () => toast('Could not update the group', 'x'),
            );
          }}
        >
          <Icon name="logout" /> {t('Leave group')}
        </button>
      </div>
      {open === 'add' && <AddById circle={circle} bare />}
      {open === 'link' && (
        <div className="group-link">
          <p className="xs subtle">For family who don't have Chaukas yet. One tap on the link opens it and joins {circle.name}. Group code <b className="mono">{circle.id}</b>.</p>
          <div className="row wrap" style={{ gap: 8 }}>
            <a className="btn whatsapp sm" href={whatsappLink(inviteText(circle.id, circle.name))} target="_blank" rel="noreferrer">
              <Icon name="message" /> WhatsApp
            </a>
            <a className="btn soft sm" href={smsLink(inviteText(circle.id, circle.name))}>
              <Icon name="phone" /> SMS
            </a>
            <button className="btn ghost sm" onClick={() => copyText(inviteUrl(circle.id), 'Invite link copied')}>
              <Icon name="copy" /> Copy link
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function CircleFeed({ circle, uid }: { circle: Circle; uid: string }) {
  const t = useT();
  const [alerts, setAlerts] = useState<CircleAlert[] | null>(null);
  useEffect(() => {
    setAlerts(null);
    return listenAlerts(circle.id, setAlerts);
  }, [circle.id]);

  return (
    <>
      {alerts === null ? (
        <div className="skeleton" style={{ height: 120 }} />
      ) : alerts.length === 0 ? (
        <div className="card empty">
          <h3>{t('All quiet')}</h3>
          <p className="small">When someone in {circle.name} checks a message that looks like a scam, it shows up here by itself. Only risky messages (65 or more) are shared, and each warning disappears after 3 days.</p>
        </div>
      ) : (
        <ul className="feed">
          {alerts.map((a, i) => (
            <li key={a.id} className="card feed-item" style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}>
              <div className="row between">
                <div className="row">
                  <Avatar name={a.byName} />
                  <div>
                    <b className="small">{a.byUid === uid ? 'You' : a.byName}</b>
                    <div className="xs subtle">{timeAgo(a.createdAt)}</div>
                  </div>
                </div>
                <span className={`verdict-chip v-${a.verdict}`}>
                  <span className={`dot ${a.verdict}`} /> {VERDICT_LABEL[a.verdict as Verdict] ?? a.verdict} · {a.riskScore}
                </span>
              </div>
              {a.note && <p className="feed-note">{a.note}</p>}
              <blockquote className="feed-quote">{a.excerpt}</blockquote>
              <p className="small muted">
                <b>{CATEGORY_LABEL[a.category] ?? a.category}.</b> {a.summary}
              </p>
              <div className="row wrap" style={{ gap: 6, marginTop: 10 }}>
                {REACTIONS.map((r) => {
                  const count = Object.values(a.reactions).filter((v) => v === r.key).length;
                  const mine = a.reactions[uid] === r.key;
                  return (
                    <button key={r.key} className={`chip react ${mine ? 'on' : ''}`} onClick={() => reactToAlert(circle.id, a.id, r.key).catch(() => toast('Could not react', 'x'))}>
                      <Icon name={r.icon} /> {t(r.label)}
                      {count > 0 && <span className="react-n">{count}</span>}
                    </button>
                  );
                })}
                <button className="chip react" onClick={() => forwardAlert(alertForwardText(a))} title="Forward this warning on WhatsApp or anywhere">
                  <Icon name="share" /> Share
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function CirclePitch() {
  return (
    <section className="pitch">
      {[
        ['1', 'Scan or type an ID', 'Scan a family member’s Chaukas QR, or type their ID, like riya-4821.'],
        ['2', 'Tap Keep', 'Your family group is made with them. They tap Keep on their phone too.'],
        ['3', 'Warn each other', 'Scam warnings reach everyone by themselves, with personal details removed. Safe messages stay private.'],
      ].map(([n, t, b]) => (
        <div key={n} className="pitch-step">
          <span className="pitch-n display">{n}</span>
          <b>{t}</b>
          <p className="small muted">{b}</p>
        </div>
      ))}
    </section>
  );
}

/** Your personal ID and QR. Family scan it or type the ID to add you to their group. */
function MyId({ name }: { name: string }) {
  const t = useT();
  const [handle, setHandle] = useState('');
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    // Wait for the name from "What should your family call you?" before making the ID from it.
    if (!name || name === 'Guest') return;
    ensureMyHandle(name).then(setHandle, () => setFailed(true));
  }, [name]);
  if (failed) return null;
  const url = handle ? personUrl(handle) : '';
  const share = async () => {
    const text = `Add me to your Chaukas family group. My Chaukas ID is ${handle}: ${url}`;
    if (navigator.share) {
      try {
        await navigator.share({ text });
        return;
      } catch {
        /* cancelled: fall back to copy */
      }
    }
    copyText(text, 'Your ID is copied');
  };
  return (
    <div className="card card-pad my-id">
      <span className="eyebrow">{t('Your Chaukas ID')}</span>
      {handle ? (
        <>
          <QrCode text={url} label={`QR code for Chaukas ID ${handle}`} />
          <div className="my-id-handle mono">{handle}</div>
          <p className="xs subtle">Family scan this with their phone camera, or type your ID. You get a popup to keep their group or not.</p>
          <div className="row wrap" style={{ gap: 6, justifyContent: 'center' }}>
            <button className="btn soft sm" onClick={share}><Icon name="share" /> {t('Share')}</button>
            <button className="btn ghost sm" onClick={() => copyText(handle, 'ID copied')}><Icon name="copy" /> {t('Copy ID')}</button>
          </div>
        </>
      ) : (
        <div className="skeleton" style={{ height: 148, width: 148, margin: '8px auto' }} />
      )}
    </div>
  );
}

/** Scan someone's QR or type their ID. Either way a popup asks whether to keep them. */
function AddById({ circle, bare }: { circle?: Circle; bare?: boolean }) {
  const t = useT();
  const [value, setValue] = useState('');
  const [asking, setAsking] = useState('');
  const scanRef = useRef<HTMLInputElement>(null);
  const scan = async (file?: File) => {
    if (!file) return;
    const codes = await decodeQr(file).catch(() => [] as string[]);
    const handle = codes.map(normalizeHandle).find(Boolean);
    if (handle) setAsking(handle);
    else toast('No Chaukas ID found in that picture. Try again closer to the QR.', 'x');
  };
  const waiting = (circle?.invited ?? []).map((u) => circle?.invitedNames?.[u] ?? 'Someone');
  return (
    <div className={bare ? 'add-id bare' : `card card-pad add-id${circle ? '' : ' tint-lilac'}`}>
      <span className="eyebrow">{circle ? `Add someone to ${circle.name}` : t('Make your family group')}</span>
      <form className="row add-id-form" style={{ gap: 8, marginTop: 8 }} onSubmit={(e) => { e.preventDefault(); setAsking(normalizeHandle(value)); }}>
        <input className="input grow mono" placeholder="Their ID, e.g. riya-4821" value={value} onChange={(e) => setValue(e.target.value)} aria-label="Their Chaukas ID" autoCapitalize="none" autoCorrect="off" spellCheck={false} />
        <button className="btn primary sm" disabled={!normalizeHandle(value)}>{t('Add')}</button>
        <button type="button" className="btn soft sm" onClick={() => scanRef.current?.click()} title="Take a photo of their Chaukas QR">
          <Icon name="qr" /> {t('Scan QR')}
        </button>
        <input ref={scanRef} type="file" accept="image/*" capture="environment" hidden onChange={(e) => { void scan(e.target.files?.[0]); e.target.value = ''; }} />
      </form>
      <p className="xs subtle" style={{ marginTop: 8 }}>
        {circle ? 'You get a popup to keep them or not.' : 'Scan a family member’s QR or type their ID, then tap Keep. Your group is made with them.'}
        {!bare && waiting.length > 0 && <> Waiting for: {waiting.join(', ')}.</>}
      </p>
      {asking && (
        <KeepDialog
          handle={asking}
          group={circle}
          onClose={(id) => {
            if (id) setValue('');
            setAsking('');
          }}
        />
      )}
    </div>
  );
}

/** The popup after scanning a QR, typing an ID or opening someone's QR link: Keep, or not. */
function KeepDialog({ handle, group, onClose }: { handle: string; group?: Circle; onClose: (groupId?: string) => void }) {
  const t = useT();
  const [person, setPerson] = useState<{ name: string; uid: string } | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    findPerson(handle).then(setPerson, () => setPerson(null));
  }, [handle]);
  const close = () => onClose();
  const isMe = person?.uid === getUid();
  const already = !!person && !!group && group.members.includes(person.uid);
  const keep = async () => {
    setBusy(true);
    try {
      const r = await keepPerson(handle, group);
      toast(`${r.name} is added to ${r.group}. They just need to tap Keep.`);
      onClose(r.groupId);
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not add them', 'x');
      setBusy(false);
    }
  };
  return (
    <Modal onClose={close} label="Keep this person?">
      {person === undefined ? (
        <div className="skeleton" style={{ height: 120 }} />
      ) : person === null ? (
        <div className="keep">
          <h2 className="keep-title">No one has this ID</h2>
          <p className="muted keep-body"><b className="mono">{handle}</b> is not a Chaukas ID yet. Ask them to check it in Family Circle on their phone.</p>
          <div className="keep-actions"><button className="btn primary" onClick={close}>OK</button></div>
        </div>
      ) : (
        <div className="keep">
          <Avatar name={person.name} size={64} />
          <h2 className="keep-title">{isMe ? 'This is your own ID' : already ? `${person.name} is already in ${group!.name}` : `Keep ${person.name}?`}</h2>
          <div className="xs subtle mono">{handle}</div>
          {!isMe && !already && (
            <p className="muted keep-body">
              {group ? <>They join <b>{group.name}</b>.</> : <>This makes your family group with {person.name}.</>} They get the same popup on their phone.
              Only scam warnings are shared in the group. Safe messages are not.
            </p>
          )}
          <div className="keep-actions">
            {isMe || already ? (
              <button className="btn primary" onClick={close}>OK</button>
            ) : (
              <>
                <button className="btn ghost" onClick={close} disabled={busy}>{t("Don't keep")}</button>
                <button className="btn primary" onClick={keep} disabled={busy}>{busy ? 'Adding…' : t('Keep')}</button>
              </>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}

/** Someone kept me in their group: the same popup, on my side. */
function InvitePopup({ invite, groups, onDone }: { invite: Invite; groups: Circle[]; onDone: (stayed: boolean) => void }) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const full = groups.filter((g) => g.id !== invite.id).length >= MAX_GROUPS;
  const act = (stay: boolean) => {
    setBusy(true);
    (stay ? stayInCircle(invite.id) : leaveInvite(invite.id)).then(
      () => {
        toast(stay ? `You're in ${invite.name}` : 'Okay, not kept');
        onDone(stay);
      },
      (err) => {
        toast(err instanceof Error && /groups at a time/.test(err.message) ? err.message : 'Could not update the group', 'x');
        setBusy(false);
      },
    );
  };
  return (
    <Modal onClose={() => onDone(false)} label="Join this family group?">
      <div className="keep">
        <span className="keep-ic"><Icon name="users" /></span>
        <h2 className="keep-title">{invite.by} wants to keep you in {invite.name}</h2>
        <p className="muted keep-body">
          {invite.members} member{invite.members === 1 ? '' : 's'}. Scam warnings are shared in the group by themselves. Safe messages are not.
          {full
            ? <> You are already in {MAX_GROUPS} groups ({groups.map((g) => g.name).join(' and ')}). Leave one first to keep this one. You can decide later: it waits for you in Family Circle.</>
            : groups.length > 0 && <> You stay in <b>{groups[0].name}</b> too. You can be in up to {MAX_GROUPS} groups.</>}
        </p>
        <div className="keep-actions">
          <button className="btn ghost" disabled={busy} onClick={() => act(false)}>{t("Don't keep")}</button>
          {full ? (
            <button className="btn primary" onClick={() => onDone(false)}>{t('Decide later')}</button>
          ) : (
            <button className="btn primary" disabled={busy} onClick={() => act(true)}>{t('Keep')}</button>
          )}
        </div>
      </div>
    </Modal>
  );
}

/** Groups that want to keep me, waiting for my answer. Keep or Don't keep, any time later. */
function WaitingInvites({ invites, groups, onKept }: { invites: Invite[]; groups: Circle[]; onKept: (id: string) => void }) {
  const t = useT();
  const [busy, setBusy] = useState('');
  if (!invites.length) return null;
  const full = groups.length >= MAX_GROUPS;
  const act = (inv: Invite, stay: boolean) => {
    setBusy(inv.id);
    (stay ? stayInCircle(inv.id) : leaveInvite(inv.id)).then(
      () => {
        toast(stay ? `You're in ${inv.name}` : 'Okay, not kept');
        if (stay) onKept(inv.id);
        setBusy('');
      },
      (err) => {
        toast(err instanceof Error && /groups at a time/.test(err.message) ? err.message : 'Could not update the group', 'x');
        setBusy('');
      },
    );
  };
  return (
    <section className="card card-pad waiting-invites">
      <span className="eyebrow">{t('Waiting for you')}</span>
      <ul>
        {invites.map((inv) => (
          <li key={inv.id}>
            <span className="grow">
              <b className="small">{inv.name}</b>
              <span className="xs subtle"> · {inv.by} wants to keep you</span>
            </span>
            <button className="btn ghost sm" disabled={busy === inv.id} onClick={() => act(inv, false)}>{t("Don't keep")}</button>
            <button className="btn primary sm" disabled={busy === inv.id || full} onClick={() => act(inv, true)}>{t('Keep')}</button>
          </li>
        ))}
      </ul>
      {full && <p className="xs subtle">You are in {MAX_GROUPS} groups. Leave one to keep a new one.</p>}
    </section>
  );
}

const getUid = () => getFirebase()?.auth.currentUser?.uid ?? '';

/** Forwards a group warning with the system share sheet, or WhatsApp where there is none. */
async function forwardAlert(text: string) {
  if (navigator.share) {
    try {
      await navigator.share({ text });
      return;
    } catch {
      return; // cancelled
    }
  }
  window.open(whatsappLink(text), '_blank', 'noopener');
}

/** Share every scam or suspicious check with my groups, on by default. */
function AutoShareToggle() {
  const on = settingsStore.use().autoShareCircle;
  return (
    <button className={`card card-pad notify-toggle ${on ? 'on' : ''}`} onClick={() => settingsStore.set({ autoShareCircle: !on })} aria-pressed={on}>
      <Icon name="share" />
      <span className="grow">
        <b>{on ? 'Auto-share scams: on' : 'Auto-share scams: off'}</b>
        <span className="xs subtle">{on ? 'Messages you check that score 65 or more go to your group, with personal details removed. They disappear after 3 days. Tap to turn off.' : 'Tap to share every scam you check with your groups automatically.'}</span>
      </span>
    </button>
  );
}
