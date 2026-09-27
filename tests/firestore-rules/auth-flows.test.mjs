// End-to-end authentication flows against the Auth + Firestore emulators, using the same
// sequence of Firebase calls the Android app makes (FirebaseAuthenticationRepository).
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { initializeApp, deleteApp } from 'firebase/app';
import {
  getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword,
  sendEmailVerification, sendPasswordResetEmail, confirmPasswordReset, applyActionCode, signOut,
} from 'firebase/auth';
import {
  getFirestore, connectFirestoreEmulator, doc, setDoc, getDoc, getDocs, updateDoc, collection, query, where,
} from 'firebase/firestore';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';

const PROJECT = 'demo-discovery-primary';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
const here = path.dirname(fileURLToPath(import.meta.url));
const SCHOOL = 'discovery-primary';
let env;
const apps = [];

function client(name) {
  const app = initializeApp({ projectId: PROJECT, apiKey: 'emulator-only-api-key' }, name);
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_HOST}`, { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  return { auth, db };
}

async function oobCodes() {
  const res = await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT}/oobCodes`);
  return (await res.json()).oobCodes;
}

async function latestCode(email, requestType) {
  const codes = (await oobCodes()).filter((c) => c.email === email && c.requestType === requestType);
  assert.ok(codes.length > 0, `no ${requestType} email sent to ${email}`);
  return codes.at(-1).oobCode;
}

