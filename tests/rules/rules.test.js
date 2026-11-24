// Security rule tests for firestore.rules, run against the Firestore emulator.
import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { arrayRemove, arrayUnion, deleteDoc, deleteField, doc, getDoc, getDocs, collection, query, where, serverTimestamp, setDoc, updateDoc, addDoc, Timestamp } from 'firebase/firestore';

let env;
const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();

const check = (over = {}) => ({ id: 'c1', verdict: 'scam', riskScore: 88, category: 'phishing', summary: 'Fake bank text', excerpt: 'Your [PHONE_1] account…', source: 'on_device', createdAt: 1, syncedAt: serverTimestamp(), ...over });
const alert = (uid, over = {}) => ({ byUid: uid, byName: 'Asha', verdict: 'scam', riskScore: 90, category: 'phishing', summary: 's', excerpt: 'e', note: '', createdAt: serverTimestamp(), reactions: {}, ...over });

beforeAll(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-chaukas', firestore: { rules: readFileSync('../../firestore.rules', 'utf8') } });
});
afterAll(() => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  // Seed a circle owned by "owner" with member "mem", and one alert by "mem".
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await setDoc(doc(f, 'circles/ABC234'), { name: 'Family', ownerUid: 'owner', members: ['owner', 'mem'], memberNames: { owner: 'Ma', mem: 'Asha' }, createdAt: 1 });
    await setDoc(doc(f, 'circles/ABC234/alerts/a1'), alert('mem', { createdAt: 1 }));
  });
});

describe('private history', () => {
  it('lets me write and read my own checks', async () => {
    await assertSucceeds(setDoc(doc(db('me'), 'users/me/checks/c1'), check()));
    await assertSucceeds(getDoc(doc(db('me'), 'users/me/checks/c1')));
  });
  it('blocks other people and signed-out users', async () => {
    await assertFails(setDoc(doc(db('eve'), 'users/me/checks/c1'), check()));
    await assertFails(getDoc(doc(db('eve'), 'users/me/checks/c1')));
    await assertFails(getDoc(doc(db(null), 'users/me/checks/c1')));
  });
  it('rejects full messages, extra fields, bad verdicts and scores', async () => {
    await assertFails(setDoc(doc(db('me'), 'users/me/checks/c1'), check({ excerpt: 'x'.repeat(201) })));
    await assertFails(setDoc(doc(db('me'), 'users/me/checks/c1'), check({ fullMessage: 'secret' })));
    await assertFails(setDoc(doc(db('me'), 'users/me/checks/c1'), check({ verdict: 'fine' })));
    await assertFails(setDoc(doc(db('me'), 'users/me/checks/c1'), check({ riskScore: 101 })));
    await assertFails(setDoc(doc(db('me'), 'users/me/checks/c1'), check({ id: 'other' })));
  });
});

describe('circles', () => {
  it('only members can read a circle', async () => {
    await assertSucceeds(getDoc(doc(db('mem'), 'circles/ABC234')));
    await assertFails(getDoc(doc(db('eve'), 'circles/ABC234')));
    await assertSucceeds(getDocs(query(collection(db('mem'), 'circles'), where('members', 'array-contains', 'mem'))));
  });
  it('creates a circle only for yourself with a valid code', async () => {
    const good = { name: 'Us', ownerUid: 'me', members: ['me'], memberNames: { me: 'Me' }, createdAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(db('me'), 'circles/XYZ789'), good));
    await assertFails(setDoc(doc(db('me'), 'circles/XYZ788'), { ...good, members: ['me', 'eve'] }));
    await assertFails(setDoc(doc(db('me'), 'circles/XYZ787'), { ...good, ownerUid: 'eve' }));
    await assertFails(setDoc(doc(db('me'), 'circles/abc'), good));
    await assertFails(setDoc(doc(db('me'), 'circles/XYZ786'), { ...good, memberNames: { me: 'Me', eve: 'Eve' } }));
    await assertFails(setDoc(doc(db(null), 'circles/XYZ785'), good));
  });
  it('cannot overwrite an existing circle (code collision)', async () => {
    await assertFails(setDoc(doc(db('eve'), 'circles/ABC234'), { name: 'Mine', ownerUid: 'eve', members: ['eve'], memberNames: { eve: 'Eve' } }));
  });
  it('joins by adding only yourself', async () => {
    await assertSucceeds(updateDoc(doc(db('new'), 'circles/ABC234'), { members: arrayUnion('new'), 'memberNames.new': 'Ravi' }));
    await assertFails(updateDoc(doc(db('eve'), 'circles/ABC234'), { members: arrayUnion('eve', 'mallory'), 'memberNames.eve': 'Eve' }));
    await assertFails(updateDoc(doc(db('eve'), 'circles/ABC234'), { members: arrayUnion('eve'), 'memberNames.owner': 'Hacked' }));
    await assertFails(updateDoc(doc(db('eve'), 'circles/ABC234'), { members: arrayUnion('eve'), 'memberNames.eve': 'Eve', name: 'Mine' }));
    await assertFails(updateDoc(doc(db('eve'), 'circles/ABC234'), { members: ['eve'], 'memberNames.eve': 'Eve' }));
  });
  it('leaves by removing only yourself, owner included', async () => {
    await assertFails(updateDoc(doc(db('mem'), 'circles/ABC234'), { members: arrayRemove('owner'), 'memberNames.owner': deleteField() }));
    await assertFails(updateDoc(doc(db('owner'), 'circles/ABC234'), { members: arrayRemove('mem'), 'memberNames.mem': deleteField() }));
    await assertSucceeds(updateDoc(doc(db('mem'), 'circles/ABC234'), { members: arrayRemove('mem'), 'memberNames.mem': deleteField() }));
    await assertSucceeds(updateDoc(doc(db('owner'), 'circles/ABC234'), { members: arrayRemove('owner'), 'memberNames.owner': deleteField() }));
  });
  it('only the owner can delete a circle', async () => {
    await assertFails(deleteDoc(doc(db('mem'), 'circles/ABC234')));
    await assertSucceeds(deleteDoc(doc(db('owner'), 'circles/ABC234')));
  });
  it('a wrong code fails cleanly', async () => {
    await assertFails(updateDoc(doc(db('new'), 'circles/NOPE22'), { members: arrayUnion('new'), 'memberNames.new': 'Ravi' }));
  });
});

