// Firestore security rules tests for Discovery Primary.
// Run with: cd tests/firestore-rules && npm install && npm test
// (starts the Firestore + Auth emulators; no real project or credentials needed).
import { before, after, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, collection, query, where, writeBatch,
} from 'firebase/firestore';

const here = path.dirname(fileURLToPath(import.meta.url));
const SCHOOL = 'discovery-primary';
let env;

const user = (uid, role, extra = {}) => ({
  uid, email: `${uid}@example.org`, phoneNumber: '', displayName: uid,
  role, schoolId: SCHOOL, active: true, createdAt: null, updatedAt: null, ...extra,
});

async function seed() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const docs = {
      'users/admin': user('admin', 'ADMIN'),
      'users/teacherA': user('teacherA', 'TEACHER'),
      'users/teacherB': user('teacherB', 'TEACHER'),
      'users/teacherOff': user('teacherOff', 'TEACHER', { active: false }),
      'users/parentA': user('parentA', 'PARENT'),
      'users/parentB': user('parentB', 'PARENT'),
      'users/parentPending': user('parentPending', 'PARENT', { active: false }),
      'users/studentA': user('studentA', 'STUDENT'),
      'users/staff': user('staff', 'STAFF'),
      'teachers/teacherA': { uid: 'teacherA', userId: 'teacherA', schoolId: SCHOOL, firstName: 'Ann', lastName: 'A', classIds: ['c1'], subjectIds: ['math'] },
      'teachers/teacherB': { uid: 'teacherB', userId: 'teacherB', schoolId: SCHOOL, firstName: 'Ben', lastName: 'B', classIds: ['c2'], subjectIds: [] },
      'teachers/teacherOff': { uid: 'teacherOff', userId: 'teacherOff', schoolId: SCHOOL, classIds: ['c1', 'c2'], subjectIds: [] },
      'classes/c1': { id: 'c1', schoolId: SCHOOL, name: 'Grade 4A', grade: 'Grade 4', teacherIds: ['teacherA'], subjectIds: ['math'] },
      'classes/c2': { id: 'c2', schoolId: SCHOOL, name: 'Grade 5A', grade: 'Grade 5', teacherIds: ['teacherB'], subjectIds: [] },
      'subjects/math': { id: 'math', schoolId: SCHOOL, name: 'Mathematics', code: 'M', teacherIds: ['teacherA'] },
      'students/s1': { uid: 's1', userId: 'studentA', schoolId: SCHOOL, firstName: 'Child', lastName: 'A', classId: 'c1', parentIds: ['parentA'] },
      'students/s2': { uid: 's2', userId: '', schoolId: SCHOOL, firstName: 'Child', lastName: 'B', classId: 'c2', parentIds: ['parentB'] },
      'assignments/a1': { id: 'a1', schoolId: SCHOOL, classId: 'c1', subjectId: 'math', teacherId: 'teacherA', title: 'A1', description: '' },
      'assignments/a2': { id: 'a2', schoolId: SCHOOL, classId: 'c2', subjectId: '', teacherId: 'teacherB', title: 'A2', description: '' },
      'notices/all': { id: 'all', schoolId: SCHOOL, authorId: 'admin', title: 'All', message: 'm', targetRole: 'ALL', classId: '', published: true },
      'notices/parents': { id: 'parents', schoolId: SCHOOL, authorId: 'admin', title: 'P', message: 'm', targetRole: 'PARENT', classId: '', published: true },
      'notices/teachers': { id: 'teachers', schoolId: SCHOOL, authorId: 'admin', title: 'T', message: 'm', targetRole: 'TEACHER', classId: '', published: true },
      'notices/students': { id: 'students', schoolId: SCHOOL, authorId: 'teacherA', title: 'S', message: 'm', targetRole: 'STUDENT', classId: 'c1', published: true },
      'notices/staff': { id: 'staff', schoolId: SCHOOL, authorId: 'admin', title: 'St', message: 'm', targetRole: 'STAFF', classId: '', published: true },
      'notices/draft': { id: 'draft', schoolId: SCHOOL, authorId: 'admin', title: 'D', message: 'm', targetRole: 'ALL', classId: '', published: false },
      'settings/school_info': { schoolName: 'Discovery Primary School', schoolId: SCHOOL },
    };
    for (const [p, data] of Object.entries(docs)) await setDoc(doc(db, p), data);
  });
}

const as = (uid, token = {}) => env.authenticatedContext(uid, { email: `${uid}@example.org`, ...token }).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const studentsOf = (db) => collection(db, 'students');
const noticeQuery = (db, role) => query(collection(db, 'notices'),
  where('schoolId', '==', SCHOOL), where('published', '==', true), where('targetRole', 'in', ['ALL', role]));

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-discovery-primary',
    firestore: { rules: readFileSync(path.join(here, '../../firestore.rules'), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(); });

