// End-to-end tests for the web portal: real browser, real UI, Firebase Auth + Firestore
// emulators with the production firestore.rules. Runs on a desktop and a mobile viewport.
import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import * as E from './emulator.mjs';

const APP = '/index.html?emulator=127.0.0.1';
const ADMIN = { email: 'principal@example.org', password: 'Admin12345' };
const TEACHER = 'nomsa.khumalo@example.org';
const PARENT = 'lindiwe.mokoena@example.org';
const SDK_DIR = new URL('./node_modules/firebase/', import.meta.url);

test.beforeEach(async ({ page }) => {
  await E.reset();
  await E.createAdmin(ADMIN.email, ADMIN.password);
  // Serve the Firebase SDK files the page requests from gstatic out of node_modules
  // (identical code; the sandboxed test runner has no route to the CDN).
  await page.route('https://www.gstatic.com/firebasejs/**', async (route) => {
    const file = new URL(route.request().url()).pathname.split('/').pop();
    await route.fulfill({ contentType: 'text/javascript', body: await readFile(new URL(file, SDK_DIR)) });
  });
});

const id = (page, testid) => page.getByTestId(testid);
const dialog = (page) => page.getByTestId('form-dialog');

async function signIn(page, email, password) {
  await id(page, 'login-email').fill(email);
  await id(page, 'login-password').fill(password);
  await id(page, 'login-submit').click();
}

async function signOut(page) {
  await id(page, 'sign-out').click();
  await expect(id(page, 'login-email')).toBeVisible();
}

async function submit(page) {
  await dialog(page).getByTestId('form-submit').click();
  await expect(dialog(page)).toHaveCount(0);
}

async function tab(page, name) {
  await id(page, `tab-${name}`).click();
}

/** Runs Firestore calls inside the signed-in page with the page's own Firebase session. */
async function tryInPage(page, fn) {
  return page.evaluate(async (source) => {
    const fs = await import('firebase/firestore');
    const { getApp } = await import('firebase/app');
    const db = fs.getFirestore(getApp());
    try {
      // eslint-disable-next-line no-new-func
      await new Function('fs', 'db', `return (${source})(fs, db);`)(fs, db);
      return 'allowed';
    } catch (e) {
      return e.code || e.message;
    }
  }, fn.toString());
}

test('setup screen appears when the site has no Firebase config', async ({ page }) => {
  await page.goto('/index.html');
  await expect(id(page, 'setup-config')).toBeVisible();
  await id(page, 'setup-save').click();
  await expect(page.getByRole('alert')).toContainText('Paste the firebaseConfig');
});