describe('alerts', () => {
  const alerts = (uid) => collection(db(uid), 'circles/ABC234/alerts');
  it('members can read and post as themselves', async () => {
    await assertSucceeds(getDocs(alerts('owner')));
    await assertSucceeds(addDoc(alerts('owner'), alert('owner')));
  });
  it('outsiders cannot read or post', async () => {
    await assertFails(getDocs(alerts('eve')));
    await assertFails(addDoc(alerts('eve'), alert('eve')));
  });
  it('blocks impersonation, pre-filled reactions, extra fields and oversized text', async () => {
    await assertFails(addDoc(alerts('owner'), alert('mem')));
    await assertFails(addDoc(alerts('owner'), alert('owner', { reactions: { mem: 'scam' } })));
    await assertFails(addDoc(alerts('owner'), alert('owner', { phone: '+91 98765 43210' })));
    await assertFails(addDoc(alerts('owner'), alert('owner', { excerpt: 'x'.repeat(241) })));
    await assertFails(addDoc(alerts('owner'), alert('owner', { createdAt: 5 })));
  });
  it('warnings expire within a few days, never later', async () => {
    const day = 24 * 60 * 60 * 1000;
    await assertSucceeds(addDoc(alerts('owner'), alert('owner', { expireAt: Timestamp.fromMillis(Date.now() + 3 * day) })));
    await assertFails(addDoc(alerts('owner'), alert('owner', { expireAt: Timestamp.fromMillis(Date.now() + 30 * day) })));
    await assertFails(addDoc(alerts('owner'), alert('owner', { expireAt: Timestamp.fromMillis(Date.now() - day) })));
    await assertFails(addDoc(alerts('owner'), alert('owner', { expireAt: 'soon' })));
  });
  it('members set or clear only their own reaction, from the allowed list', async () => {
    const a = doc(db('owner'), 'circles/ABC234/alerts/a1');
    await assertSucceeds(updateDoc(a, { 'reactions.owner': 'call' }));
    await assertSucceeds(updateDoc(a, { 'reactions.owner': 'got' }));
    await assertSucceeds(updateDoc(a, { 'reactions.owner': deleteField() }));
    await assertFails(updateDoc(a, { 'reactions.mem': 'love' }));
    await assertFails(updateDoc(a, { 'reactions.owner': 'evil' }));
    await assertFails(updateDoc(a, { verdict: 'safe' }));
    await assertFails(updateDoc(doc(db('eve'), 'circles/ABC234/alerts/a1'), { 'reactions.eve': 'seen' }));
  });
  it('author or circle owner can delete an alert', async () => {
    await assertFails(deleteDoc(doc(db('eve'), 'circles/ABC234/alerts/a1')));
    await assertSucceeds(deleteDoc(doc(db('owner'), 'circles/ABC234/alerts/a1')));
  });
});