describe('unauthenticated', () => {
  test('cannot read or write anything', async () => {
    const db = anon();
    await assertFails(getDoc(doc(db, 'students/s1')));
    await assertFails(getDoc(doc(db, 'classes/c1')));
    await assertFails(getDoc(doc(db, 'notices/all')));
    await assertFails(getDoc(doc(db, 'settings/school_info')));
    await assertFails(setDoc(doc(db, 'users/x'), user('x', 'PARENT', { active: false })));
  });
});

describe('self registration and self-promotion', () => {
  test('new account may register only as an inactive PARENT', async () => {
    const db = as('newbie');
    await assertFails(setDoc(doc(db, 'users/newbie'), user('newbie', 'ADMIN', { active: false })));
    await assertFails(setDoc(doc(db, 'users/newbie'), user('newbie', 'TEACHER', { active: false })));
    await assertFails(setDoc(doc(db, 'users/newbie'), user('newbie', 'STAFF', { active: false })));
    await assertFails(setDoc(doc(db, 'users/newbie'), user('newbie', 'PARENT', { active: true })));
    await assertFails(setDoc(doc(db, 'users/newbie'), user('newbie', 'PARENT', { active: false, email: 'someone-else@example.org' })));
    await assertFails(setDoc(doc(db, 'users/other'), user('other', 'PARENT', { active: false })));
    await assertSucceeds(setDoc(doc(db, 'users/newbie'), user('newbie', 'PARENT', { active: false })));
  });

  test('pending account reads only its own profile', async () => {
    const db = as('parentPending');
    await assertSucceeds(getDoc(doc(db, 'users/parentPending')));
    await assertFails(getDoc(doc(db, 'classes/c1')));
    await assertFails(getDocs(noticeQuery(db, 'PARENT')));
    await assertFails(getDoc(doc(db, 'settings/school_info')));
  });

  test('users cannot change their own role, active flag or school', async () => {
    for (const uid of ['parentA', 'teacherA', 'studentA', 'staff', 'parentPending']) {
      const db = as(uid);
      await assertFails(updateDoc(doc(db, `users/${uid}`), { role: 'ADMIN' }));
      await assertFails(updateDoc(doc(db, `users/${uid}`), { active: true, role: 'ADMIN' }));
      await assertFails(updateDoc(doc(db, `users/${uid}`), { schoolId: 'other' }));
    }
    await assertFails(updateDoc(doc(as('parentPending'), 'users/parentPending'), { active: true }));
  });

  test('users can edit their own display name and phone only', async () => {
    const db = as('parentA');
    await assertSucceeds(updateDoc(doc(db, 'users/parentA'), { displayName: 'New Name', phoneNumber: '+27820000000' }));
    await assertFails(updateDoc(doc(db, 'users/parentA'), { displayName: '' }));
    await assertFails(updateDoc(doc(db, 'users/parentB'), { displayName: 'Hacked' }));
  });
});

describe('admin', () => {
  test('manages users and roles', async () => {
    const db = as('admin');
    await assertSucceeds(getDocs(collection(db, 'users')));
    await assertSucceeds(setDoc(doc(db, 'users/newTeacher'), user('newTeacher', 'TEACHER')));
    await assertSucceeds(updateDoc(doc(db, 'users/parentPending'), { active: true }));
    await assertSucceeds(updateDoc(doc(db, 'users/teacherB'), { active: false }));
    await assertFails(setDoc(doc(db, 'users/bad'), user('bad', 'SUPERUSER')));
    await assertFails(setDoc(doc(db, 'users/bad'), { ...user('bad', 'PARENT'), extra: 1 }));
  });

  test('manages school structure, students, assignments and notices', async () => {
    const db = as('admin');
    await assertSucceeds(getDocs(query(studentsOf(db), where('schoolId', '==', SCHOOL))));
    const batch = writeBatch(db);
    batch.set(doc(db, 'classes/c3'), { id: 'c3', schoolId: SCHOOL, name: 'Grade 6', grade: 'Grade 6', teacherIds: ['teacherA'], subjectIds: [] });
    batch.update(doc(db, 'teachers/teacherA'), { classIds: ['c1', 'c3'] });
    await assertSucceeds(batch.commit());
    await assertSucceeds(setDoc(doc(db, 'students/s3'), { uid: 's3', userId: '', schoolId: SCHOOL, firstName: 'N', lastName: 'S', classId: 'c3', parentIds: ['parentA'] }));
    await assertSucceeds(setDoc(doc(db, 'subjects/eng'), { id: 'eng', schoolId: SCHOOL, name: 'English', code: 'E', teacherIds: [] }));
    await assertSucceeds(setDoc(doc(db, 'assignments/a3'), { id: 'a3', schoolId: SCHOOL, classId: 'c2', subjectId: '', teacherId: 'teacherB', title: 'x', description: '' }));
    await assertSucceeds(setDoc(doc(db, 'notices/n'), { id: 'n', schoolId: SCHOOL, authorId: 'admin', title: 't', message: 'm', targetRole: 'PARENT', classId: '', published: true }));
    await assertSucceeds(getDocs(query(collection(db, 'notices'), where('schoolId', '==', SCHOOL))));
    await assertSucceeds(deleteDoc(doc(db, 'notices/students')));
    await assertSucceeds(deleteDoc(doc(db, 'assignments/a2')));
    await assertSucceeds(setDoc(doc(db, 'settings/school_info'), { schoolName: 'X' }));
    await assertFails(setDoc(doc(db, 'students/other'), { uid: 'other', schoolId: 'another-school', parentIds: [] }));
  });
});

