// End-to-end security test of the Super Admin Owner account against the Auth, Firestore
// and Functions emulators: provisioning script, custom claims, server-side (403) owner
// protection, audit log, password recovery and session revocation.
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { promisify } from 'node:util';
import { initializeApp, deleteApp } from 'firebase/app';
import {
  getAuth, connectAuthEmulator, signInWithEmailAndPassword, sendPasswordResetEmail, confirmPasswordReset, signOut,
} from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, getDoc, getDocs, updateDoc, collection } from 'firebase/firestore';
import { getFunctions, connectFunctionsEmulator, httpsCallable } from 'firebase/functions';

const PROJECT = 'demo-discovery-primary';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';
const here = path.dirname(fileURLToPath(import.meta.url));
const functionsDir = path.join(here, '../../functions');
const requireFromFunctions = createRequire(path.join(functionsDir, 'package.json'));

// Emulator-only fixtures (no real account or secret is involved).
const OWNER = { email: 'owner.fixture@example.org', password: 'OwnerFixture2024' };
const NEW_OWNER_PASSWORD = 'OwnerRecovered2025';
const ADMIN = { email: 'admin.fixture@example.org', password: 'AdminFixture2024' };
const SECRETS = [OWNER.password, NEW_OWNER_PASSWORD, ADMIN.password];

process.env.GCLOUD_PROJECT = PROJECT;
const { initializeApp: adminInit } = requireFromFunctions('firebase-admin/app');
const adminApp = adminInit({ projectId: PROJECT }, 'owner-tests');
const adminAuth = requireFromFunctions('firebase-admin/auth').getAuth(adminApp);
const adminDb = requireFromFunctions('firebase-admin/firestore').getFirestore(adminApp);

const apps = [];
function client(name) {
  const app = initializeApp({ projectId: PROJECT, apiKey: 'emulator-only-api-key' }, name);
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_HOST}`, { disableWarnings: true });
  const db = getFirestore(app);
  const [host, port] = FIRESTORE_HOST.split(':');
  connectFirestoreEmulator(db, host, Number(port));
  const functions = getFunctions(app);
  connectFunctionsEmulator(functions, '127.0.0.1', 5001);
  const call = (fn, data) => httpsCallable(functions, fn)(data).then((r) => r.data);
  return { auth, db, call };
}

async function provision(env) {
  try {
    const { stdout, stderr } = await promisify(execFile)(process.execPath, ['scripts/provision-owner.mjs'], {
      cwd: functionsDir, env: { ...process.env, GCLOUD_PROJECT: PROJECT, ...env },
    });
    return { code: 0, output: stdout + stderr };
  } catch (e) {
    return { code: e.code, output: (e.stdout ?? '') + (e.stderr ?? '') };
  }
}

async function auditEntries() {
  return (await adminDb.collection('auditLogs').get()).docs.map((d) => d.data());
}

const count = (entries, type) => entries.filter((e) => e.type === type).length;

async function denied(promise) {
  try {
    await promise;
  } catch (e) {
    return e;
  }
  assert.fail('expected the call to be rejected');
}

async function oobCode(email, type) {
  const res = await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT}/oobCodes`);
  const codes = (await res.json()).oobCodes.filter((c) => c.email === email && c.requestType === type);
  assert.ok(codes.length, `no ${type} email sent to ${email}`);
  return codes.at(-1).oobCode;
}

let ownerUid;
let adminUid;
let owner;
let admin;

before(async () => {
  await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT}/accounts`, { method: 'DELETE' });
  await fetch(`http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
  const a = await adminAuth.createUser({ email: ADMIN.email, password: ADMIN.password, emailVerified: true });
  adminUid = a.uid;
  await adminDb.doc(`users/${adminUid}`).set({
    uid: adminUid, email: ADMIN.email, phoneNumber: '', displayName: 'Other Admin', role: 'ADMIN',
    schoolId: 'discovery-primary', active: true, createdAt: null, updatedAt: null,
  });
  await adminDb.doc('users/parent1').set({
    uid: 'parent1', email: 'parent1@example.org', phoneNumber: '', displayName: 'Parent One', role: 'PARENT',
    schoolId: 'discovery-primary', active: false, createdAt: null, updatedAt: null,
  });
  owner = client('owner');
  admin = client('admin');
});

after(async () => {
  for (const app of apps) await deleteApp(app);
  await adminApp.delete();
});

