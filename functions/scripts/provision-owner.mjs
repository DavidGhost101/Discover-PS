#!/usr/bin/env node
// Creates or updates the protected Super Admin Owner account with the Firebase Admin SDK.
//
//   SUPER_ADMIN_EMAIL=<owner email> [SUPER_ADMIN_PASSWORD=<secret>] [SUPER_ADMIN_NAME=<name>] \
//   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> node scripts/provision-owner.mjs
//
// * The password (optional) is read from the environment only, handed straight to Firebase
//   Authentication (which stores it hashed) and never printed, logged or written anywhere.
//   Without SUPER_ADMIN_PASSWORD the owner sets a password with "Forgot password".
// * The Owner role is granted as an Auth custom claim {role: SUPER_ADMIN_OWNER, owner: true}
//   plus the /users/{uid} profile; clients can set neither (firestore.rules).
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { OWNER_ROLE, SCHOOL_ID, audit, passwordProblem } from '../owner.js';

function die(message) {
  console.error(`provision-owner: ${message}`);
  process.exit(1);
}

const email = (process.env.SUPER_ADMIN_EMAIL ?? '').trim().toLowerCase();
const password = process.env.SUPER_ADMIN_PASSWORD ?? '';
const name = (process.env.SUPER_ADMIN_NAME ?? '').trim() || 'School Owner';
if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) die('SUPER_ADMIN_EMAIL is missing or not an email address.');
if (password) {
  const problem = passwordProblem(password);
  if (problem) die(`SUPER_ADMIN_PASSWORD ${problem}. (The value is not shown.)`);
}

initializeApp(process.env.GCLOUD_PROJECT ? { projectId: process.env.GCLOUD_PROJECT } : undefined);
const auth = getAuth();
const db = getFirestore();

// Exactly one Owner: refuse to silently create a second one.
const others = (await db.collection('users').where('role', '==', OWNER_ROLE).get()).docs
  .filter((d) => (d.get('email') ?? '').toLowerCase() !== email);
if (others.length) die(`another account (${others.map((d) => d.id).join(', ')}) is already the Owner. Transfer ownership deliberately in the Firebase console first.`);

let record;
let created = false;
try {
  record = await auth.getUserByEmail(email);
} catch (e) {
  if (e.code !== 'auth/user-not-found') die(`could not look up the owner account (${e.code ?? e.message}).`);
  record = await auth.createUser({ email, displayName: name, ...(password ? { password } : {}) });
  created = true;
}
if (!created && password) await auth.updateUser(record.uid, { password });
if (record.disabled) await auth.updateUser(record.uid, { disabled: false });

await auth.setCustomUserClaims(record.uid, { ...(record.customClaims ?? {}), role: OWNER_ROLE, owner: true });

const ref = db.doc(`users/${record.uid}`);
const existing = (await ref.get()).data() ?? {};
await ref.set({
  uid: record.uid,
  email,
  phoneNumber: existing.phoneNumber ?? '',
  displayName: existing.displayName || record.displayName || name,
  role: OWNER_ROLE,
  schoolId: SCHOOL_ID,
  active: true,
  createdAt: existing.createdAt ?? FieldValue.serverTimestamp(),
  updatedAt: FieldValue.serverTimestamp(),
});

await audit({
  type: 'OWNER_PROVISIONED', outcome: 'OK', actorUid: 'admin-sdk', targetUid: record.uid,
  details: { accountCreated: created, loginUpdated: !!password },
});

console.log(`Owner account ready: uid=${record.uid} email=${email} role=${OWNER_ROLE} (${created ? 'created' : 'updated'}).`);
console.log(password
  ? 'Password: set from SUPER_ADMIN_PASSWORD (not shown).'
  : 'Password: unchanged. To set one, use "Forgot password" on the sign-in page with the owner email.');
console.log('Sign in with the owner email; the portal asks you to verify the address on first sign-in.');
process.exit(0);