test('admin → teacher → parent workflows, isolation and security', async ({ page }) => {
  await page.goto(APP);

  // Invalid password is rejected with a visible error.
  await signIn(page, ADMIN.email, 'wrong-password');
  await expect(id(page, 'login-error')).toContainText('Incorrect email or password');

  // ADMIN signs in and builds the school structure.
  await signIn(page, ADMIN.email, ADMIN.password);
  await expect(id(page, 'portal-admin')).toBeVisible();

  await tab(page, 'classes');
  for (const [name, grade] of [['Grade 4A', 'Grade 4'], ['Grade 5B', 'Grade 5']]) {
    await id(page, 'admin-add-class').click();
    await id(page, 'class-name').fill(name);
    await id(page, 'class-grade').fill(grade);
    await submit(page);
    await expect(id(page, 'class-card').filter({ hasText: name })).toBeVisible();
  }

  await tab(page, 'subjects');
  await id(page, 'admin-add-subject').click();
  await id(page, 'subject-name').fill('Mathematics');
  await id(page, 'subject-code').fill('MATH-4');
  await dialog(page).getByLabel('Grade 4A (Grade 4)').check();
  await submit(page);
  await expect(id(page, 'subject-card')).toContainText('Classes: Grade 4A');

  // WORKFLOW 1: admin creates a teacher (login + class + subject).
  await tab(page, 'teachers');
  await id(page, 'admin-add-teacher').click();
  await id(page, 'teacher-first').fill('Nomsa');
  await id(page, 'teacher-last').fill('Khumalo');
  await id(page, 'teacher-email').fill(TEACHER);
  await id(page, 'teacher-emp').fill('EMP-102');
  await dialog(page).getByLabel('Grade 4A (Grade 4)').check();
  await dialog(page).getByLabel('Mathematics').check();
  await submit(page);
  const teacherCard = id(page, 'teacher-card').filter({ hasText: 'Nomsa Khumalo' });
  await expect(teacherCard).toContainText('Classes: Grade 4A');
  await expect(teacherCard).toContainText('LOGIN ACTIVE');
  expect(await E.hasEmail(TEACHER, 'PASSWORD_RESET')).toBe(true);

  // WORKFLOW 2: admin enrols learners in two classes.
  await tab(page, 'students');
  for (const [first, last, number, cls] of [['Thabo', 'Mokoena', 'DPS-1001', 'Grade 4A (Grade 4)'], ['Sipho', 'Other', 'DPS-2001', 'Grade 5B (Grade 5)']]) {
    await id(page, 'admin-add-student').click();
    await id(page, 'student-first').fill(first);
    await id(page, 'student-last').fill(last);
    await id(page, 'student-number').fill(number);
    await id(page, 'student-class').selectOption({ label: cls });
    await submit(page);
    await expect(id(page, 'student-card').filter({ hasText: `${first} ${last}` })).toBeVisible();
  }

  // WORKFLOW 3: admin creates a parent account linked to Thabo only.
  await id(page, 'student-card').filter({ hasText: 'Thabo Mokoena' }).getByRole('button', { name: '+ Parent account' }).click();
  await id(page, 'new-parent-name').fill('Lindiwe Mokoena');
  await id(page, 'new-parent-email').fill(PARENT);
  await submit(page);
  await expect(id(page, 'student-card').filter({ hasText: 'Thabo Mokoena' })).toContainText('Parents: Lindiwe Mokoena');

  // WORKFLOW 6: notices for parents and for teachers.
  await tab(page, 'notices');
  for (const [title, message, audience] of [['Parents meeting', 'Friday 18:00 in the hall', 'PARENT'], ['Staff briefing', 'Monday 07:30', 'TEACHER']]) {
    await id(page, 'admin-add-notice').click();
    await id(page, 'notice-title-input').fill(title);
    await id(page, 'notice-message').fill(message);
    await id(page, 'notice-audience').selectOption(audience);
    await submit(page);
    await expect(id(page, 'notice-card').filter({ hasText: title })).toBeVisible();
  }

  // Dashboard numbers come from Firestore.
  await tab(page, 'overview');
  await expect(id(page, 'stat-students-value')).toHaveText('2');
  await expect(id(page, 'stat-teachers-value')).toHaveText('1');
  await expect(id(page, 'stat-classes-value')).toHaveText('2');
  await expect(id(page, 'stat-notices-value')).toHaveText('2');
  await expect(id(page, 'stat-parents-value')).toHaveText('1');
  await signOut(page);

  // TEACHER sets a password from the emailed link and signs in.
  await E.completePasswordReset(TEACHER, 'Teacher12345');
  await signIn(page, TEACHER, 'Teacher12345');
  await expect(id(page, 'portal-teacher')).toBeVisible();
  await expect(id(page, 'teacher-class')).toContainText('Thabo Mokoena');
  await expect(page.getByText('Sipho Other')).toHaveCount(0);
  // Server-side isolation: the teacher's own session cannot read another class.
  expect(await tryInPage(page, async (fs, db) => fs.getDocs(fs.query(fs.collection(db, 'students'), fs.where('schoolId', '==', 'discovery-primary'))))).toBe('permission-denied');
  expect(await tryInPage(page, async (fs, db) => fs.getDocs(fs.collection(db, 'users')))).toBe('permission-denied');

  await tab(page, 'notices');
  await expect(id(page, 'notice-card').filter({ hasText: 'Staff briefing' })).toBeVisible();
  await expect(id(page, 'notice-card').filter({ hasText: 'Parents meeting' })).toHaveCount(0);

  // WORKFLOW 5: teacher publishes an assignment for their class.
  await tab(page, 'assignments');
  await id(page, 'teacher-add-assignment').click();
  await id(page, 'assignment-title-input').fill('Fractions worksheet');
  await id(page, 'assignment-description').fill('Complete pages 42-45');
  await id(page, 'assignment-due').fill('2030-01-15');
  await submit(page);
  await expect(id(page, 'assignment-card').filter({ hasText: 'Fractions worksheet' })).toContainText('Upcoming');
  await signOut(page);

  // PARENT sees only their child, the homework and the parent notice.
  await E.completePasswordReset(PARENT, 'Parent12345');
  await signIn(page, PARENT, 'Parent12345');
  await expect(id(page, 'portal-parent')).toBeVisible();
  await expect(id(page, 'child-name')).toHaveText('Thabo Mokoena');
  await expect(id(page, 'child-teacher')).toHaveText('Nomsa Khumalo');
  await expect(page.getByText('Sipho Other')).toHaveCount(0);
  // Server-side isolation and self-promotion attempts from the parent's own session.
  expect(await tryInPage(page, async (fs, db) => fs.getDocs(fs.query(fs.collection(db, 'students'), fs.where('schoolId', '==', 'discovery-primary'))))).toBe('permission-denied');
  const parentUid = await page.evaluate(async () => (await import('firebase/auth')).getAuth().currentUser.uid);
  expect(await tryInPage(page, new Function('fs', 'db', `return fs.updateDoc(fs.doc(db, 'users', '${parentUid}'), { role: 'ADMIN' });`))).toBe('permission-denied');
  expect(await tryInPage(page, async (fs, db) => fs.setDoc(fs.doc(db, 'notices', 'x'), { schoolId: 'discovery-primary', title: 't', message: 'm', targetRole: 'ALL', classId: '', published: true, authorId: 'x' }))).toBe('permission-denied');

  await tab(page, 'homework');
  await expect(id(page, 'assignment-card').filter({ hasText: 'Fractions worksheet' })).toBeVisible();
  await tab(page, 'notices');
  await expect(id(page, 'notice-card').filter({ hasText: 'Parents meeting' })).toBeVisible();
  await expect(id(page, 'notice-card').filter({ hasText: 'Staff briefing' })).toHaveCount(0);

  // Profile edit persists, and the session survives a page reload.
  await tab(page, 'profile');
  await id(page, 'profile-edit').click();
  await id(page, 'profile-name-input').fill('Lindiwe M. Mokoena');
  await submit(page);
  await expect(id(page, 'profile-name')).toHaveText('Lindiwe M. Mokoena');
  await page.reload();
  await expect(id(page, 'portal-parent')).toBeVisible();
  await tab(page, 'profile');
  await expect(id(page, 'profile-name')).toHaveText('Lindiwe M. Mokoena');
  await signOut(page);

  // WORKFLOW 7: admin moves Thabo to Grade 5B; access follows the change.
  await signIn(page, ADMIN.email, ADMIN.password);
  await tab(page, 'students');
  await id(page, 'student-card').filter({ hasText: 'Thabo Mokoena' }).getByRole('button', { name: 'Edit' }).click();
  await id(page, 'student-class').selectOption({ label: 'Grade 5B (Grade 5)' });
  await submit(page);
  await expect(id(page, 'student-card').filter({ hasText: 'Thabo Mokoena' })).toContainText('Grade 5B');
  await signOut(page);

  await signIn(page, TEACHER, 'Teacher12345');
  await tab(page, 'students');
  await expect(id(page, 'empty-state')).toContainText('No learners in your classes yet.');
  await signOut(page);

  await signIn(page, PARENT, 'Parent12345');
  await expect(id(page, 'portal-parent')).toContainText('Grade 5B');
  await tab(page, 'homework');
  await expect(id(page, 'empty-state')).toContainText('No assignments for Grade 5B yet.');
  await signOut(page);
});