test('provisioning script creates the Owner with server-side claims and never prints the password', async () => {
  const bad = await provision({ SUPER_ADMIN_EMAIL: OWNER.email, SUPER_ADMIN_PASSWORD: 'short' });
  assert.notEqual(bad.code, 0);
  assert.ok(!bad.output.includes('short'), 'rejected password must not be echoed');

  const run = await provision({ SUPER_ADMIN_EMAIL: OWNER.email, SUPER_ADMIN_PASSWORD: OWNER.password });
  assert.equal(run.code, 0, run.output);
  for (const s of SECRETS) assert.ok(!run.output.includes(s), 'password printed by the script');

  const record = await adminAuth.getUserByEmail(OWNER.email);
  ownerUid = record.uid;
  assert.deepEqual({ role: record.customClaims.role, owner: record.customClaims.owner }, { role: 'SUPER_ADMIN_OWNER', owner: true });
  const profile = (await adminDb.doc(`users/${ownerUid}`).get()).data();
  assert.equal(profile.role, 'SUPER_ADMIN_OWNER');
  assert.equal(profile.active, true);
  assert.ok(!Object.keys(profile).some((k) => /pass/i.test(k)), 'profile must not hold a password field');
  assert.equal(count(await auditEntries(), 'OWNER_PROVISIONED'), 1);

  // Re-running is idempotent; a second, different Owner is refused.
  assert.equal((await provision({ SUPER_ADMIN_EMAIL: OWNER.email })).code, 0);
  const other = await provision({ SUPER_ADMIN_EMAIL: 'someone.else@example.org', SUPER_ADMIN_PASSWORD: 'Another12345' });
  assert.notEqual(other.code, 0);
  await assert.rejects(adminAuth.getUserByEmail('someone.else@example.org'));
});

test('the Owner signs in normally and receives SUPER_ADMIN_OWNER authorization', async () => {
  const cred = await signInWithEmailAndPassword(owner.auth, OWNER.email, OWNER.password);
  const token = await cred.user.getIdTokenResult();
  assert.equal(token.claims.role, 'SUPER_ADMIN_OWNER');
  assert.equal(token.claims.owner, true);
  assert.ok((await getDocs(collection(owner.db, 'users'))).size >= 3, 'owner has administrator access');
  assert.ok((await getDocs(collection(owner.db, 'auditLogs'))).size >= 1, 'owner reads the audit log');

  await owner.call('recordSecurityEvent', { type: 'LOGIN' });
  const entries = await auditEntries();
  assert.equal(count(entries, 'OWNER_LOGIN'), 1);
  assert.equal(entries.find((e) => e.type === 'OWNER_ROLE_VERIFICATION').outcome, 'VERIFIED');
});