const profile = (uid, email, role, active) => ({
  uid, email, phoneNumber: '', displayName: email.split('@')[0], role, schoolId: SCHOOL, active, createdAt: null, updatedAt: null,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: { rules: readFileSync(path.join(here, '../../firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
  await env.clearFirestore();
  await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT}/accounts`, { method: 'DELETE' });
});

after(async () => {
  for (const app of apps) await deleteApp(app);
  await env?.cleanup();
});

test('bootstrap admin signs in and reads the user list', async () => {
  const { auth, db } = client('admin');
  const cred = await createUserWithEmailAndPassword(auth, 'principal@example.org', 'Admin12345');
  // First administrator is created by the school's Firebase owner (console / rules disabled).
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), `users/${cred.user.uid}`), profile(cred.user.uid, 'principal@example.org', 'ADMIN', true)));
  await signOut(auth);
  await signInWithEmailAndPassword(auth, 'principal@example.org', 'Admin12345');
  const users = await getDocs(query(collection(db, 'users'), where('schoolId', '==', SCHOOL)));
  assert.equal(users.size, 1);
});

test('invalid email / wrong password are rejected', async () => {
  const { auth } = client('bad-login');
  await assert.rejects(signInWithEmailAndPassword(auth, 'principal@example.org', 'wrong-password'), /auth\/(wrong-password|invalid-credential)/);
  await assert.rejects(signInWithEmailAndPassword(auth, 'nobody@example.org', 'Whatever123'), /auth\/(user-not-found|invalid-credential)/);
  await assert.rejects(signInWithEmailAndPassword(auth, 'not-an-email', 'Whatever123'), /auth\/invalid-email/);
});

test('parent self-registration: pending profile, verification email, no data until approved', async () => {
  const { auth, db } = client('parent-reg');
  const email = 'newparent@example.org';
  const cred = await createUserWithEmailAndPassword(auth, email, 'Parent12345');
  // Same document the app writes in registerParent().
  await setDoc(doc(db, `users/${cred.user.uid}`), profile(cred.user.uid, email, 'PARENT', false));
  await sendEmailVerification(cred.user);
  assert.equal(cred.user.emailVerified, false);

  // Attempted self-promotion is rejected by the rules.
  await assert.rejects(updateDoc(doc(db, `users/${cred.user.uid}`), { role: 'ADMIN', active: true }), /permission/i);
  // Pending account cannot read school data.
  await assert.rejects(getDocs(query(collection(db, 'classes'), where('schoolId', '==', SCHOOL))), /permission/i);

  // Verify the email through the emailed link.
  await applyActionCode(auth, await latestCode(email, 'VERIFY_EMAIL'));
  await cred.user.reload();
  assert.equal(cred.user.emailVerified, true);

  // Admin approves.
  const admin = client('admin-approve');
  await signInWithEmailAndPassword(admin.auth, 'principal@example.org', 'Admin12345');
  await updateDoc(doc(admin.db, `users/${cred.user.uid}`), { active: true });

  // Now the parent can read school data but still no children until linked.
  await getDocs(query(collection(db, 'classes'), where('schoolId', '==', SCHOOL)));
  const kids = await getDocs(query(collection(db, 'students'), where('parentIds', 'array-contains', cred.user.uid)));
  assert.equal(kids.size, 0);
});

test('admin provisions a teacher via a secondary auth instance without being signed out', async () => {
  const admin = client('admin-provision');
  await signInWithEmailAndPassword(admin.auth, 'principal@example.org', 'Admin12345');
  const provisioning = client('provisioning');
  const email = 'teacher.new@example.org';

  // Mirrors FirebaseAuthenticationRepository.provisionAccount().
  const created = await createUserWithEmailAndPassword(provisioning.auth, email, 'Tmp-' + Math.random().toString(36).slice(2) + 'A1!');
  await setDoc(doc(admin.db, `users/${created.user.uid}`), profile(created.user.uid, email, 'TEACHER', true));
  await setDoc(doc(admin.db, `teachers/${created.user.uid}`), {
    uid: created.user.uid, userId: created.user.uid, schoolId: SCHOOL, firstName: 'New', lastName: 'Teacher',
    email, phoneNumber: '', employeeNumber: '', classIds: [], subjectIds: [],
  });
  await signOut(provisioning.auth);
  await sendPasswordResetEmail(admin.auth, email);

  assert.equal(admin.auth.currentUser?.email, 'principal@example.org', 'admin must stay signed in');

  // The teacher sets a password from the emailed link and signs in.
  const teacher = client('teacher-login');
  await confirmPasswordReset(teacher.auth, await latestCode(email, 'PASSWORD_RESET'), 'Teacher12345');
  const signedIn = await signInWithEmailAndPassword(teacher.auth, email, 'Teacher12345');
  const me = await getDoc(doc(teacher.db, `users/${signedIn.user.uid}`));
  assert.equal(me.data().role, 'TEACHER');
  console.log(`emailVerified after password reset: ${signedIn.user.emailVerified}`);
});

test('non-admin cannot provision privileged accounts', async () => {
  const { auth, db } = client('parent-attacker');
  await signInWithEmailAndPassword(auth, 'newparent@example.org', 'Parent12345');
  const provisioning = client('parent-provisioning');
  const created = await createUserWithEmailAndPassword(provisioning.auth, 'fake.admin@example.org', 'Whatever123!');
  await assert.rejects(setDoc(doc(db, `users/${created.user.uid}`), profile(created.user.uid, 'fake.admin@example.org', 'ADMIN', true)), /permission/i);
  // And the orphan login cannot give itself a profile beyond a pending parent.
  await assert.rejects(setDoc(doc(provisioning.db, `users/${created.user.uid}`), profile(created.user.uid, 'fake.admin@example.org', 'ADMIN', true)), /permission/i);
});

test('forgot password sends a reset email and the new password works', async () => {
  const { auth } = client('forgot');
  await sendPasswordResetEmail(auth, 'newparent@example.org');
  await confirmPasswordReset(auth, await latestCode('newparent@example.org', 'PASSWORD_RESET'), 'Changed12345');
  await assert.rejects(signInWithEmailAndPassword(auth, 'newparent@example.org', 'Parent12345'));
  const cred = await signInWithEmailAndPassword(auth, 'newparent@example.org', 'Changed12345');
  assert.ok(cred.user.uid);
});

test('deactivating a user cuts off data access immediately', async () => {
  const admin = client('admin-deactivate');
  await signInWithEmailAndPassword(admin.auth, 'principal@example.org', 'Admin12345');
  const parent = client('parent-deactivated');
  const cred = await signInWithEmailAndPassword(parent.auth, 'newparent@example.org', 'Changed12345');
  await getDocs(query(collection(parent.db, 'classes'), where('schoolId', '==', SCHOOL)));
  await updateDoc(doc(admin.db, `users/${cred.user.uid}`), { active: false });
  await assert.rejects(getDocs(query(collection(parent.db, 'classes'), where('schoolId', '==', SCHOOL))), /permission/i);
});