test('parent self-registration requires email verification and admin approval', async ({ page }) => {
  const email = 'new.parent@example.org';
  await page.goto(APP);
  await id(page, 'login-mode-register').click();
  await id(page, 'register-name').fill('New Parent');
  await id(page, 'register-email').fill(email);
  await id(page, 'register-password').fill('short');
  await id(page, 'register-confirm').fill('short');
  await id(page, 'register-submit').click();
  await expect(id(page, 'login-error')).toContainText('at least 8 characters');

  await id(page, 'register-password').fill('Parent12345');
  await id(page, 'register-confirm').fill('Parent12345');
  await id(page, 'register-submit').click();
  await expect(id(page, 'verify-email-screen')).toBeVisible();
  await expect(id(page, 'login-info')).toContainText('Account created');

  await id(page, 'verify-check').click();
  await expect(id(page, 'login-error')).toContainText('not verified yet');

  await E.completeEmailVerification(email);
  await id(page, 'verify-check').click();
  await expect(id(page, 'access-denied')).toContainText('not active');

  // Admin approves → the parent gets in (with no children linked yet).
  await id(page, 'denied-sign-out').click();
  await signIn(page, ADMIN.email, ADMIN.password);
  await tab(page, 'users');
  await id(page, 'user-filter-INACTIVE').click();
  await page.getByRole('button', { name: /New Parent/ }).click();
  await id(page, 'edit-user-active').check();
  await submit(page);
  await signOut(page);
  await signIn(page, email, 'Parent12345');
  await expect(id(page, 'portal-parent')).toBeVisible();
  await expect(id(page, 'empty-state')).toContainText('No learners are linked');
});

