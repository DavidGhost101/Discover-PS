// Firebase access for the web portal: session tracking, role-scoped live listeners and
// writes. Queries and document shapes match the Android app and are what firestore.rules allow.
import {
  onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword, updateProfile,
  sendEmailVerification, sendPasswordResetEmail, signOut as fbSignOut, RecaptchaVerifier, signInWithPhoneNumber,
} from 'firebase/auth';
import {
  collection, doc, query, where, orderBy, limit, onSnapshot, setDoc, updateDoc, deleteDoc, getDoc, writeBatch,
  arrayUnion, arrayRemove, Timestamp,
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { provisioningAuth } from './firebase.js';
import { SCHOOL_ID, AUDIENCE_ALL, parseRole, validate, diff, fullName } from './access.js';

// ---------- errors ----------

export function friendlyError(e) {
  const code = e?.code ?? '';
  if (code.startsWith('functions/')) {
    const message = (e.message ?? '').replace(/\s*\[\d{3}\]$/, '');
    if (code === 'functions/permission-denied') return `403 FORBIDDEN — ${message || 'Access denied.'}`;
    if (['functions/not-found', 'functions/unavailable', 'functions/internal'].includes(code)) {
      return "The school's server functions are not reachable. An administrator must deploy Cloud Functions (see README).";
    }
    return message || 'Something went wrong. Please try again.';
  }
  const map = {
    'auth/invalid-credential': 'Incorrect email or password.',
    'auth/wrong-password': 'Incorrect email or password.',
    'auth/user-not-found': 'Incorrect email or password.',
    'auth/invalid-email': 'Enter a valid email address.',
    'auth/user-disabled': 'This account has been disabled.',
    'auth/email-already-in-use': 'An account already exists with this email address.',
    'auth/weak-password': 'The password is too weak.',
    'auth/too-many-requests': 'Too many attempts. Please wait a moment and try again.',
    'auth/network-request-failed': 'Network error. Check your connection and try again.',
    'auth/invalid-verification-code': 'The SMS code is incorrect.',
    'auth/invalid-phone-number': 'Enter a valid mobile number.',
    'auth/unauthorized-domain': 'This website address is not authorised in Firebase Authentication settings.',
    'permission-denied': 'Access denied: your account is not permitted to do this.',
    'unavailable': 'The school database is unreachable. Check your connection.',
    'not-found': 'The record no longer exists.',
    'unauthenticated': 'Your session has expired. Please sign in again.',
  };
  return map[code] || e?.message || 'Something went wrong. Please try again.';
}

function fail(message) {
  throw Object.assign(new Error(message), { code: 'validation' });
}

function check(error) {
  if (error) fail(error);
}

// ---------- session ----------

/**
 * Tracks the signed-in user. Calls onChange with one of:
 * {kind:'signedOut'} | {kind:'verifyEmail', email} | {kind:'noProfile'} | {kind:'ready', user} | {kind:'failed', message}
 */
export function watchSession({ auth, db }, onChange) {
  let profileUnsub = null;
  const evaluate = (fbUser) => {
    profileUnsub?.();
    profileUnsub = null;
    if (!fbUser) return onChange({ kind: 'signedOut' });
    const passwordUser = fbUser.providerData.some((p) => p.providerId === 'password');
    if (passwordUser && !fbUser.emailVerified) return onChange({ kind: 'verifyEmail', email: fbUser.email ?? '' });
    const uid = fbUser.uid;
    profileUnsub = onSnapshot(doc(db, 'users', uid), (snap) => {
      if (auth.currentUser?.uid !== uid) return;
      if (!snap.exists()) onChange({ kind: 'noProfile' });
      else onChange({ kind: 'ready', user: { ...snap.data(), uid } });
    }, (error) => {
      if (auth.currentUser?.uid === uid) onChange({ kind: 'failed', message: friendlyError(error) });
    });
  };
  const authUnsub = onAuthStateChanged(auth, evaluate);
  return {
    reevaluate: () => evaluate(auth.currentUser),
    stopProfile: () => { profileUnsub?.(); profileUnsub = null; },
    close: () => { profileUnsub?.(); authUnsub(); },
  };
}

// ---------- server functions & security events ----------

export function callFunction({ functions }, name, payload = {}) {
  return httpsCallable(functions, name)(payload).then((r) => r.data);
}

/**
 * Owner security events (login, logout, failed sign-in, reset request). The server writes
 * the audit entry only for the Owner account and never receives a password. Best effort:
 * sign-in keeps working when Cloud Functions are not deployed.
 */
function securityEvent(services, type, email, waitMs = 0) {
  const call = callFunction(services, 'recordSecurityEvent', email ? { type, email } : { type }).catch(() => {});
  return waitMs ? Promise.race([call, new Promise((r) => setTimeout(r, waitMs))]) : call;
}

// ---------- auth actions ----------

export async function signIn(services, email, password) {
  check(validate.email(email));
  if (!password) fail('Password is required.');
  try {
    await signInWithEmailAndPassword(services.auth, email.trim(), password);
  } catch (e) {
    if (['auth/invalid-credential', 'auth/wrong-password', 'auth/too-many-requests'].includes(e?.code)) securityEvent(services, 'AUTH_FAILURE', email.trim());
    throw e;
  }
  securityEvent(services, 'LOGIN');
}

/** Self-registration: always an inactive PARENT awaiting administrator approval. */
export async function registerParent({ auth, db }, name, email, password) {
  check(validate.required(name, 'Full name'));
  check(validate.email(email));
  check(validate.password(password));
  const cred = await createUserWithEmailAndPassword(auth, email.trim(), password);
  try {
    await updateProfile(cred.user, { displayName: name.trim() });
    await setDoc(doc(db, 'users', cred.user.uid), {
      uid: cred.user.uid, email: email.trim(), phoneNumber: '', displayName: name.trim(),
      role: 'PARENT', schoolId: SCHOOL_ID, active: false, createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
  } catch (e) {
    await cred.user.delete().catch(() => {});
    throw e;
  }
  await sendEmailVerification(cred.user);
}

export async function sendReset(services, email) {
  check(validate.email(email));
  await sendPasswordResetEmail(services.auth, email.trim());
  securityEvent(services, 'PASSWORD_RESET_REQUESTED', email.trim());
}

export async function resendVerification({ auth }) {
  if (!auth.currentUser) fail('Please sign in again.');
  await sendEmailVerification(auth.currentUser);
}

export async function refreshVerification({ auth }) {
  if (!auth.currentUser) fail('Please sign in again.');
  await auth.currentUser.reload();
  return auth.currentUser.emailVerified;
}

export async function signOut(services) {
  if (services.auth.currentUser) await securityEvent(services, 'LOGOUT', '', 3000);
  await fbSignOut(services.auth);
}

/** Revokes every refresh token of this account, ending its sessions on all devices. */
export async function revokeAllSessions(services) {
  await callFunction(services, 'revokeMySessions');
}

/**
 * Administrator changes to the protected Owner go through the server, which refuses them
 * (403) and records the attempt in the audit log.
 */
export async function adminUpdateUser(services, { uid, role, active, displayName, phoneNumber }) {
  await callFunction(services, 'adminUpdateUser', { uid, role, active, displayName, phoneNumber });
}

let recaptcha = null;

export async function sendPhoneCode({ auth }, phoneE164, containerId) {
  recaptcha?.clear?.();
  recaptcha = new RecaptchaVerifier(auth, containerId, { size: 'invisible' });
  return signInWithPhoneNumber(auth, phoneE164, recaptcha);
}

/** Confirms the SMS code; a first-time phone login becomes a pending PARENT profile. */
export async function confirmPhoneCode({ db }, confirmation, code) {
  const cred = await confirmation.confirm(code);
  const ref = doc(db, 'users', cred.user.uid);
  if (!(await getDoc(ref)).exists()) {
    await setDoc(ref, {
      uid: cred.user.uid, email: '', phoneNumber: cred.user.phoneNumber ?? '',
      displayName: cred.user.displayName || cred.user.phoneNumber || '', role: 'PARENT',
      schoolId: SCHOOL_ID, active: false, createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
  }
}

function randomPassword() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%';
  const bytes = crypto.getRandomValues(new Uint32Array(32));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join('');
}

/**
 * Administrator only: creates a login for someone else and emails them a link to set a
 * password. The role is enforced by firestore.rules (non-admins are rejected).
 */
export async function provisionAccount({ auth, db }, { email, name, role, phone = '' }) {
  check(validate.email(email));
  check(validate.required(name, 'Name'));
  check(validate.phone(phone));
  if (!parseRole(role)) fail('Choose a valid role.');
  const provisioning = provisioningAuth();
  const cred = await createUserWithEmailAndPassword(provisioning, email.trim(), randomPassword());
  try {
    await setDoc(doc(db, 'users', cred.user.uid), {
      uid: cred.user.uid, email: email.trim(), phoneNumber: phone.trim(), displayName: name.trim(),
      role, schoolId: SCHOOL_ID, active: true, createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
  } catch (e) {
    await cred.user.delete().catch(() => {});
    throw e;
  } finally {
    await fbSignOut(provisioning).catch(() => {});
  }
  await sendPasswordResetEmail(auth, email.trim());
  return cred.user.uid;
}

// ---------- live listeners ----------

const inSchool = (db, name) => query(collection(db, name), where('schoolId', '==', SCHOOL_ID));

function listen(q, onData) {
  return onSnapshot(q,
    (snap) => onData({ items: snap.docs.map((d) => d.data()), loading: false, error: null }),
    (error) => onData({ items: [], loading: false, error: friendlyError(error) }));
}

function listenDoc(ref, onData) {
  return onSnapshot(ref,
    (snap) => onData({ items: snap.exists() ? [snap.data()] : [], loading: false, error: null }),
    (error) => onData({ items: [], loading: false, error: friendlyError(error) }));
}

/** whereIn accepts at most 30 values: split and merge. */
function listenIn(base, field, values, onData) {
  const unique = [...new Set(values.filter(Boolean))];
  if (!unique.length) { onData({ items: [], loading: false, error: null }); return () => {}; }
  const chunks = [];
  for (let i = 0; i < unique.length; i += 30) chunks.push(unique.slice(i, i + 30));
  const parts = chunks.map(() => ({ items: [], loading: true, error: null }));
  const emit = () => onData({ items: parts.flatMap((p) => p.items), loading: parts.some((p) => p.loading), error: parts.find((p) => p.error)?.error ?? null });
  const unsubs = chunks.map((chunk, i) => listen(query(base, where(field, 'in', chunk)), (r) => { parts[i] = r; emit(); }));
  return () => unsubs.forEach((u) => u());
}

function mergeNotices(onData) {
  const parts = [{ items: [], loading: true, error: null }, { items: [], loading: true, error: null }];
  return [0, 1].map((i) => (r) => {
    parts[i] = r;
    const seen = new Map();
    parts.flatMap((p) => p.items).forEach((n) => seen.set(n.id, n));
    onData({ items: [...seen.values()], loading: parts.some((p) => p.loading), error: parts.find((p) => p.error)?.error ?? null });
  });
}

export const COLLECTIONS = ['users', 'teachers', 'teacherProfile', 'students', 'classes', 'subjects', 'assignments', 'notices', 'settings', 'auditLogs'];

/**
 * Starts the listeners a role may use. update(key, remote) receives {items, loading, error}.
 * Returns a function that removes every listener (called on sign-out / role change).
 */
export function startRoleListeners({ db }, uid, role, update, { owner = false } = {}) {
  const unsubs = [];
  const on = (key, fn) => unsubs.push(fn((r) => update(key, r)));
  on('settings', (cb) => listenDoc(doc(db, 'settings', 'school_info'), cb));
  const noticesFor = (cb) => listen(query(inSchool(db, 'notices'), where('published', '==', true), where('targetRole', 'in', [AUDIENCE_ALL, role])), cb);

  if (role === 'ADMIN') {
    for (const name of ['users', 'teachers', 'students', 'classes', 'subjects', 'assignments', 'notices']) {
      on(name, (cb) => listen(inSchool(db, name), cb));
    }
    if (owner) on('auditLogs', (cb) => listen(query(collection(db, 'auditLogs'), orderBy('at', 'desc'), limit(50)), cb));
  } else if (role === 'TEACHER') {
    let studentsUnsub = () => {};
    let lastKey = null;
    unsubs.push(listenDoc(doc(db, 'teachers', uid), (r) => {
      update('teacherProfile', r);
      const classIds = r.items[0]?.classIds ?? [];
      const key = [...classIds].sort().join('|');
      if (key !== lastKey || r.error) {
        lastKey = key;
        studentsUnsub();
        studentsUnsub = listenIn(inSchool(db, 'students'), 'classId', classIds, (s) => update('students', s));
      }
    }));
    unsubs.push(() => studentsUnsub());
    for (const name of ['teachers', 'classes', 'subjects', 'assignments']) on(name, (cb) => listen(inSchool(db, name), cb));
    const [a, b] = mergeNotices((r) => update('notices', r));
    unsubs.push(noticesFor(a));
    unsubs.push(listen(query(inSchool(db, 'notices'), where('authorId', '==', uid)), b));
  } else if (role === 'PARENT' || role === 'STUDENT') {
    const studentsQuery = role === 'PARENT'
      ? query(collection(db, 'students'), where('parentIds', 'array-contains', uid))
      : query(collection(db, 'students'), where('userId', '==', uid));
    on('students', (cb) => listen(studentsQuery, cb));
    for (const name of ['teachers', 'classes', 'subjects', 'assignments']) on(name, (cb) => listen(inSchool(db, name), cb));
    on('notices', noticesFor);
  } else if (role === 'STAFF') {
    on('teachers', (cb) => listen(inSchool(db, 'teachers'), cb));
    on('notices', noticesFor);
  }
  return () => unsubs.forEach((u) => u());
}

// ---------- writes ----------

const newId = () => crypto.randomUUID();
const stamp = (existing) => ({ createdAt: existing?.createdAt ?? Timestamp.now(), updatedAt: Timestamp.now() });

export async function updateOwnProfile({ db }, uid, displayName, phoneNumber) {
  check(validate.required(displayName, 'Name'));
  if (displayName.trim().length > 100) fail('Name is too long.');
  check(validate.phone(phoneNumber));
  await updateDoc(doc(db, 'users', uid), { displayName: displayName.trim(), phoneNumber: phoneNumber.trim(), updatedAt: Timestamp.now() });
}

export async function updateUser({ db }, user) {
  if (!parseRole(user.role)) fail('Choose a valid role.');
  check(validate.required(user.displayName, 'Name'));
  check(validate.phone(user.phoneNumber));
  const { uid, email, phoneNumber, displayName, role, active, createdAt } = user;
  await setDoc(doc(db, 'users', uid), {
    uid, email: email ?? '', phoneNumber: (phoneNumber ?? '').trim(), displayName: displayName.trim(),
    role, schoolId: SCHOOL_ID, active: !!active, createdAt: createdAt ?? null, updatedAt: Timestamp.now(),
  });
}

export async function saveTeacher({ db }, teacher, previous) {
  if (!teacher.uid) fail('Teacher account is missing.');
  check(validate.required(teacher.firstName, 'First name'));
  check(validate.phone(teacher.phoneNumber));
  const batch = writeBatch(db);
  batch.set(doc(db, 'teachers', teacher.uid), {
    uid: teacher.uid, userId: teacher.uid, schoolId: SCHOOL_ID,
    employeeNumber: teacher.employeeNumber ?? '', firstName: teacher.firstName.trim(), lastName: (teacher.lastName ?? '').trim(),
    email: teacher.email ?? '', phoneNumber: (teacher.phoneNumber ?? '').trim(),
    classIds: teacher.classIds ?? [], subjectIds: teacher.subjectIds ?? [], ...stamp(previous),
  });
  const c = diff(previous?.classIds, teacher.classIds);
  c.added.forEach((id) => batch.update(doc(db, 'classes', id), { teacherIds: arrayUnion(teacher.uid) }));
  c.removed.forEach((id) => batch.update(doc(db, 'classes', id), { teacherIds: arrayRemove(teacher.uid) }));
  const s = diff(previous?.subjectIds, teacher.subjectIds);
  s.added.forEach((id) => batch.update(doc(db, 'subjects', id), { teacherIds: arrayUnion(teacher.uid) }));
  s.removed.forEach((id) => batch.update(doc(db, 'subjects', id), { teacherIds: arrayRemove(teacher.uid) }));
  await batch.commit();
}

export async function createTeacher(services, teacher) {
  check(validate.required(teacher.firstName, 'First name'));
  const uid = await provisionAccount(services, { email: teacher.email, name: fullName(teacher), role: 'TEACHER', phone: teacher.phoneNumber });
  await saveTeacher(services, { ...teacher, uid }, null);
}

export async function deleteTeacher({ db }, teacher) {
  const batch = writeBatch(db);
  batch.delete(doc(db, 'teachers', teacher.uid));
  (teacher.classIds ?? []).forEach((id) => batch.update(doc(db, 'classes', id), { teacherIds: arrayRemove(teacher.uid) }));
  (teacher.subjectIds ?? []).forEach((id) => batch.update(doc(db, 'subjects', id), { teacherIds: arrayRemove(teacher.uid) }));
  await batch.commit();
}

export async function saveStudent({ db }, student) {
  check(validate.required(student.firstName, 'First name'));
  check(validate.required(student.classId, 'Class'));
  if (student.email) check(validate.email(student.email));
  const id = student.uid || newId();
  await setDoc(doc(db, 'students', id), {
    uid: id, userId: student.userId ?? '', schoolId: SCHOOL_ID, studentNumber: (student.studentNumber ?? '').trim(),
    firstName: student.firstName.trim(), lastName: (student.lastName ?? '').trim(), email: (student.email ?? '').trim(),
    phoneNumber: student.phoneNumber ?? '', classId: student.classId,
    parentIds: [...new Set((student.parentIds ?? []).filter(Boolean))], ...stamp(student),
  });
  return id;
}

export async function deleteStudent({ db }, student) {
  await deleteDoc(doc(db, 'students', student.uid));
}

export async function createParentForStudent(services, student, { name, email, phone }) {
  const uid = await provisionAccount(services, { email, name, role: 'PARENT', phone });
  await saveStudent(services, { ...student, parentIds: [...(student.parentIds ?? []), uid] });
}

export async function createStudentLogin(services, student, email) {
  const uid = await provisionAccount(services, { email, name: fullName(student), role: 'STUDENT' });
  await saveStudent(services, { ...student, userId: uid, email: email.trim() });
}

export async function saveClass({ db }, cls, previous) {
  check(validate.required(cls.name, 'Class name'));
  const id = cls.id || newId();
  const batch = writeBatch(db);
  batch.set(doc(db, 'classes', id), {
    id, schoolId: SCHOOL_ID, name: cls.name.trim(), grade: (cls.grade ?? '').trim(),
    teacherIds: cls.teacherIds ?? [], subjectIds: cls.subjectIds ?? [], ...stamp(previous),
  });
  const t = diff(previous?.teacherIds, cls.teacherIds);
  t.added.forEach((uid) => batch.update(doc(db, 'teachers', uid), { classIds: arrayUnion(id) }));
  t.removed.forEach((uid) => batch.update(doc(db, 'teachers', uid), { classIds: arrayRemove(id) }));
  await batch.commit();
}

export async function deleteClass({ db }, cls, enrolled) {
  if (enrolled > 0) fail(`Move the ${enrolled} enrolled learner(s) to another class before deleting ${cls.name}.`);
  const batch = writeBatch(db);
  batch.delete(doc(db, 'classes', cls.id));
  (cls.teacherIds ?? []).forEach((uid) => batch.update(doc(db, 'teachers', uid), { classIds: arrayRemove(cls.id) }));
  await batch.commit();
}

export async function saveSubject({ db }, subject, previous, classIds, previousClassIds) {
  check(validate.required(subject.name, 'Subject name'));
  const id = subject.id || newId();
  const batch = writeBatch(db);
  batch.set(doc(db, 'subjects', id), {
    id, schoolId: SCHOOL_ID, name: subject.name.trim(), code: (subject.code ?? '').trim(),
    teacherIds: subject.teacherIds ?? [], ...stamp(previous),
  });
  const t = diff(previous?.teacherIds, subject.teacherIds);
  t.added.forEach((uid) => batch.update(doc(db, 'teachers', uid), { subjectIds: arrayUnion(id) }));
  t.removed.forEach((uid) => batch.update(doc(db, 'teachers', uid), { subjectIds: arrayRemove(id) }));
  const c = diff(previousClassIds, classIds);
  c.added.forEach((cid) => batch.update(doc(db, 'classes', cid), { subjectIds: arrayUnion(id) }));
  c.removed.forEach((cid) => batch.update(doc(db, 'classes', cid), { subjectIds: arrayRemove(id) }));
  await batch.commit();
}

export async function deleteSubject({ db }, subject, classIds) {
  const batch = writeBatch(db);
  batch.delete(doc(db, 'subjects', subject.id));
  (subject.teacherIds ?? []).forEach((uid) => batch.update(doc(db, 'teachers', uid), { subjectIds: arrayRemove(subject.id) }));
  classIds.forEach((cid) => batch.update(doc(db, 'classes', cid), { subjectIds: arrayRemove(subject.id) }));
  await batch.commit();
}

export async function saveAssignment({ db }, a) {
  check(validate.required(a.title, 'Title'));
  check(validate.required(a.classId, 'Class'));
  if (a.title.trim().length > 200) fail('Title is too long.');
  const id = a.id || newId();
  await setDoc(doc(db, 'assignments', id), {
    id, schoolId: SCHOOL_ID, classId: a.classId, subjectId: a.subjectId ?? '', teacherId: a.teacherId ?? '',
    title: a.title.trim(), description: (a.description ?? '').trim(),
    dueDate: a.dueDate ? Timestamp.fromDate(a.dueDate instanceof Date ? a.dueDate : a.dueDate.toDate()) : null,
    ...stamp(a),
  });
}

export async function deleteAssignment({ db }, a) {
  await deleteDoc(doc(db, 'assignments', a.id));
}

export async function saveNotice({ db }, n) {
  check(validate.required(n.title, 'Title'));
  check(validate.required(n.message, 'Message'));
  if (n.title.trim().length > 200) fail('Title is too long.');
  const id = n.id || newId();
  await setDoc(doc(db, 'notices', id), {
    id, schoolId: SCHOOL_ID, authorId: n.authorId, title: n.title.trim(), message: n.message.trim(),
    targetRole: n.targetRole, classId: n.classId ?? '', published: !!n.published, ...stamp(n),
  });
}

export async function deleteNotice({ db }, n) {
  await deleteDoc(doc(db, 'notices', n.id));
}

export async function saveSettings({ db }, s) {
  check(validate.required(s.schoolName, 'School name'));
  if (s.contactEmail) check(validate.email(s.contactEmail));
  check(validate.phone(s.contactPhone));
  await setDoc(doc(db, 'settings', 'school_info'), { ...s, schoolId: SCHOOL_ID });
}

