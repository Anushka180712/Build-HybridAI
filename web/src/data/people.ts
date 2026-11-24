/**
 * Personal Chaukas IDs and circle invites. Everyone gets an ID like `riya-4821` with a QR code.
 * A circle member can add someone by scanning or typing it, which puts them on the circle's
 * invite list. The invited person then chooses Stay (become a member) or Leave. Firestore rules
 * make sure people can only invite into circles they belong to, and only accept or decline
 * their own invite.
 */
import { arrayRemove, arrayUnion, collection, deleteDoc, deleteField, doc, getDoc, onSnapshot, query, setDoc, updateDoc, where } from 'firebase/firestore';
import { getFirebase } from '../firebase/app';
import { log } from '../lib/log';
import { handleFitsName, makeHandle, normalizeHandle } from '../lib/invite';
import { checkGroupLimit, createCircle, type Circle } from './circle';

export interface Invite {
  id: string;
  name: string;
  by: string;
  members: number;
}

function need() {
  const fb = getFirebase();
  const user = fb?.auth.currentUser;
  if (!fb || !user) throw new Error('Sign in to use Family Circle.');
  return { fb, user };
}

const myName = () => (getFirebase()?.auth.currentUser?.displayName || 'Someone').slice(0, 60);
const cacheKey = (uid: string) => `chaukas.handle.${uid}`;

/**
 * Returns this person's ID, made from the name they gave plus four digits (`papa-4821`). Called
 * with that name, so it never uses a placeholder while sign-in is still saving it. If the name
 * changes, a new matching ID replaces the old one (groups are kept: they use the account, not the ID).
 */
export async function ensureMyHandle(name = myName()): Promise<string> {
  const { fb, user } = need();
  try {
    const cached = localStorage.getItem(cacheKey(user.uid));
    if (cached && handleFitsName(cached, name)) return cached;
  } catch {
    /* storage blocked: read from Firestore */
  }
  const profile = await getDoc(doc(fb.db, 'profiles', user.uid));
  const old = profile.exists() ? (profile.data().handle as string) : '';
  let handle = old && handleFitsName(old, name) ? old : '';
  if (!handle) {
    // A taken ID fails the create rule (the document exists), so try a few others.
    for (let i = 0; i < 6 && !handle; i++) {
      const h = makeHandle(name);
      try {
        await setDoc(doc(fb.db, 'handles', h), { uid: user.uid, name: name.slice(0, 60) });
        handle = h;
      } catch {
        /* taken, try another */
      }
    }
    if (!handle) throw new Error('Could not create your Chaukas ID. Try again.');
    await setDoc(doc(fb.db, 'profiles', user.uid), { handle });
    // Free the old ID so it no longer points at this person.
    if (old) await deleteDoc(doc(fb.db, 'handles', old)).catch(() => undefined);
  }
  try {
    localStorage.setItem(cacheKey(user.uid), handle);
  } catch {
    /* fine without a cache */
  }
  return handle;
}

/** Looks up an ID. Returns null when nobody has it. */
export async function findPerson(input: string): Promise<{ uid: string; name: string; handle: string } | null> {
  const { fb } = need();
  const handle = normalizeHandle(input);
  if (!handle) return null;
  const snap = await getDoc(doc(fb.db, 'handles', handle));
  if (!snap.exists()) return null;
  const v = snap.data() as { uid: string; name: string };
  return { uid: v.uid, name: v.name, handle };
}

/** Adds someone to a circle's invite list by their ID. They choose Stay or Leave. */
export async function addByHandle(circle: Circle, input: string): Promise<string> {
  const { fb, user } = need();
  const person = await findPerson(input);
  if (!person) throw new Error('No one has that Chaukas ID. Check it and try again.');
  if (person.uid === user.uid) throw new Error("That's your own ID.");
  if (circle.members.includes(person.uid)) throw new Error(`${person.name} is already in ${circle.name}.`);
  if (circle.invited?.includes(person.uid)) throw new Error(`${person.name} is already invited. They just need to tap Stay.`);
  await updateDoc(doc(fb.db, 'circles', circle.id), {
    invited: arrayUnion(person.uid),
    [`invitedNames.${person.uid}`]: person.name.slice(0, 60),
    [`invitedBy.${person.uid}`]: myName(),
  });
  return person.name;
}

/** Circles that have invited me and are waiting for Stay or Leave. */
export function listenMyInvites(uid: string, cb: (invites: Invite[]) => void) {
  const fb = getFirebase();
  if (!fb) return () => undefined;
  const q = query(collection(fb.db, 'circles'), where('invited', 'array-contains', uid));
  return onSnapshot(
    q,
    (snap) =>
      cb(
        snap.docs.map((d) => {
          const v = d.data() as Circle & { invitedBy?: Record<string, string> };
          return { id: d.id, name: v.name, by: v.invitedBy?.[uid] ?? 'Someone', members: v.members?.length ?? 0 };
        }),
      ),
    (err) => { log.warn('circle', 'invites listener stopped', err); cb([]); },
  );
}

const clearMyInvite = (uid: string) => ({
  invited: arrayRemove(uid),
  [`invitedNames.${uid}`]: deleteField(),
  [`invitedBy.${uid}`]: deleteField(),
});

/** Accepts an invite: become a member and take the invite off the list. At most MAX_GROUPS groups. */
export async function stayInCircle(circleId: string) {
  const { fb, user } = need();
  await checkGroupLimit(circleId);
  await updateDoc(doc(fb.db, 'circles', circleId), {
    members: arrayUnion(user.uid),
    [`memberNames.${user.uid}`]: myName(),
    ...clearMyInvite(user.uid),
  });
}

/**
 * "Keep" after scanning someone's QR or typing their ID: they join my family group. With no
 * group yet, one is made for me first. They still choose Keep or not on their own phone.
 */
export async function keepPerson(input: string, myGroup?: Circle): Promise<{ name: string; group: string; groupId: string }> {
  const { user } = need();
  let group = myGroup;
  if (!group) {
    const name = `${myName().split(' ')[0]}'s family`.slice(0, 40);
    const id = await createCircle(name);
    group = { id, name, ownerUid: user.uid, members: [user.uid], memberNames: {}, invited: [] };
  }
  const name = await addByHandle(group, input);
  return { name, group: group.name, groupId: group.id };
}

/** Declines an invite. Nothing else in the circle changes. */
export async function leaveInvite(circleId: string) {
  const { fb, user } = need();
  await updateDoc(doc(fb.db, 'circles', circleId), clearMyInvite(user.uid));
}