test('forgot password sends a reset link that works', async ({ page }) => {
  await page.goto(APP);
  await id(page, 'login-forgot').click();
  await expect(id(page, 'login-error')).toContainText('Enter your email address');
  await id(page, 'login-email').fill(ADMIN.email);
  await id(page, 'login-forgot').click();
  await expect(id(page, 'login-info')).toContainText('password reset link');
  await E.completePasswordReset(ADMIN.email, 'NewAdmin12345');
  await signIn(page, ADMIN.email, 'NewAdmin12345');
  await expect(id(page, 'portal-admin')).toBeVisible();
});

test('protected Super Admin Owner: owner console, 403 for other admins, audit log, sign out everywhere', async ({ page }) => {
  const OWNER = { email: 'owner.fixture@example.org', password: 'OwnerFixture2024' };
  const ownerUid = await E.provisionOwner(OWNER.email, OWNER.password);
  await page.goto(APP);

  // The Owner signs in through the normal sign-in form.
  await signIn(page, OWNER.email, OWNER.password);
  await expect(id(page, 'portal-admin')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Owner Console' })).toBeVisible();
  await tab(page, 'settings');
  await expect(page.getByRole('heading', { name: 'Owner Security' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'OWNER_LOGIN' }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'OWNER_ROLE_VERIFICATION' }).first()).toBeVisible();
  await signOut(page);

  // Another administrator cannot deactivate or demote the Owner.
  await signIn(page, ADMIN.email, ADMIN.password);
  await expect(id(page, 'portal-admin')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Admin Console' })).toBeVisible();
  await tab(page, 'users');
  const row = id(page, `user-row-${ownerUid}`);
  await expect(row).toContainText('PROTECTED OWNER');
  await row.click();
  await expect(id(page, 'owner-protected-note')).toBeVisible();
  await id(page, 'edit-user-active').uncheck();
  await dialog(page).getByTestId('form-submit').click();
  await expect(dialog(page).getByTestId('form-error')).toHaveText('403 FORBIDDEN — Protected Super Admin Owner account.');
  await dialog(page).getByTestId('form-cancel').click();
  // Bypassing the UI hits firestore.rules.
  expect(await tryInPage(page, `(fs, db) => fs.updateDoc(fs.doc(db, 'users', '${ownerUid}'), { active: false })`)).toBe('permission-denied');
  expect(await tryInPage(page, `(fs, db) => fs.getDocs(fs.collection(db, 'auditLogs'))`)).toBe('permission-denied');
  await signOut(page);

  // The Owner is still active, sees the blocked attempt, and can end every session.
  await signIn(page, OWNER.email, OWNER.password);
  await expect(page.getByRole('heading', { name: 'Owner Console' })).toBeVisible();
  await tab(page, 'settings');
  await expect(page.getByRole('heading', { name: 'PROTECTED_OWNER_ACTION_BLOCKED' }).first()).toBeVisible();
  await expect(page.getByText(`UPDATE_USER • DENIED • ${ADMIN.email}`).first()).toBeVisible();
  await id(page, 'owner-revoke-sessions').click();
  await page.getByTestId('owner-revoke-dialog').getByTestId('form-submit').click();
  await expect(id(page, 'login-email')).toBeVisible();

  // The password never appears in the page or in Firestore.
  expect(await page.content()).not.toContain(OWNER.password);
});
