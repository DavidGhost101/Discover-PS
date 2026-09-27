// Security rules for the protected Super Admin Owner account.
import { before, after, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, collection } from 'firebase/firestore';

const here = path.dirname(fileURLToPath(import.meta.url));
const SCHOOL = 'discovery-primary';
const OWNER = 'SUPER_ADMIN_OWNER';
const OWNER_CLAIMS = { role: OWNER, owner: true };
let env;

const user = (uid, role, extra = {}) => ({
  uid, email: `${uid}@example.org`, phoneNumber: '', displayName: uid,
  role, schoolId: SCHOOL, active: true, createdAt: null, updatedAt: null, ...extra,
});

const as = (uid, claims = {}) => env.authenticatedContext(uid, { email: `${uid}@example.org`, ...claims }).firestore();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-discovery-primary',
    firestore: { rules: readFileSync(path.join(here, '../../firestore.rules'), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/owner'), user('owner', OWNER));
    await setDoc(doc(db, 'users/admin'), user('admin', 'ADMIN'));
    await setDoc(doc(db, 'users/docOnlyOwner'), user('docOnlyOwner', OWNER));
    await setDoc(doc(db, 'users/parentA'), user('parentA', 'PARENT'));
    await setDoc(doc(db, 'auditLogs/e1'), { type: 'OWNER_LOGIN', actorUid: 'owner' });
  });
});

describe('owner identity', () => {
  test('owner (profile + custom claims) has administrator access and reads the audit log', async () => {
    const db = as('owner', OWNER_CLAIMS);
    await assertSucceeds(getDocs(collection(db, 'users')));
    await assertSucceeds(setDoc(doc(db, 'classes/c9'), { id: 'c9', schoolId: SCHOOL, name: 'Grade 7', grade: 'Grade 7', teacherIds: [], subjectIds: [] }));
    await assertSucceeds(getDocs(collection(db, 'auditLogs')));
  });

  test('an Owner profile without the server-side claim grants nothing', async () => {
    const db = as('docOnlyOwner');
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(getDocs(collection(db, 'auditLogs')));
  });

  test('Owner claims on a non-owner profile grant nothing', async () => {
    const db = as('parentA', OWNER_CLAIMS);
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(getDocs(collection(db, 'auditLogs')));
  });

  test('a deactivated Owner profile grants nothing', async () => {
    await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), 'users/owner'), { active: false }));
    await assertFails(getDocs(collection(as('owner', OWNER_CLAIMS), 'users')));
  });
});

describe('owner protection', () => {
  test('another administrator cannot deactivate, demote, rename or delete the Owner', async () => {
    const db = as('admin');
    await assertFails(updateDoc(doc(db, 'users/owner'), { active: false }));
    await assertFails(updateDoc(doc(db, 'users/owner'), { role: 'ADMIN' }));
    await assertFails(updateDoc(doc(db, 'users/owner'), { displayName: 'Changed' }));
    await assertFails(setDoc(doc(db, 'users/owner'), user('owner', 'PARENT')));
    await assertFails(deleteDoc(doc(db, 'users/owner')));
  });

  test('nobody can grant the Owner role from a client', async () => {
    await assertFails(updateDoc(doc(as('admin'), 'users/parentA'), { role: OWNER }));
    await assertFails(setDoc(doc(as('admin'), 'users/newUser'), user('newUser', OWNER)));
    await assertFails(updateDoc(doc(as('admin'), 'users/admin'), { role: OWNER }));
    await assertFails(updateDoc(doc(as('owner', OWNER_CLAIMS), 'users/parentA'), { role: OWNER }));
    // Self-registration as Owner.
    const self = env.authenticatedContext('intruder', { email: 'intruder@example.org' }).firestore();
    await assertFails(setDoc(doc(self, 'users/intruder'), { ...user('intruder', OWNER, { email: 'intruder@example.org' }), active: false }));
  });

  test('the Owner may edit only their own name and phone, not their role or status', async () => {
    const db = as('owner', OWNER_CLAIMS);
    await assertSucceeds(updateDoc(doc(db, 'users/owner'), { displayName: 'Owner Name', phoneNumber: '+27820000000' }));
    await assertFails(updateDoc(doc(db, 'users/owner'), { role: 'ADMIN' }));
    await assertFails(updateDoc(doc(db, 'users/owner'), { active: false }));
    await assertFails(deleteDoc(doc(db, 'users/owner')));
  });

  test('administrators still manage ordinary accounts', async () => {
    const db = as('admin');
    await assertSucceeds(updateDoc(doc(db, 'users/parentA'), { active: false }));
    await assertSucceeds(deleteDoc(doc(db, 'users/parentA')));
    await assertSucceeds(updateDoc(doc(as('owner', OWNER_CLAIMS), 'users/admin'), { role: 'TEACHER' }));
  });
});

describe('audit log', () => {
  test('only Cloud Functions write it; only the Owner reads it', async () => {
    await assertFails(getDocs(collection(as('admin'), 'auditLogs')));
    await assertFails(getDoc(doc(as('admin'), 'auditLogs/e1')));
    await assertFails(addDoc(collection(as('owner', OWNER_CLAIMS), 'auditLogs'), { type: 'FAKE' }));
    await assertFails(updateDoc(doc(as('owner', OWNER_CLAIMS), 'auditLogs/e1'), { type: 'EDITED' }));
    await assertFails(deleteDoc(doc(as('owner', OWNER_CLAIMS), 'auditLogs/e1')));
    await assertFails(addDoc(collection(as('admin'), 'auditLogs'), { type: 'FAKE' }));
  });
});