describe('Chaukas IDs and invites', () => {
  const circle = (uid) => doc(db(uid), 'circles/ABC234');
  const invite = (uids, by = 'Asha') => ({
    invited: arrayUnion(...uids),
    ...Object.fromEntries(uids.flatMap((u) => [[`invitedNames.${u}`, u], [`invitedBy.${u}`, by]])),
  });

  it('claims an ID once, only for yourself, and lets others look it up but not list', async () => {
    await assertSucceeds(setDoc(doc(db('riya'), 'handles/riya-4821'), { uid: 'riya', name: 'Riya' }));
    await assertSucceeds(getDoc(doc(db('mem'), 'handles/riya-4821')));
    await assertFails(getDocs(collection(db('mem'), 'handles')));
    await assertFails(setDoc(doc(db('eve'), 'handles/riya-4821'), { uid: 'eve', name: 'Eve' }));
    await assertFails(setDoc(doc(db('eve'), 'handles/eve-0001'), { uid: 'riya', name: 'Riya' }));
    await assertFails(setDoc(doc(db('eve'), 'handles/Bad Handle'), { uid: 'eve', name: 'Eve' }));
    await assertFails(getDoc(doc(db(null), 'handles/riya-4821')));
  });

  it('creates a circle with an empty invite list, but not a pre-filled one', async () => {
    const base = { name: 'Fam', ownerUid: 'riya', members: ['riya'], memberNames: { riya: 'Riya' }, createdAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(db('riya'), 'circles/QWE234'), { ...base, invited: [], invitedNames: {}, invitedBy: {} }));
    await assertFails(setDoc(doc(db('riya'), 'circles/QWE235'), { ...base, invited: ['zed'], invitedNames: { zed: 'Zed' }, invitedBy: {} }));
  });

  it('keeps profiles private', async () => {
    await assertSucceeds(setDoc(doc(db('riya'), 'profiles/riya'), { handle: 'riya-4821' }));
    await assertFails(getDoc(doc(db('eve'), 'profiles/riya')));
    await assertFails(setDoc(doc(db('riya'), 'profiles/riya'), { handle: 'riya-4821', extra: 1 }));
  });

  it('members invite non-members; outsiders cannot', async () => {
    await assertSucceeds(updateDoc(circle('mem'), invite(['riya'])));
    await assertFails(updateDoc(circle('eve'), invite(['zed'])));
    await assertFails(updateDoc(circle('mem'), invite(['owner'])));
    await assertFails(updateDoc(circle('mem'), invite(['a', 'b', 'c', 'd', 'e', 'f'])));
    await assertFails(updateDoc(circle('mem'), { ...invite(['zed']), name: 'Hacked' }));
  });

  it('an invite never makes someone a member by itself', async () => {
    await assertSucceeds(updateDoc(circle('mem'), invite(['riya'])));
    await assertFails(updateDoc(circle('mem'), { members: arrayUnion('riya'), 'memberNames.riya': 'Riya' }));
  });

  it('the invited person can read the circle, then Stay or Leave', async () => {
    await assertSucceeds(updateDoc(circle('mem'), invite(['riya', 'zed'])));
    await assertSucceeds(getDoc(circle('riya')));
    await assertSucceeds(getDocs(query(collection(db('riya'), 'circles'), where('invited', 'array-contains', 'riya'))));
    // Stay
    await assertSucceeds(updateDoc(circle('riya'), { members: arrayUnion('riya'), 'memberNames.riya': 'Riya', invited: arrayRemove('riya'), 'invitedNames.riya': deleteField(), 'invitedBy.riya': deleteField() }));
    // Leave the invite (decline)
    await assertSucceeds(updateDoc(circle('zed'), { invited: arrayRemove('zed'), 'invitedNames.zed': deleteField(), 'invitedBy.zed': deleteField() }));
    await assertFails(getDoc(circle('zed')));
  });

  it('members show only their own, real Chaukas ID in the group', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'handles/asha-1234'), { uid: 'mem', name: 'Asha' }));
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'handles/ma-5555'), { uid: 'owner', name: 'Ma' }));
    await assertSucceeds(updateDoc(circle('mem'), { 'memberHandles.mem': 'asha-1234' }));
    await assertFails(updateDoc(circle('mem'), { 'memberHandles.owner': 'ma-5555' }));
    await assertFails(updateDoc(circle('mem'), { 'memberHandles.mem': 'ma-5555' }));
    await assertFails(updateDoc(circle('mem'), { 'memberHandles.mem': 'Not An ID' }));
    // Leaving takes the ID out too.
    await assertSucceeds(updateDoc(circle('mem'), { members: arrayRemove('mem'), 'memberNames.mem': deleteField(), 'memberHandles.mem': deleteField() }));
  });

  it("nobody can remove someone else's invite or accept it for them", async () => {
    await assertSucceeds(updateDoc(circle('mem'), invite(['riya', 'zed'])));
    await assertFails(updateDoc(circle('zed'), { invited: arrayRemove('riya'), 'invitedNames.riya': deleteField(), 'invitedBy.riya': deleteField() }));
    await assertFails(updateDoc(circle('zed'), { members: arrayUnion('riya'), 'memberNames.riya': 'Riya' }));
  });
});

describe('everything else', () => {
  it('is denied', async () => {
    await assertFails(setDoc(doc(db('me'), 'secrets/x'), { a: 1 }));
    await assertFails(getDoc(doc(db('me'), 'users/me')));
  });
});