test('another administrator is refused with 403 and the attempt is audited', async () => {
  await signInWithEmailAndPassword(admin.auth, ADMIN.email, ADMIN.password);
  const before = count(await auditEntries(), 'PROTECTED_OWNER_ACTION_BLOCKED');

  for (const [fn, data] of [
    ['adminUpdateUser', { uid: ownerUid, active: false }],
    ['adminUpdateUser', { uid: ownerUid, role: 'ADMIN' }],
    ['adminSetAccountDisabled', { uid: ownerUid, disabled: true }],
    ['adminDeleteUser', { uid: ownerUid }],
  ]) {
    const e = await denied(admin.call(fn, data));
    assert.equal(e.code, 'functions/permission-denied', `${fn}: ${e.code}`);
    assert.match(e.message, /^Protected Super Admin Owner account\./);
  }
  // The raw HTTPS response is 403 FORBIDDEN with the protection message.
  const res = await fetch(`http://127.0.0.1:5001/${PROJECT}/us-central1/adminUpdateUser`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${await admin.auth.currentUser.getIdToken()}` },
    body: JSON.stringify({ data: { uid: ownerUid, active: false } }),
  });
  assert.equal(res.status, 403);
  assert.deepEqual((await res.json()).error, { status: 'PERMISSION_DENIED', message: 'Protected Super Admin Owner account.' });
  const blocked = (await auditEntries()).filter((e) => e.type === 'PROTECTED_OWNER_ACTION_BLOCKED');
  assert.equal(blocked.length - before, 5);
  assert.ok(blocked.every((e) => e.actorUid === adminUid && e.targetUid === ownerUid && e.at));
  assert.deepEqual(new Set(blocked.map((e) => e.action)), new Set(['UPDATE_USER', 'SUSPEND_ACCOUNT', 'DELETE_USER']));

  // Direct database writes are refused by firestore.rules as well.
  await assert.rejects(updateDoc(doc(admin.db, 'users', ownerUid), { active: false }), (e) => e.code === 'permission-denied');

  const record = await adminAuth.getUser(ownerUid);
  assert.equal(record.disabled, false);
  assert.equal(record.customClaims.role, 'SUPER_ADMIN_OWNER');
  const profile = (await adminDb.doc(`users/${ownerUid}`).get()).data();
  assert.equal(profile.role, 'SUPER_ADMIN_OWNER');
  assert.equal(profile.active, true);
});

test('the Owner cannot be demoted even by the Owner, and cannot be granted to anyone else', async () => {
  const e = await denied(owner.call('adminUpdateUser', { uid: ownerUid, role: 'ADMIN' }));
  assert.equal(e.code, 'functions/permission-denied');
  const grant = await denied(admin.call('adminUpdateUser', { uid: 'parent1', role: 'SUPER_ADMIN_OWNER' }));
  assert.equal(grant.code, 'functions/invalid-argument');
  // Ordinary administration still works (and the Owner's own actions are audited).
  await owner.call('adminUpdateUser', { uid: 'parent1', active: true });
  assert.equal((await adminDb.doc('users/parent1').get()).get('active'), true);
  assert.ok((await auditEntries()).some((x) => x.type === 'ADMIN_ACTION' && x.actorUid === ownerUid && x.targetUid === 'parent1'));
});

test('users without the Owner claim cannot impersonate the Owner', async () => {
  const intruder = client('intruder');
  const cred = await adminAuth.createUser({ email: 'intruder@example.org', password: 'Intruder12345' });
  await adminDb.doc(`users/${cred.uid}`).set({
    uid: cred.uid, email: 'intruder@example.org', phoneNumber: '', displayName: 'Intruder', role: 'SUPER_ADMIN_OWNER',
    schoolId: 'discovery-primary', active: true, createdAt: null, updatedAt: null,
  });
  await signInWithEmailAndPassword(intruder.auth, 'intruder@example.org', 'Intruder12345');
  await assert.rejects(getDocs(collection(intruder.db, 'users')), (e) => e.code === 'permission-denied');
  await assert.rejects(getDocs(collection(intruder.db, 'auditLogs')), (e) => e.code === 'permission-denied');
  const e = await denied(intruder.call('adminUpdateUser', { uid: 'parent1', active: false }));
  assert.equal(e.code, 'functions/permission-denied');
  // A profile that claims the Owner role without the server-side claim fails verification.
  await intruder.call('recordSecurityEvent', { type: 'LOGIN' });
  const check = (await auditEntries()).filter((x) => x.type === 'OWNER_ROLE_VERIFICATION' && x.actorUid === cred.uid);
  assert.deepEqual(check.map((x) => x.outcome), ['MISMATCH']);
  await adminAuth.deleteUser(cred.uid);
  await adminDb.doc(`users/${cred.uid}`).delete();
});

test('authentication failures and password recovery are audited; recovery restores access', async () => {
  const anon = client('anonymous');
  await assert.rejects(signInWithEmailAndPassword(anon.auth, OWNER.email, 'not-the-password1'));
  await anon.call('recordSecurityEvent', { type: 'AUTH_FAILURE', email: OWNER.email });
  // Non-owner addresses are not logged and nothing reveals whether they exist.
  assert.deepEqual(await anon.call('recordSecurityEvent', { type: 'AUTH_FAILURE', email: 'nobody@example.org' }), { ok: true });

  await sendPasswordResetEmail(anon.auth, OWNER.email);
  await anon.call('recordSecurityEvent', { type: 'PASSWORD_RESET_REQUESTED', email: OWNER.email });
  await confirmPasswordReset(anon.auth, await oobCode(OWNER.email, 'PASSWORD_RESET'), NEW_OWNER_PASSWORD);

  await assert.rejects(signInWithEmailAndPassword(anon.auth, OWNER.email, OWNER.password));
  const cred = await signInWithEmailAndPassword(anon.auth, OWNER.email, NEW_OWNER_PASSWORD);
  assert.equal((await cred.user.getIdTokenResult()).claims.role, 'SUPER_ADMIN_OWNER');

  const entries = await auditEntries();
  assert.equal(count(entries, 'OWNER_AUTH_FAILURE'), 1);
  assert.equal(count(entries, 'OWNER_PASSWORD_RESET_REQUESTED'), 1);
  await signOut(anon.auth);
});

test('the Owner can sign out every device; logout is audited', async () => {
  await signInWithEmailAndPassword(owner.auth, OWNER.email, NEW_OWNER_PASSWORD);
  const beforeRevoke = (await adminAuth.getUser(ownerUid)).tokensValidAfterTime;
  await new Promise((r) => setTimeout(r, 1100));
  await owner.call('revokeMySessions', {});
  assert.notEqual((await adminAuth.getUser(ownerUid)).tokensValidAfterTime, beforeRevoke);
  const second = client('owner-2');
  await signInWithEmailAndPassword(second.auth, OWNER.email, NEW_OWNER_PASSWORD);
  await second.call('recordSecurityEvent', { type: 'LOGOUT' });
  await signOut(second.auth);
  const entries = await auditEntries();
  assert.equal(count(entries, 'SESSIONS_REVOKED'), 1);
  assert.equal(count(entries, 'OWNER_LOGOUT'), 1);
});

test('no password or credential appears in Firestore or the audit log', async () => {
  const collections = await adminDb.listCollections();
  for (const c of collections) {
    const dump = JSON.stringify((await c.get()).docs.map((d) => d.data()));
    for (const s of SECRETS) assert.ok(!dump.includes(s), `a password was found in ${c.id}`);
    assert.ok(!/"[^"]*pass(word)?[^"]*":/i.test(dump), `a password field was found in ${c.id}`);
  }
});