describe('parent', () => {
  test('sees only linked children', async () => {
    const db = as('parentA');
    const snap = await assertSucceeds(getDocs(query(studentsOf(db), where('parentIds', 'array-contains', 'parentA'))));
    if (snap.size !== 1 || snap.docs[0].id !== 's1') throw new Error(`expected only s1, got ${snap.docs.map((d) => d.id)}`);
    await assertSucceeds(getDoc(doc(db, 'students/s1')));
    await assertFails(getDoc(doc(db, 'students/s2')));
    await assertFails(getDocs(query(studentsOf(db), where('schoolId', '==', SCHOOL))));
    await assertFails(getDocs(query(studentsOf(db), where('parentIds', 'array-contains', 'parentB'))));
  });

  test('cannot reach users, admin data or write school records', async () => {
    const db = as('parentA');
    await assertFails(getDoc(doc(db, 'users/parentB')));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(setDoc(doc(db, 'students/s1'), { schoolId: SCHOOL, parentIds: ['parentA'], classId: 'c2' }));
    await assertFails(updateDoc(doc(db, 'students/s2'), { parentIds: ['parentA', 'parentB'] }));
    await assertFails(setDoc(doc(db, 'assignments/x'), { schoolId: SCHOOL, classId: 'c1', teacherId: 'parentA', title: 'x' }));
    await assertFails(setDoc(doc(db, 'notices/x'), { schoolId: SCHOOL, authorId: 'parentA', title: 't', message: 'm', targetRole: 'ALL', classId: '', published: true }));
    await assertFails(setDoc(doc(db, 'classes/c1'), { schoolId: SCHOOL, name: 'x' }));
    await assertFails(setDoc(doc(db, 'settings/school_info'), { schoolName: 'x' }));
  });

  test('reads school structure, assignments and parent notices only', async () => {
    const db = as('parentA');
    await assertSucceeds(getDocs(query(collection(db, 'classes'), where('schoolId', '==', SCHOOL))));
    await assertSucceeds(getDocs(query(collection(db, 'teachers'), where('schoolId', '==', SCHOOL))));
    await assertSucceeds(getDocs(query(collection(db, 'assignments'), where('schoolId', '==', SCHOOL), where('classId', 'in', ['c1']))));
    const notices = await assertSucceeds(getDocs(noticeQuery(db, 'PARENT')));
    const ids = notices.docs.map((d) => d.id).sort().join(',');
    if (ids !== 'all,parents') throw new Error(`parent notices were ${ids}`);
    await assertFails(getDocs(noticeQuery(db, 'TEACHER')));
    await assertFails(getDoc(doc(db, 'notices/teachers')));
    await assertFails(getDoc(doc(db, 'notices/draft')));
  });
});

describe('teacher', () => {
  test('sees only students in assigned classes', async () => {
    const db = as('teacherA');
    const snap = await assertSucceeds(getDocs(query(studentsOf(db), where('schoolId', '==', SCHOOL), where('classId', 'in', ['c1']))));
    if (snap.size !== 1) throw new Error('expected one student');
    await assertSucceeds(getDoc(doc(db, 'students/s1')));
    await assertFails(getDoc(doc(db, 'students/s2')));
    await assertFails(getDocs(query(studentsOf(db), where('schoolId', '==', SCHOOL), where('classId', 'in', ['c1', 'c2']))));
    await assertFails(getDocs(query(studentsOf(db), where('schoolId', '==', SCHOOL))));
  });

  test('creates and edits assignments only for own classes', async () => {
    const db = as('teacherA');
    const base = { schoolId: SCHOOL, subjectId: 'math', description: 'd' };
    await assertSucceeds(setDoc(doc(db, 'assignments/t1'), { ...base, id: 't1', classId: 'c1', teacherId: 'teacherA', title: 'Mine' }));
    await assertFails(setDoc(doc(db, 'assignments/t2'), { ...base, id: 't2', classId: 'c2', teacherId: 'teacherA', title: 'Other class' }));
    await assertFails(setDoc(doc(db, 'assignments/t3'), { ...base, id: 't3', classId: 'c1', teacherId: 'teacherB', title: 'Spoofed' }));
    await assertFails(setDoc(doc(db, 'assignments/t4'), { ...base, id: 't4', classId: 'c1', teacherId: 'teacherA', title: '' }));
    await assertSucceeds(updateDoc(doc(db, 'assignments/a1'), { title: 'Edited' }));
    await assertFails(updateDoc(doc(db, 'assignments/a1'), { classId: 'c2' }));
    await assertFails(updateDoc(doc(db, 'assignments/a2'), { title: 'Hijack' }));
    await assertFails(deleteDoc(doc(db, 'assignments/a2')));
    await assertSucceeds(deleteDoc(doc(db, 'assignments/a1')));
  });

  test('posts class notices only to own classes, students or parents', async () => {
    const db = as('teacherA');
    const n = { schoolId: SCHOOL, authorId: 'teacherA', title: 't', message: 'm', published: true };
    await assertSucceeds(setDoc(doc(db, 'notices/t1'), { ...n, id: 't1', targetRole: 'PARENT', classId: 'c1' }));
    await assertSucceeds(setDoc(doc(db, 'notices/t2'), { ...n, id: 't2', targetRole: 'STUDENT', classId: 'c1' }));
    await assertFails(setDoc(doc(db, 'notices/t3'), { ...n, id: 't3', targetRole: 'ALL', classId: 'c1' }));
    await assertFails(setDoc(doc(db, 'notices/t4'), { ...n, id: 't4', targetRole: 'PARENT', classId: 'c2' }));
    await assertFails(setDoc(doc(db, 'notices/t5'), { ...n, id: 't5', authorId: 'admin', targetRole: 'PARENT', classId: 'c1' }));
    await assertFails(deleteDoc(doc(db, 'notices/all')));
    await assertSucceeds(getDocs(noticeQuery(db, 'TEACHER')));
    await assertSucceeds(getDocs(query(collection(db, 'notices'), where('schoolId', '==', SCHOOL), where('authorId', '==', 'teacherA'))));
    await assertFails(getDocs(noticeQuery(db, 'PARENT')));
  });

  test('cannot use admin functions or change own class list', async () => {
    const db = as('teacherA');
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(updateDoc(doc(db, 'teachers/teacherA'), { classIds: ['c1', 'c2'] }));
    await assertFails(setDoc(doc(db, 'classes/c9'), { schoolId: SCHOOL, name: 'x' }));
    await assertFails(setDoc(doc(db, 'subjects/s9'), { schoolId: SCHOOL, name: 'x' }));
  });

  test('deactivated teacher loses all access', async () => {
    const db = as('teacherOff');
    await assertFails(getDoc(doc(db, 'students/s1')));
    await assertFails(getDoc(doc(db, 'classes/c1')));
    await assertFails(setDoc(doc(db, 'assignments/z'), { schoolId: SCHOOL, classId: 'c1', teacherId: 'teacherOff', title: 'z' }));
  });
});

describe('student', () => {
  test('sees only own record', async () => {
    const db = as('studentA');
    const snap = await assertSucceeds(getDocs(query(studentsOf(db), where('userId', '==', 'studentA'))));
    if (snap.size !== 1) throw new Error('expected own record');
    await assertSucceeds(getDoc(doc(db, 'students/s1')));
    await assertFails(getDoc(doc(db, 'students/s2')));
    await assertFails(getDocs(query(studentsOf(db), where('classId', '==', 'c1'))));
    await assertFails(getDoc(doc(db, 'users/parentA')));
  });

  test('reads student notices, not teacher notices; cannot write', async () => {
    const db = as('studentA');
    await assertSucceeds(getDocs(noticeQuery(db, 'STUDENT')));
    await assertFails(getDoc(doc(db, 'notices/teachers')));
    await assertFails(setDoc(doc(db, 'assignments/x'), { schoolId: SCHOOL, classId: 'c1', teacherId: 'studentA', title: 'x' }));
    await assertFails(updateDoc(doc(db, 'students/s1'), { classId: 'c2' }));
  });
});

describe('staff', () => {
  test('reads directory and staff notices, never students', async () => {
    const db = as('staff');
    await assertSucceeds(getDocs(query(collection(db, 'teachers'), where('schoolId', '==', SCHOOL))));
    await assertSucceeds(getDocs(noticeQuery(db, 'STAFF')));
    await assertFails(getDoc(doc(db, 'students/s1')));
    await assertFails(getDocs(query(studentsOf(db), where('schoolId', '==', SCHOOL))));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(setDoc(doc(db, 'notices/x'), { schoolId: SCHOOL, authorId: 'staff', title: 't', message: 'm', targetRole: 'ALL', classId: '', published: true }));
  });
});
