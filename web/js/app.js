// Discover PS web portal: same Firebase project, data and security rules as the Android app.
import { initFirebase, configSource, parseConfigText, saveConfig, forgetSavedConfig } from './firebase.js';
import * as data from './data.js';
import * as A from './access.js';
import {
  el, text, pill, button, iconButton, loading, empty, errorBox, remoteList, card, labeled,
  field, select, multiSelect, checkbox, openForm, confirmDialog,
} from './ui.js';

const services = initFirebase();
const root = document.getElementById('app');
const blank = (isLoading = false) => ({ items: [], loading: isLoading, error: null });

const TABS = {
  ADMIN: [['overview', 'Overview'], ['users', 'Users & Parents'], ['teachers', 'Teachers'], ['students', 'Students'], ['classes', 'Classes'], ['subjects', 'Subjects'], ['assignments', 'Assignments'], ['notices', 'Notices'], ['settings', 'Settings']],
  TEACHER: [['classes', 'My Classes'], ['students', 'My Students'], ['assignments', 'Assignments'], ['notices', 'Notices'], ['profile', 'Profile']],
  PARENT: [['children', 'My Children'], ['homework', 'Homework & Tasks'], ['notices', 'School Notices'], ['profile', 'Parent Profile']],
  STUDENT: [['subjects', 'My Subjects'], ['assignments', 'Assignments'], ['notices', 'Notices'], ['profile', 'My Profile']],
  STAFF: [['notices', 'School Notices'], ['directory', 'Staff Directory'], ['profile', 'Profile']],
};

const emptyLogin = () => ({ email: '', password: '', name: '', regEmail: '', regPassword: '', regConfirm: '', phone: '', code: '' });

const state = {
  session: services ? { kind: 'initializing' } : { kind: 'notConfigured' },
  authBusy: false,
  authMessage: null,
  loginMode: 'signin',
  login: emptyLogin(),
  phoneConfirmation: null,
  school: Object.fromEntries(data.COLLECTIONS.map((k) => [k, blank()])),
  tab: Object.fromEntries(Object.entries(TABS).map(([role, tabs]) => [role, tabs[0][0]])),
  status: null,
  ui: { userFilter: 'ALL', userSearch: '', teacherSearch: '', studentSearch: '', studentClass: 'ALL', assignmentClass: 'ALL', myStudentSearch: '', childId: '' },
};

// ---------- rendering ----------

let frame = 0;
function render() {
  if (frame) return;
  frame = requestAnimationFrame(() => {
    frame = 0;
    const active = document.activeElement;
    const key = active && root.contains(active) ? active.dataset.key : null;
    const selection = key && 'selectionStart' in active ? [active.selectionStart, active.selectionEnd] : null;
    root.replaceChildren(view());
    if (key) {
      const again = root.querySelector(`[data-key="${key}"]`);
      if (again) {
        again.focus();
        if (selection && again.setSelectionRange) try { again.setSelectionRange(...selection); } catch { /* not a text input */ }
      }
    }
  });
}

function view() {
  const s = state.session;
  switch (s.kind) {
    case 'notConfigured': return setupScreen();
    case 'initializing': return splash();
    case 'signedOut': return loginScreen();
    case 'verifyEmail': return verifyScreen(s.email);
    case 'denied': return deniedScreen(s.reason);
    case 'portal': return portal(s.user, s.role);
    default: return splash();
  }
}

// ---------- session ----------

let stopListeners = null;
let listenerKey = null;
let watcher = null;

function stopData() {
  stopListeners?.();
  stopListeners = null;
  listenerKey = null;
  state.school = Object.fromEntries(data.COLLECTIONS.map((k) => [k, blank()]));
}

function startData(uid, role, owner = false) {
  const key = `${uid}:${role}:${owner}`;
  if (listenerKey === key) return;
  stopData();
  listenerKey = key;
  const needed = {
    ADMIN: ['users', 'teachers', 'students', 'classes', 'subjects', 'assignments', 'notices', 'settings'],
    TEACHER: ['teacherProfile', 'teachers', 'students', 'classes', 'subjects', 'assignments', 'notices', 'settings'],
    PARENT: ['students', 'teachers', 'classes', 'subjects', 'assignments', 'notices', 'settings'],
    STUDENT: ['students', 'teachers', 'classes', 'subjects', 'assignments', 'notices', 'settings'],
    STAFF: ['teachers', 'notices', 'settings'],
  }[role].concat(owner ? ['auditLogs'] : []);
  needed.forEach((k) => { state.school[k] = blank(true); });
  stopListeners = data.startRoleListeners(services, uid, role, (k, remote) => {
    if (listenerKey !== key) return;
    state.school[k] = remote;
    render();
  }, { owner });
}

function onSession(s) {
  if (s.kind === 'ready') {
    const role = A.portalRole(s.user.role);
    if (!role) {
      stopData();
      state.session = { kind: 'denied', reason: `Your account has no valid role ('${s.user.role}'). Contact the school administrator.` };
    } else if (s.user.active !== true) {
      stopData();
      state.session = { kind: 'denied', reason: 'Your account is not active. New registrations must be approved by the school administrator before you can see school information. If your account was deactivated, please contact the school office.' };
    } else {
      startData(s.user.uid, role, A.isOwner(s.user));
      state.login = emptyLogin();
      state.session = { kind: 'portal', user: s.user, role };
    }
  } else {
    stopData();
    if (s.kind !== 'signedOut') { state.login.password = ''; state.login.regPassword = ''; state.login.regConfirm = ''; }
    if (s.kind === 'noProfile') state.session = { kind: 'denied', reason: 'This login has no Discovery Primary profile. Ask the school administrator to create or link your account.' };
    else if (s.kind === 'failed') state.session = { kind: 'denied', reason: s.message };
    else state.session = s;
  }
  render();
}

if (services) watcher = data.watchSession(services, onSession);

async function signOut() {
  watcher?.stopProfile();
  stopData();
  state.status = null;
  state.authMessage = null;
  state.phoneConfirmation = null;
  state.loginMode = 'signin';
  state.login = emptyLogin();
  state.ui.childId = '';
  Object.keys(state.tab).forEach((role) => { state.tab[role] = TABS[role][0][0]; });
  await data.signOut(services).catch(() => {});
  state.session = { kind: 'signedOut' };
  render();
}

// ---------- actions ----------

async function authAction(fn, success) {
  if (state.authBusy) return;
  state.authBusy = true;
  state.authMessage = null;
  render();
  try {
    await fn();
    if (success) state.authMessage = { text: success, error: false };
  } catch (e) {
    state.authMessage = { text: data.friendlyError(e), error: true };
  }
  state.authBusy = false;
  render();
}

function setStatus(text, error = false) {
  state.status = { text, error };
  render();
}

/** Runs a write, reports the real outcome, and rethrows a friendly error for dialogs. */
async function save(fn, success) {
  try {
    await fn();
    setStatus(success);
  } catch (e) {
    const message = data.friendlyError(e);
    setStatus(message, true);
    throw Object.assign(new Error(message), { friendly: message });
  }
}

const quiet = (promise) => promise.catch(() => {});

// ---------- shared pieces ----------

function bound(obj, key, attrs = {}) {
  const input = el('input', { ...attrs, 'data-key': `${attrs['data-testid'] ?? key}` });
  input.value = obj[key] ?? '';
  input.addEventListener('input', () => { obj[key] = input.value; });
  return input;
}

function searchBox(key, placeholder, testid) {
  const input = el('input', { type: 'search', class: 'search', placeholder, 'data-testid': testid, 'data-key': testid });
  input.value = state.ui[key];
  input.addEventListener('input', () => { state.ui[key] = input.value; render(); });
  return input;
}

function chips(choices, current, onPick, testid) {
  return el('div', { class: 'chips', role: 'tablist', 'data-testid': testid },
    choices.map((c) => el('button', { type: 'button', class: `chip${c.id === current ? ' chip-on' : ''}`, 'data-testid': `${testid}-${c.id}`, onclick: () => onPick(c.id) }, c.label)));
}

function banner() {
  if (!state.status) return null;
  const { text: t, error } = state.status;
  return el('div', { class: `banner ${error ? 'banner-error' : 'banner-ok'}`, role: 'status', 'data-testid': error ? 'status-error' : 'status-success' },
    el('span', {}, t), iconButton('Dismiss', '×', () => { state.status = null; render(); }, 'dismiss-status'));
}

function header(title, badge, tone, subtitle) {
  return el('header', { class: 'topbar' },
    el('div', { class: 'brand' },
      el('div', { class: 'crest', 'aria-hidden': 'true' }, 'DP'),
      el('div', {}, el('div', { class: 'title-row' }, text('h1', '', title), pill(badge, tone)), text('div', 'subtitle', subtitle))),
    button('Sign out', signOut, { variant: 'light', testid: 'sign-out', small: true }));
}

function tabsNav(role) {
  return el('nav', { class: 'tabs', 'aria-label': 'Sections' },
    TABS[role].map(([id, label]) => el('button', {
      type: 'button', class: `tab${state.tab[role] === id ? ' tab-on' : ''}`, 'data-testid': `tab-${id}`,
      'aria-current': state.tab[role] === id ? 'page' : null,
      onclick: () => { state.tab[role] = id; state.status = null; render(); },
    }, label)));
}

function section(title, action, ...children) {
  return el('section', { class: 'panel' }, el('div', { class: 'panel-head' }, text('h2', '', title), action), ...children);
}

const S = () => state.school;
const byId = (items, key = 'id') => new Map(items.map((x) => [x[key], x]));
const classChoices = () => [...S().classes.items].sort((a, b) => a.name.localeCompare(b.name)).map((c) => ({ id: c.id, label: `${c.name} (${c.grade})` }));
const subjectChoices = () => [...S().subjects.items].sort((a, b) => a.name.localeCompare(b.name)).map((s) => ({ id: s.id, label: s.name }));
const teacherChoices = () => [...S().teachers.items].sort((a, b) => A.fullName(a).localeCompare(A.fullName(b))).map((t) => ({ id: t.uid, label: A.fullName(t) }));
const count = (remote, pred = () => true) => (remote.error ? '!' : remote.loading ? '…' : String(remote.items.filter(pred).length));

function assignmentCard(a, { onEdit, onDelete } = {}) {
  const status = A.dueStatus(a.dueDate);
  const tone = { [A.DUE.OVERDUE]: 'red', [A.DUE.DUE_TODAY]: 'amber', [A.DUE.UPCOMING]: 'green', [A.DUE.NO_DUE_DATE]: 'neutral' }[status];
  const cls = byId(S().classes.items).get(a.classId);
  const subj = byId(S().subjects.items).get(a.subjectId);
  const teacher = byId(S().teachers.items, 'uid').get(a.teacherId);
  return el('article', { class: 'card', 'data-testid': 'assignment-card' },
    el('div', { class: 'card-head' }, text('h3', 'accent', a.title, { 'data-testid': 'assignment-title' }), actions(onEdit, onDelete, `assignment-${a.id}`)),
    a.description ? text('p', '', a.description) : null,
    text('div', 'muted', [cls?.name, subj?.name, teacher ? `by ${A.fullName(teacher)}` : null].filter(Boolean).join(' • ') || '—'),
    el('div', { class: 'row' }, text('strong', 'small', `Due: ${A.formatDate(a.dueDate)}`), pill(status, tone)));
}

function noticeCard(n, { showAudience = false, onEdit, onDelete } = {}) {
  const cls = n.classId ? byId(S().classes.items).get(n.classId) : null;
  return el('article', { class: 'card', 'data-testid': 'notice-card' },
    el('div', { class: 'card-head' }, text('h3', '', n.title, { 'data-testid': 'notice-title' }), actions(onEdit, onDelete, `notice-${n.id}`)),
    text('p', '', n.message),
    el('div', { class: 'row' },
      showAudience ? pill(n.targetRole === 'ALL' ? 'EVERYONE' : n.targetRole, 'blue') : null,
      cls ? pill(cls.name, 'amber') : null,
      n.published ? null : pill('DRAFT', 'neutral'),
      text('small', 'muted', A.formatDateTime(n.createdAt))));
}

function actions(onEdit, onDelete, id) {
  if (!onEdit && !onDelete) return null;
  return el('div', { class: 'actions' },
    onEdit ? iconButton('Edit', '✎', onEdit, `edit-${id}`) : null,
    onDelete ? iconButton('Delete', '🗑', onDelete, `delete-${id}`, 'red') : null);
}

// ---------- auth screens ----------

function authShell(...content) {
  return el('div', { class: 'auth' },
    el('div', { class: 'auth-hero' }, el('div', { class: 'crest big', 'aria-hidden': 'true' }, 'DP'),
      text('h1', '', 'DISCOVERY PRIMARY SCHOOL'), text('p', '', 'LMS & Academic Operations Platform')),
    el('div', { class: 'auth-card' }, ...content));
}

function authMessage() {
  const m = state.authMessage;
  if (!m) return null;
  return el('div', { class: `notice-box ${m.error ? 'err' : 'ok'}`, role: 'alert', 'data-testid': m.error ? 'login-error' : 'login-info' }, m.text);
}

function loginScreen() {
  const modes = [['signin', 'Sign In'], ['register', 'Register'], ['phone', 'Phone OTP']];
  const tabs = el('div', { class: 'tabs auth-tabs' }, modes.map(([id, label]) => el('button', {
    type: 'button', class: `tab${state.loginMode === id ? ' tab-on' : ''}`, 'data-testid': `login-mode-${id}`,
    onclick: () => { state.loginMode = id; state.authMessage = null; render(); },
  }, label)));
  const L = state.login;
  let body;
  if (state.loginMode === 'signin') {
    body = el('form', { class: 'stack', novalidate: true, onsubmit: (e) => { e.preventDefault(); authAction(() => data.signIn(services, L.email, L.password)); } },
      text('h2', '', 'Email & Password Sign In'),
      el('label', { class: 'field' }, el('span', {}, 'School email'), bound(L, 'email', { type: 'email', autocomplete: 'username', 'data-testid': 'login-email' })),
      el('label', { class: 'field' }, el('span', {}, 'Password'), bound(L, 'password', { type: 'password', autocomplete: 'current-password', 'data-testid': 'login-password' })),
      button(state.authBusy ? 'Signing in…' : 'Sign In', null, { type: 'submit', testid: 'login-submit', disabled: state.authBusy }),
      button('Forgot password?', () => {
        if (A.validate.email(L.email)) { state.authMessage = { text: 'Enter your email address above, then tap "Forgot password".', error: true }; render(); return; }
        authAction(() => data.sendReset(services, L.email), `If an account exists for ${L.email.trim()}, a password reset link has been sent.`);
      }, { variant: 'link', testid: 'login-forgot', disabled: state.authBusy }));
  } else if (state.loginMode === 'register') {
    body = el('form', { class: 'stack', novalidate: true, onsubmit: (e) => {
      e.preventDefault();
      const problem = A.validate.required(L.name, 'Full name') || A.validate.email(L.regEmail) || A.validate.password(L.regPassword) || (L.regPassword !== L.regConfirm ? 'Passwords do not match.' : null);
      if (problem) { state.authMessage = { text: problem, error: true }; render(); return; }
      authAction(() => data.registerParent(services, L.name, L.regEmail, L.regPassword), 'Account created. Check your inbox to verify your email address.');
    } },
      text('h2', '', 'Parent / Guardian Registration'),
      text('p', 'muted', 'Creates a parent account. After you verify your email, the school administrator approves the account and links your children. Teacher, staff and learner accounts are issued by the school.'),
      el('label', { class: 'field' }, el('span', {}, 'Full name'), bound(L, 'name', { autocomplete: 'name', 'data-testid': 'register-name' })),
      el('label', { class: 'field' }, el('span', {}, 'Email address'), bound(L, 'regEmail', { type: 'email', autocomplete: 'email', 'data-testid': 'register-email' })),
      el('label', { class: 'field' }, el('span', {}, `Password (min ${A.MIN_PASSWORD_LENGTH}, letters + numbers)`), bound(L, 'regPassword', { type: 'password', autocomplete: 'new-password', 'data-testid': 'register-password' })),
      el('label', { class: 'field' }, el('span', {}, 'Confirm password'), bound(L, 'regConfirm', { type: 'password', autocomplete: 'new-password', 'data-testid': 'register-confirm' })),
      button(state.authBusy ? 'Creating account…' : 'Create Parent Account', null, { type: 'submit', testid: 'register-submit', disabled: state.authBusy }));
  } else {
    body = el('form', { class: 'stack', novalidate: true, onsubmit: (e) => {
      e.preventDefault();
      if (state.phoneConfirmation) {
        authAction(() => data.confirmPhoneCode(services, state.phoneConfirmation, L.code.trim()));
      } else {
        const phone = A.normalizeSouthAfricanPhone(L.phone);
        const problem = !phone ? 'Enter your mobile number.' : A.validate.phone(phone);
        if (problem) { state.authMessage = { text: problem, error: true }; render(); return; }
        authAction(async () => { state.phoneConfirmation = await data.sendPhoneCode(services, phone, 'recaptcha-container'); }, `A 6-digit code was sent to ${phone}.`);
      }
    } },
      text('h2', '', 'Phone Number SMS Sign In'),
      text('p', 'muted', 'For parents without email. Enter a South African mobile number (e.g. 082 123 4567). New phone accounts must be approved by the school administrator.'),
      el('label', { class: 'field' }, el('span', {}, 'Mobile number'), bound(L, 'phone', { type: 'tel', autocomplete: 'tel', 'data-testid': 'phone-number' })),
      state.phoneConfirmation ? el('label', { class: 'field' }, el('span', {}, '6-digit code'), bound(L, 'code', { inputmode: 'numeric', maxlength: 6, 'data-testid': 'phone-code' })) : null,
      button(state.authBusy ? 'Please wait…' : state.phoneConfirmation ? 'Verify code & sign in' : 'Send SMS code', null, { type: 'submit', testid: 'phone-submit', disabled: state.authBusy }));
  }
  return authShell(tabs, authMessage(), body);
}

function verifyScreen(email) {
  return authShell(
    text('h2', '', 'Verify your email'),
    text('p', '', `We sent a verification link to ${email}. Open it, then press "I've verified my email".`),
    authMessage(),
    el('div', { class: 'stack', 'data-testid': 'verify-email-screen' },
      button(state.authBusy ? 'Checking…' : "I've verified my email", () => authAction(async () => {
        const verified = await data.refreshVerification(services);
        if (!verified) throw new Error('Your email address is not verified yet.');
        watcher.reevaluate();
      }), { testid: 'verify-check', disabled: state.authBusy }),
      button('Resend verification email', () => authAction(() => data.resendVerification(services), 'Verification email sent. Check your inbox (and spam folder).'), { variant: 'ghost', testid: 'verify-resend', disabled: state.authBusy }),
      button('Sign out', signOut, { variant: 'ghost' })));
}

function deniedScreen(reason) {
  return authShell(
    el('div', { class: 'stack', 'data-testid': 'access-denied' },
      text('h2', 'danger', 'Access Denied'),
      text('p', '', reason),
      button('Return to Sign In', signOut, { variant: 'danger', testid: 'denied-sign-out' })));
}

function splash() {
  return el('div', { class: 'splash' }, el('div', { class: 'crest big' }, 'DP'), loading('Starting Discovery Primary…'));
}

function setupScreen() {
  const area = el('textarea', { rows: 8, 'data-testid': 'setup-config', placeholder: 'const firebaseConfig = {\n  apiKey: "…",\n  authDomain: "…",\n  projectId: "…",\n  appId: "…"\n};' });
  const err = el('div', { class: 'notice-box err', hidden: true, role: 'alert' });
  return authShell(
    text('h2', '', 'Connect this website to the school Firebase project'),
    el('ol', { class: 'steps' },
      el('li', {}, 'Firebase console → Project settings → Your apps → add a Web app (or open the existing one).'),
      el('li', {}, 'Recommended: copy web/firebase-config.example.js to web/firebase-config.js, paste the config there and redeploy.'),
      el('li', {}, 'Or paste the firebaseConfig below to use it in this browser only.'),
      el('li', {}, 'Add this site\'s domain under Authentication → Settings → Authorized domains.')),
    err, area,
    button('Save and connect', () => {
      try {
        saveConfig(parseConfigText(area.value));
        location.reload();
      } catch (e) {
        err.textContent = e.message;
        err.hidden = false;
      }
    }, { testid: 'setup-save' }),
    text('p', 'muted small', 'The Firebase web config is a public identifier, not a password. School data stays protected by sign-in and the Firestore security rules.'));
}

// ---------- portal shell ----------

function portal(user, role) {
  const titles = {
    ADMIN: ['Admin Console', 'ADMIN', 'gold'],
    TEACHER: ['Teacher Portal', 'TEACHER', 'green'],
    PARENT: ['Parent Portal', 'PARENT', 'purple'],
    STUDENT: ['Student Portal', 'STUDENT', 'blue'],
    STAFF: ['Support Staff Portal', 'STAFF', 'teal'],
  };
  const [title, badge, tone] = A.isOwner(user) ? ['Owner Console', 'OWNER', 'gold'] : titles[role];
  const tab = state.tab[role];
  const content = {
    ADMIN: adminView, TEACHER: teacherView, PARENT: parentView, STUDENT: studentView, STAFF: staffView,
  }[role](user, tab);
  return el('div', { class: 'portal', 'data-testid': `portal-${role.toLowerCase()}` },
    header(title, badge, tone, A.userLabel(user)), banner(), tabsNav(role), el('main', { class: 'content' }, content));
}

function profileView(user, tone, details = []) {
  return section('My Profile', null,
    card(
      el('div', { class: 'profile-head' }, el('div', { class: `avatar avatar-${tone}` }, (user.displayName || '?').slice(0, 2).toUpperCase()),
        el('div', {}, text('h3', '', user.displayName || 'Name not set', { 'data-testid': 'profile-name' }), text('div', 'muted', user.email || user.phoneNumber), pill(user.role, tone))),
      labeled('Phone', user.phoneNumber), details.map(([k, v]) => labeled(k, v))),
    el('div', { class: 'row wrap' },
      button('Edit name & phone', () => {
        const name = field('Full name', { value: user.displayName, testid: 'profile-name-input', validate: (v) => A.validate.required(v, 'Name') });
        const phone = field('Phone number', { value: user.phoneNumber, testid: 'profile-phone-input', type: 'tel', validate: A.validate.phone });
        openForm({ title: 'Edit profile', body: [name.node, phone.node],
          onSubmit: () => save(() => data.updateOwnProfile(services, user.uid, name.get(), phone.get()), 'Profile updated') });
      }, { testid: 'profile-edit' }),
      user.email ? button('Change password (email me a secure link)', () => quiet(save(() => data.sendReset(services, user.email), `Password reset link sent to ${user.email}`)), { variant: 'ghost', testid: 'profile-reset' }) : null,
      button('Sign out', signOut, { variant: 'danger', testid: 'profile-sign-out' })));
}

// ---------- ADMIN ----------

function adminView(user, tab) {
  switch (tab) {
    case 'overview': return adminOverview();
    case 'users': return adminUsers(user);
    case 'teachers': return adminTeachers();
    case 'students': return adminStudents();
    case 'classes': return adminClasses();
    case 'subjects': return adminSubjects();
    case 'assignments': return adminAssignments();
    case 'notices': return adminNotices(user);
    case 'settings': return adminSettings(user);
    default: return null;
  }
}

function stat(label, value, testid, goTo) {
  return el('button', { type: 'button', class: 'stat', 'data-testid': testid, onclick: () => { state.tab.ADMIN = goTo; render(); } },
    el('span', { class: 'stat-value', 'data-testid': `${testid}-value` }, value), el('span', { class: 'stat-label' }, label));
}

function adminOverview() {
  const s = S();
  const firstError = [s.users, s.students, s.notices, s.teachers].find((r) => r.error)?.error;
  const latest = [...s.notices.items].sort((a, b) => (A.millis(b.createdAt) ?? 0) - (A.millis(a.createdAt) ?? 0)).slice(0, 3);
  return el('div', {},
    section('School Operations Snapshot', null,
      firstError ? errorBox(firstError) : null,
      el('div', { class: 'stats' },
        stat('User accounts', count(s.users), 'stat-users', 'users'),
        stat('Parents', count(s.users, (u) => u.role === 'PARENT'), 'stat-parents', 'users'),
        stat('Teachers', count(s.teachers), 'stat-teachers', 'teachers'),
        stat('Students', count(s.students), 'stat-students', 'students'),
        stat('Classes', count(s.classes), 'stat-classes', 'classes'),
        stat('Subjects', count(s.subjects), 'stat-subjects', 'subjects'),
        stat('Assignments', count(s.assignments), 'stat-assignments', 'assignments'),
        stat('Notices', count(s.notices), 'stat-notices', 'notices'),
        stat('Inactive / awaiting approval', count(s.users, (u) => !u.active), 'stat-inactive', 'users'))),
    section('Latest School Notices', button('View all', () => { state.tab.ADMIN = 'notices'; render(); }, { variant: 'ghost', small: true }),
      latest.length ? latest.map((n) => noticeCard(n, { showAudience: true })) : (s.notices.loading ? loading() : empty('No notices yet.'))));
}

function adminUsers(me) {
  const s = S();
  const filters = [{ id: 'ALL', label: 'All' }, { id: 'INACTIVE', label: 'Inactive / pending' }, ...A.ROLES.map((r) => ({ id: r, label: r[0] + r.slice(1).toLowerCase() }))];
  const q = state.ui.userSearch.toLowerCase();
  const list = s.users.items.filter((u) => (state.ui.userFilter === 'ALL' || (state.ui.userFilter === 'INACTIVE' ? !u.active : u.role === state.ui.userFilter)) &&
    (!q || [u.displayName, u.email, u.phoneNumber].some((v) => (v ?? '').toLowerCase().includes(q))))
    .sort((a, b) => Number(a.active) - Number(b.active) || A.userLabel(a).localeCompare(A.userLabel(b)));
  return section(`User Accounts (${s.users.items.length})`, button('Create account', () => createAccountDialog(), { testid: 'admin-create-account', small: true }),
    chips(filters, state.ui.userFilter, (id) => { state.ui.userFilter = id; render(); }, 'user-filter'),
    searchBox('userSearch', 'Search name, email or phone…', 'user-search'),
    remoteList(s.users, list, q || state.ui.userFilter !== 'ALL' ? 'No users match.' : 'No user accounts yet.', (u) => {
      const kids = u.role === 'PARENT' ? A.childrenOf(u.uid, s.students.items) : [];
      return el('button', { type: 'button', class: 'card card-button', 'data-testid': `user-row-${u.uid}`, onclick: () => editUserDialog(u, me) },
        el('div', { class: 'card-head' }, text('h3', '', A.userLabel(u)), el('div', { class: 'row' },
          A.isOwner(u) ? pill('PROTECTED OWNER', 'gold') : pill(u.role, 'green'), pill(u.active ? 'ACTIVE' : 'INACTIVE', u.active ? 'teal' : 'amber'))),
        text('div', 'muted', [u.email, u.phoneNumber].filter(Boolean).join(' • ')),
        u.role === 'PARENT' ? text('div', 'purple small', kids.length ? `Children: ${kids.map(A.fullName).join(', ')}` : 'No linked children') : null);
    }));
}

function createAccountDialog() {
  const name = field('Full name', { testid: 'account-name', validate: (v) => A.validate.required(v, 'Name') });
  const email = field('Email', { type: 'email', testid: 'account-email', validate: A.validate.email });
  const phone = field('Phone (optional)', { type: 'tel', testid: 'account-phone', validate: A.validate.phone });
  const role = select('Role', A.ROLES.map((r) => ({ id: r, label: r })), { value: 'PARENT', testid: 'account-role' });
  openForm({
    title: 'Create account', submitLabel: 'Create & email link',
    body: [text('p', 'muted small', 'The person receives an email to set their own password. Use the Teachers tab to create teachers with class assignments.'), name.node, email.node, phone.node, role.node],
    onSubmit: () => save(() => data.provisionAccount(services, { email: email.get(), name: name.get(), role: role.get(), phone: phone.get() }),
      `Account created. A link to set a password was emailed to ${email.get().trim()}`),
  });
}

function editUserDialog(u, me) {
  const isMe = u.uid === me.uid;
  const protectedOwner = A.isOwner(u);
  const s = S();
  const kids = A.childrenOf(u.uid, s.students.items);
  const classes = byId(s.classes.items);
  const name = field('Display name', { value: u.displayName, testid: 'edit-user-name', validate: (v) => A.validate.required(v, 'Name') });
  const phone = field('Phone', { value: u.phoneNumber, type: 'tel', testid: 'edit-user-phone', validate: A.validate.phone });
  const roleChoices = (A.isOwner(u) ? [{ id: A.OWNER_ROLE, label: `${A.OWNER_ROLE} (protected)` }] : []).concat(A.ROLES.map((r) => ({ id: r, label: r })));
  const role = select('Role', roleChoices, { value: u.role, testid: 'edit-user-role' });
  const active = checkbox(u.active ? 'Account active' : 'Approve / activate account', u.active, 'edit-user-active');
  openForm({
    title: `Manage ${A.userLabel(u)}`,
    body: [
      labeled('Email', u.email), labeled('Created', A.formatDateTime(u.createdAt)), name.node, phone.node,
      protectedOwner ? text('p', 'notice-box err small', 'Protected Super Admin Owner account. Its role, status and deletion cannot be changed by any administrator; attempts are refused by the server and recorded in the audit log.', { 'data-testid': 'owner-protected-note' }) : null,
      isMe ? text('p', 'muted small', 'You cannot change your own role or deactivate yourself.') : [role.node, active.node],
      u.role === 'PARENT' ? el('div', {}, text('strong', 'small', 'Linked children'),
        kids.length ? el('ul', {}, kids.map((k) => el('li', {}, `${A.fullName(k)} — ${classes.get(k.classId)?.name ?? 'No class'}`)))
          : text('p', 'muted small', 'None. Link children from the Students tab (edit a learner → Parents).')) : null,
      u.email ? button('Email a password reset link', () => quiet(save(() => data.sendReset(services, u.email), `Password reset link sent to ${u.email}`)), { variant: 'ghost', testid: 'edit-user-reset' }) : null,
    ],
    onSubmit: () => save(() => {
      if (protectedOwner && isMe) return data.updateOwnProfile(services, u.uid, name.get(), phone.get());
      if (protectedOwner) {
        return data.adminUpdateUser(services, { uid: u.uid, role: role.get(), active: active.get(), displayName: name.get(), phoneNumber: phone.get() });
      }
      const next = { ...u, displayName: name.get(), phoneNumber: phone.get(), role: isMe ? u.role : role.get(), active: isMe ? u.active : active.get() };
      return data.updateUser(services, next);
    }, `User ${name.get().trim()} updated`),
  });
}

function adminTeachers() {
  const s = S();
  const users = byId(s.users.items, 'uid');
  const classes = byId(s.classes.items);
  const subjects = byId(s.subjects.items);
  const q = state.ui.teacherSearch.toLowerCase();
  const list = s.teachers.items.filter((t) => !q || [A.fullName(t), t.email, t.employeeNumber].some((v) => (v ?? '').toLowerCase().includes(q)))
    .sort((a, b) => A.fullName(a).localeCompare(A.fullName(b)));
  return section(`Teachers (${s.teachers.items.length})`, button('Add teacher', () => teacherDialog(null), { testid: 'admin-add-teacher', small: true }),
    searchBox('teacherSearch', 'Search by name, employee # or email…', 'teacher-search'),
    remoteList(s.teachers, list, q ? 'No teachers match.' : 'No teachers yet. Use "Add teacher" to create one.', (t) => {
      const login = users.get(t.uid);
      return el('article', { class: 'card', 'data-testid': 'teacher-card' },
        el('div', { class: 'card-head' }, text('h3', '', A.fullName(t)), actions(() => teacherDialog(t), () => confirmDialog(`Remove the teacher record for ${A.fullName(t)}? Their login stays; deactivate it under Users.`, () => save(() => data.deleteTeacher(services, t), `Teacher ${A.fullName(t)} removed`)), `teacher-${t.uid}`)),
        text('div', 'muted', `Emp #: ${t.employeeNumber || '—'} • ${t.email}`),
        text('div', 'blue small', `Classes: ${(t.classIds ?? []).map((id) => classes.get(id)?.name).filter(Boolean).join(', ') || 'none assigned'}`),
        text('div', 'purple small', `Subjects: ${(t.subjectIds ?? []).map((id) => subjects.get(id)?.name).filter(Boolean).join(', ') || 'none assigned'}`),
        pill(!login ? 'NO LOGIN' : login.active ? 'LOGIN ACTIVE' : 'LOGIN INACTIVE', login?.active ? 'teal' : 'amber'));
    }));
}

function teacherDialog(existing) {
  const first = field('First name', { value: existing?.firstName, testid: 'teacher-first', validate: (v) => A.validate.required(v, 'First name') });
  const last = field('Last name', { value: existing?.lastName, testid: 'teacher-last' });
  const email = existing ? null : field('Email (login)', { type: 'email', testid: 'teacher-email', validate: A.validate.email });
  const emp = field('Employee number', { value: existing?.employeeNumber, testid: 'teacher-emp' });
  const phone = field('Phone', { value: existing?.phoneNumber, type: 'tel', testid: 'teacher-phone', validate: A.validate.phone });
  const classes = multiSelect('Classes taught', classChoices(), existing?.classIds, { testid: 'teacher-classes', emptyText: 'Create classes first.' });
  const subjects = multiSelect('Subjects taught', subjectChoices(), existing?.subjectIds, { testid: 'teacher-subjects', emptyText: 'Create subjects first.' });
  openForm({
    title: existing ? `Edit ${A.fullName(existing)}` : 'Add teacher',
    submitLabel: existing ? 'Save' : 'Create teacher',
    body: [existing ? labeled('Login email', existing.email) : text('p', 'muted small', "Creates the teacher's login and emails them a link to set a password."),
      first.node, last.node, email?.node, emp.node, phone.node, classes.node, subjects.node],
    onSubmit: () => {
      const teacher = { ...(existing ?? {}), firstName: first.get(), lastName: last.get(), email: existing ? existing.email : email.get().trim(), employeeNumber: emp.get(), phoneNumber: phone.get(), classIds: classes.get(), subjectIds: subjects.get() };
      return existing
        ? save(() => data.saveTeacher(services, teacher, existing), `Teacher ${A.fullName(teacher)} saved`)
        : save(() => data.createTeacher(services, teacher), `Teacher ${A.fullName(teacher)} created. A link to set a password was emailed to ${teacher.email}`);
    },
  });
}

function adminStudents() {
  const s = S();
  const users = byId(s.users.items, 'uid');
  const classes = byId(s.classes.items);
  const q = state.ui.studentSearch.toLowerCase();
  const list = s.students.items.filter((st) => (state.ui.studentClass === 'ALL' || st.classId === state.ui.studentClass) &&
    (!q || A.fullName(st).toLowerCase().includes(q) || (st.studentNumber ?? '').toLowerCase().includes(q)))
    .sort((a, b) => A.fullName(a).localeCompare(A.fullName(b)));
  const classFilter = [{ id: 'ALL', label: 'All classes' }, ...[...s.classes.items].sort((a, b) => a.name.localeCompare(b.name)).map((c) => ({ id: c.id, label: c.name }))];
  return section(`Students (${s.students.items.length})`, button('Enroll student', () => studentDialog(null), { testid: 'admin-add-student', small: true }),
    chips(classFilter, state.ui.studentClass, (id) => { state.ui.studentClass = id; render(); }, 'student-class-filter'),
    searchBox('studentSearch', 'Search learner name or admission #…', 'student-search'),
    remoteList(s.students, list, q || state.ui.studentClass !== 'ALL' ? 'No learners match.' : 'No learners enrolled yet.', (st) => el('article', { class: 'card', 'data-testid': 'student-card' },
      el('div', { class: 'card-head' }, el('div', { class: 'row' }, text('h3', '', A.fullName(st)), pill(classes.get(st.classId)?.name ?? 'No class', 'amber')),
        actions(() => studentDialog(st), () => confirmDialog(`Delete learner ${A.fullName(st)}?`, () => save(() => data.deleteStudent(services, st), `Learner ${A.fullName(st)} removed`)), `student-${st.uid}`)),
      text('div', 'muted', `No: ${st.studentNumber || '—'}`),
      text('div', 'purple small', `Parents: ${(st.parentIds ?? []).map((id) => A.userLabel(users.get(id)) || 'Unknown account').join(', ') || 'none linked'}`),
      text('div', 'muted small', `Learner login: ${st.userId ? A.userLabel(users.get(st.userId)) || 'linked' : 'none'}`),
      el('div', { class: 'row wrap' },
        button('+ Parent account', () => newParentDialog(st), { variant: 'ghost', small: true, testid: `add-parent-${st.uid}` }),
        st.userId ? null : button('+ Learner login', () => newLearnerLoginDialog(st), { variant: 'ghost', small: true, testid: `add-login-${st.uid}` })))));
}

function studentDialog(existing) {
  const s = S();
  const first = field('First name', { value: existing?.firstName, testid: 'student-first', validate: (v) => A.validate.required(v, 'First name') });
  const last = field('Last name', { value: existing?.lastName, testid: 'student-last' });
  const number = field('Admission / student #', { value: existing?.studentNumber, testid: 'student-number' });
  const email = field('Learner email (optional)', { value: existing?.email, type: 'email', testid: 'student-email', validate: A.validate.email });
  const cls = select('Class', classChoices(), { value: existing?.classId ?? '', testid: 'student-class', placeholder: 'Select a class' });
  const parents = multiSelect('Parents / guardians',
    s.users.items.filter((u) => u.role === 'PARENT').sort((a, b) => A.userLabel(a).localeCompare(A.userLabel(b))).map((u) => ({ id: u.uid, label: A.userLabel(u) + (u.active ? '' : ' (inactive)') })),
    existing?.parentIds, { testid: 'student-parents', emptyText: 'No parent accounts yet. Parents can register, or use "+ Parent account".' });
  const login = select("Learner's own login", [{ id: '', label: 'No learner login' }, ...s.users.items.filter((u) => u.role === 'STUDENT').map((u) => ({ id: u.uid, label: A.userLabel(u) }))],
    { value: existing?.userId ?? '', testid: 'student-login' });
  openForm({
    title: existing ? `Edit ${A.fullName(existing)}` : 'Enroll student',
    submitLabel: existing ? 'Save' : 'Enroll',
    body: [first.node, last.node, number.node, email.node, cls.node, parents.node, login.node],
    onSubmit: () => {
      const student = { ...(existing ?? {}), firstName: first.get(), lastName: last.get(), studentNumber: number.get(), email: email.get(), classId: cls.get(), parentIds: parents.get(), userId: login.get() };
      return save(() => data.saveStudent(services, student), `Learner ${A.fullName(student)} saved`);
    },
  });
}

function newParentDialog(student) {
  const name = field('Parent full name', { testid: 'new-parent-name', validate: (v) => A.validate.required(v, 'Name') });
  const email = field('Parent email', { type: 'email', testid: 'new-parent-email', validate: A.validate.email });
  const phone = field('Phone (optional)', { type: 'tel', testid: 'new-parent-phone', validate: A.validate.phone });
  openForm({
    title: `New parent for ${A.fullName(student)}`, submitLabel: 'Create & link',
    body: [text('p', 'muted small', 'Creates an active parent login linked to this learner and emails a password link.'), name.node, email.node, phone.node],
    onSubmit: () => save(() => data.createParentForStudent(services, student, { name: name.get(), email: email.get(), phone: phone.get() }),
      `Parent account created for ${A.fullName(student)}. A password link was emailed to ${email.get().trim()}`),
  });
}

function newLearnerLoginDialog(student) {
  const email = field('Login email', { value: student.email, type: 'email', testid: 'new-learner-email', validate: A.validate.email });
  openForm({
    title: `Learner login for ${A.fullName(student)}`, submitLabel: 'Create login',
    body: [text('p', 'muted small', 'The learner (or parent) receives a link at this address to set the password.'), email.node],
    onSubmit: () => save(() => data.createStudentLogin(services, student, email.get()), `Learner login created. A password link was emailed to ${email.get().trim()}`),
  });
}

function adminClasses() {
  const s = S();
  const teachers = byId(s.teachers.items, 'uid');
  const subjects = byId(s.subjects.items);
  const list = [...s.classes.items].sort((a, b) => a.name.localeCompare(b.name));
  return section(`Classes (${s.classes.items.length})`, button('Add class', () => classDialog(null), { testid: 'admin-add-class', small: true }),
    remoteList(s.classes, list, 'No classes yet. Use "Add class".', (c) => {
      const learners = s.students.items.filter((st) => st.classId === c.id).length;
      return el('article', { class: 'card', 'data-testid': 'class-card' },
        el('div', { class: 'card-head' }, text('h3', '', c.name), actions(() => classDialog(c), () => confirmDialog(`Delete class ${c.name}?`, () => save(() => data.deleteClass(services, c, learners), `Class ${c.name} deleted`)), `class-${c.id}`)),
        text('div', 'muted', `${c.grade || 'No grade'} • ${learners} learner(s)`),
        text('div', 'blue small', `Teachers: ${(c.teacherIds ?? []).map((id) => teachers.get(id)).filter(Boolean).map(A.fullName).join(', ') || 'none'}`),
        text('div', 'purple small', `Subjects: ${(c.subjectIds ?? []).map((id) => subjects.get(id)?.name).filter(Boolean).join(', ') || 'none'}`));
    }));
}

function classDialog(existing) {
  const name = field('Class name (e.g. Grade 4C)', { value: existing?.name, testid: 'class-name', validate: (v) => A.validate.required(v, 'Class name') });
  const grade = field('Grade (e.g. Grade 4)', { value: existing?.grade, testid: 'class-grade', validate: (v) => A.validate.required(v, 'Grade') });
  const teachers = multiSelect('Class teachers', teacherChoices(), existing?.teacherIds, { testid: 'class-teachers', emptyText: 'Add teachers first.' });
  const subjects = multiSelect('Subjects', subjectChoices(), existing?.subjectIds, { testid: 'class-subjects', emptyText: 'Add subjects first.' });
  openForm({
    title: existing ? `Edit ${existing.name}` : 'Create class', submitLabel: existing ? 'Save' : 'Create',
    body: [name.node, grade.node, teachers.node, subjects.node],
    onSubmit: () => {
      if (A.validate.required(grade.get(), 'Grade')) return save(() => Promise.reject(new Error('Grade is required.')), '');
      const cls = { ...(existing ?? {}), name: name.get(), grade: grade.get(), teacherIds: teachers.get(), subjectIds: subjects.get() };
      return save(() => data.saveClass(services, cls, existing), `Class ${cls.name.trim()} saved`);
    },
  });
}

function adminSubjects() {
  const s = S();
  const teachers = byId(s.teachers.items, 'uid');
  const list = [...s.subjects.items].sort((a, b) => a.name.localeCompare(b.name));
  return section(`Subjects (${s.subjects.items.length})`, button('Add subject', () => subjectDialog(null), { testid: 'admin-add-subject', small: true }),
    remoteList(s.subjects, list, 'No subjects yet. Use "Add subject".', (sub) => {
      const classIds = s.classes.items.filter((c) => (c.subjectIds ?? []).includes(sub.id)).map((c) => c.id);
      return el('article', { class: 'card', 'data-testid': 'subject-card' },
        el('div', { class: 'card-head' }, text('h3', '', sub.name), actions(() => subjectDialog(sub), () => confirmDialog(`Delete subject ${sub.name}?`, () => save(() => data.deleteSubject(services, sub, classIds), `Subject ${sub.name} deleted`)), `subject-${sub.id}`)),
        text('div', 'muted', `Code: ${sub.code || '—'}`),
        text('div', 'blue small', `Teachers: ${(sub.teacherIds ?? []).map((id) => teachers.get(id)).filter(Boolean).map(A.fullName).join(', ') || 'none'}`),
        text('div', 'purple small', `Classes: ${s.classes.items.filter((c) => classIds.includes(c.id)).map((c) => c.name).join(', ') || 'none'}`));
    }));
}

function subjectDialog(existing) {
  const s = S();
  const previousClassIds = existing ? s.classes.items.filter((c) => (c.subjectIds ?? []).includes(existing.id)).map((c) => c.id) : [];
  const name = field('Subject name', { value: existing?.name, testid: 'subject-name', validate: (v) => A.validate.required(v, 'Subject name') });
  const code = field('CAPS code', { value: existing?.code, testid: 'subject-code' });
  const teachers = multiSelect('Teachers', teacherChoices(), existing?.teacherIds, { testid: 'subject-teachers', emptyText: 'Add teachers first.' });
  const classes = multiSelect('Offered in classes', classChoices(), previousClassIds, { testid: 'subject-classes', emptyText: 'Add classes first.' });
  openForm({
    title: existing ? `Edit ${existing.name}` : 'Add subject', submitLabel: existing ? 'Save' : 'Add',
    body: [name.node, code.node, teachers.node, classes.node],
    onSubmit: () => {
      const subject = { ...(existing ?? {}), name: name.get(), code: code.get(), teacherIds: teachers.get() };
      return save(() => data.saveSubject(services, subject, existing, classes.get(), previousClassIds), `Subject ${subject.name.trim()} saved`);
    },
  });
}

function adminAssignments() {
  const s = S();
  const filter = [{ id: 'ALL', label: 'All classes' }, ...[...s.classes.items].sort((a, b) => a.name.localeCompare(b.name)).map((c) => ({ id: c.id, label: c.name }))];
  const list = s.assignments.items.filter((a) => state.ui.assignmentClass === 'ALL' || a.classId === state.ui.assignmentClass)
    .sort((a, b) => (A.millis(b.createdAt) ?? 0) - (A.millis(a.createdAt) ?? 0));
  return section(`Assignments (${s.assignments.items.length})`, button('New assignment', () => assignmentDialog(null, s.classes.items, null), { testid: 'admin-add-assignment', small: true }),
    chips(filter, state.ui.assignmentClass, (id) => { state.ui.assignmentClass = id; render(); }, 'assignment-class-filter'),
    remoteList(s.assignments, list, 'No assignments yet.', (a) => assignmentCard(a, {
      onEdit: () => assignmentDialog(a, s.classes.items, null),
      onDelete: () => confirmDialog(`Delete assignment "${a.title}"?`, () => save(() => data.deleteAssignment(services, a), `Assignment "${a.title}" deleted`)),
    })));
}

/** lockedTeacherId is set in the teacher portal (teachers can only target their own classes). */
function assignmentDialog(existing, classes, lockedTeacherId) {
  const title = field('Title', { value: existing?.title, testid: 'assignment-title-input', validate: (v) => A.validate.required(v, 'Title') });
  const description = field('Instructions', { value: existing?.description, multiline: true, testid: 'assignment-description' });
  const sortedClasses = [...classes].sort((a, b) => a.name.localeCompare(b.name));
  const cls = select('Class', sortedClasses.map((c) => ({ id: c.id, label: c.name })),
    { value: existing?.classId ?? (sortedClasses.length === 1 ? sortedClasses[0].id : ''), testid: 'assignment-class', placeholder: 'Select a class' });
  const subject = select('Subject', [{ id: '', label: 'General' }, ...subjectChoices()], { value: existing?.subjectId ?? '', testid: 'assignment-subject' });
  const teacher = lockedTeacherId ? null
    : select('Teacher', [{ id: '', label: 'School administration' }, ...teacherChoices()], { value: existing?.teacherId ?? '', testid: 'assignment-teacher' });
  const due = field('Due date', { type: 'date', value: A.toDateInput(existing?.dueDate), testid: 'assignment-due' });
  openForm({
    title: existing ? 'Edit assignment' : 'Create assignment', submitLabel: existing ? 'Save' : 'Publish',
    body: [title.node, description.node, cls.node, subject.node, teacher?.node, due.node],
    onSubmit: () => {
      const a = { ...(existing ?? {}), title: title.get(), description: description.get(), classId: cls.get(), subjectId: subject.get(),
        teacherId: lockedTeacherId ?? teacher.get(), dueDate: A.fromDateInput(due.get()) };
      return save(() => data.saveAssignment(services, a), existing ? 'Assignment updated' : 'Assignment published to class');
    },
  });
}

function adminNotices(user) {
  const s = S();
  const list = [...s.notices.items].sort((a, b) => (A.millis(b.createdAt) ?? 0) - (A.millis(a.createdAt) ?? 0));
  return section(`Notices (${s.notices.items.length})`, button('New notice', () => noticeDialog(null, user, s.classes.items, A.ADMIN_AUDIENCES, true), { testid: 'admin-add-notice', small: true }),
    remoteList(s.notices, list, 'No notices yet.', (n) => noticeCard(n, {
      showAudience: true,
      onEdit: () => noticeDialog(n, user, s.classes.items, A.ADMIN_AUDIENCES, true),
      onDelete: () => confirmDialog(`Delete notice "${n.title}"?`, () => save(() => data.deleteNotice(services, n), `Notice "${n.title}" deleted`)),
    })));
}

function audienceLabel(a) {
  return a === 'ALL' ? 'Everyone' : `${a[0]}${a.slice(1).toLowerCase()}s`;
}

function noticeDialog(existing, user, classes, audiences, allowWholeSchool) {
  const title = field('Notice title', { value: existing?.title, testid: 'notice-title-input', validate: (v) => A.validate.required(v, 'Title') });
  const message = field('Message', { value: existing?.message, multiline: true, testid: 'notice-message', validate: (v) => A.validate.required(v, 'Message') });
  const audience = select('Audience', audiences.map((a) => ({ id: a, label: audienceLabel(a) })), { value: existing?.targetRole ?? audiences[0], testid: 'notice-audience' });
  const sorted = [...classes].sort((a, b) => a.name.localeCompare(b.name));
  const cls = select('Class', [...(allowWholeSchool ? [{ id: '', label: 'Whole school' }] : []), ...sorted.map((c) => ({ id: c.id, label: c.name }))],
    { value: existing?.classId ?? (allowWholeSchool ? '' : sorted[0]?.id ?? ''), testid: 'notice-class' });
  const published = checkbox('Published (uncheck to keep as a draft)', existing?.published ?? true, 'notice-published');
  openForm({
    title: existing ? 'Edit notice' : 'New notice', submitLabel: 'Save',
    body: [title.node, message.node, audience.node, cls.node, published.node],
    onSubmit: () => {
      const isTeacher = user.role === 'TEACHER';
      const n = { ...(existing ?? {}), title: title.get(), message: message.get(), targetRole: audience.get(), classId: cls.get(), published: published.get(),
        authorId: !existing || isTeacher ? user.uid : existing.authorId };
      if (!allowWholeSchool && !n.classId) return save(() => Promise.reject(new Error('Choose a class.')), '');
      return save(() => data.saveNotice(services, n), !n.published ? 'Notice saved as draft' : existing ? 'Notice updated' : 'Notice published');
    },
  });
}

function adminSettings(user) {
  const s = S();
  const cfg = s.settings.items[0] ?? { schoolName: 'Discovery Primary School' };
  const rows = [['School name', 'schoolName'], ['EMIS number (GDE)', 'emisNumber'], ['Education district', 'district'], ['Province', 'province'],
    ['Principal', 'principalName'], ['Administrative email', 'contactEmail'], ['Contact telephone', 'contactPhone'], ['Academic term', 'academicTerm']];
  return section('School Configuration & Settings', button('Edit settings', () => {
    const fields = rows.map(([label, key]) => [key, field(label, {
      value: cfg[key] ?? '', testid: `settings-${key}`,
      validate: key === 'contactEmail' ? A.validate.email : key === 'contactPhone' ? A.validate.phone : key === 'schoolName' ? (v) => A.validate.required(v, 'School name') : undefined,
    })]);
    openForm({
      title: 'Edit school settings', body: fields.map(([, f]) => f.node),
      onSubmit: () => save(() => data.saveSettings(services, Object.fromEntries(fields.map(([k, f]) => [k, f.get().trim()]))), 'School settings updated'),
    });
  }, { testid: 'admin-edit-settings', small: true }),
  s.settings.error ? errorBox(s.settings.error) : s.settings.loading ? loading() : card(rows.map(([label, key]) => labeled(label, cfg[key]))),
  A.isOwner(user) ? ownerSecurity() : null);
}

/** Owner only: session control and the security audit log (read-only; written by the server). */
function ownerSecurity() {
  const logs = S().auditLogs;
  const describe = (e) => [e.action, e.outcome, e.actorEmail || e.actorUid].filter(Boolean).join(' • ');
  return section('Owner Security', button('Sign out all devices', () => openForm({
    title: 'Sign out all devices', submitLabel: 'Sign out everywhere', testid: 'owner-revoke-dialog',
    body: text('p', '', 'This ends the Owner session on every phone, tablet and computer, including this one. You can sign in again with your password.'),
    onSubmit: () => save(async () => { await data.revokeAllSessions(services); await signOut(); }, 'Signed out on every device.'),
  }), { variant: 'danger', small: true, testid: 'owner-revoke-sessions' }),
  text('p', 'muted small', 'Your password is managed only by Firebase Authentication. To change it, use "Change password" on your profile or "Forgot password" on the sign-in page.'),
  remoteList(logs, logs.items, 'No security events recorded yet.', (e) => card(
    text('h3', 'audit-type', e.type ?? 'EVENT'),
    text('div', 'muted small', A.formatDateTime(e.at)),
    text('div', 'muted small', describe(e)),
  )));
}

// ---------- TEACHER ----------

function teacherView(user, tab) {
  const s = S();
  if (tab === 'profile') {
    const profile = s.teacherProfile.items[0];
    return profileView(user, 'green', [
      ['Employee number', profile?.employeeNumber ?? ''],
      ['Classes', A.teacherClasses(profile, s.classes.items).map((c) => c.name).join(', ')],
      ['Subjects', s.subjects.items.filter((x) => (profile?.subjectIds ?? []).includes(x.id)).map((x) => x.name).join(', ')],
    ]);
  }
  const p = s.teacherProfile;
  if (p.error) return errorBox(p.error);
  if (p.loading) return loading();
  if (!p.items.length) return empty('Your login is not linked to a teacher record yet. Ask the school administrator to add you under Teachers.');
  const teacher = p.items[0];
  const myClasses = A.teacherClasses(teacher, s.classes.items);
  const classIds = myClasses.map((c) => c.id);
  const subjects = byId(s.subjects.items);

  if (tab === 'classes') {
    return section(`My Assigned Classes (${myClasses.length})`, null,
      s.students.error ? errorBox(s.students.error) : null,
      myClasses.length ? myClasses.map((c) => {
        const roster = s.students.items.filter((st) => st.classId === c.id).sort((a, b) => A.fullName(a).localeCompare(A.fullName(b)));
        return el('article', { class: 'card', 'data-testid': 'teacher-class' },
          el('div', { class: 'card-head' }, el('div', {}, text('h3', '', c.name), text('div', 'muted', c.grade)), pill(`${roster.length} learners`, 'amber')),
          text('div', 'purple small', `Subjects: ${(c.subjectIds ?? []).map((id) => subjects.get(id)?.name).filter(Boolean).join(', ') || 'none'}`),
          roster.length ? el('ul', { class: 'roster' }, roster.map((st) => el('li', {}, el('span', {}, A.fullName(st)), text('span', 'muted', st.studentNumber)))) : text('p', 'muted small', 'No learners enrolled.'));
      }) : empty('No classes are assigned to you yet. The administrator assigns classes under Teachers or Classes.'));
  }
  if (tab === 'students') {
    const classes = byId(myClasses);
    const q = state.ui.myStudentSearch.toLowerCase();
    const list = s.students.items.filter((st) => !q || A.fullName(st).toLowerCase().includes(q) || (st.studentNumber ?? '').toLowerCase().includes(q))
      .sort((a, b) => A.fullName(a).localeCompare(A.fullName(b)));
    return section(`Learners in My Classes (${s.students.items.length})`, null,
      searchBox('myStudentSearch', 'Search by learner name or admission #…', 'my-student-search'),
      remoteList(s.students, list, q ? 'No learners match.' : 'No learners in your classes yet.', (st) => el('article', { class: 'card', 'data-testid': 'teacher-student' },
        el('div', { class: 'card-head' }, text('h3', '', A.fullName(st)), pill(classes.get(st.classId)?.name ?? '', 'green')),
        text('div', 'muted', `No: ${st.studentNumber || '—'}`), st.phoneNumber ? text('div', 'muted small', st.phoneNumber) : null)));
  }
  if (tab === 'assignments') {
    const list = A.assignmentsForClasses(s.assignments.items, classIds);
    return section(`Class Assignments (${list.length})`,
      button('New task', () => assignmentDialog(null, myClasses, user.uid), { testid: 'teacher-add-assignment', small: true, disabled: !myClasses.length }),
      myClasses.length ? null : text('p', 'muted small', 'You need an assigned class before you can publish assignments.'),
      remoteList(s.assignments, list, 'No assignments for your classes yet.', (a) => {
        const mine = a.teacherId === user.uid;
        return assignmentCard(a, mine ? {
          onEdit: () => assignmentDialog(a, myClasses, user.uid),
          onDelete: () => confirmDialog(`Delete assignment "${a.title}"?`, () => save(() => data.deleteAssignment(services, a), `Assignment "${a.title}" deleted`)),
        } : {});
      }));
  }
  if (tab === 'notices') {
    const list = A.visibleNotices(s.notices.items, 'TEACHER', user.uid, classIds);
    return section(`Notices (${list.length})`,
      button('Class notice', () => noticeDialog(null, user, myClasses, A.TEACHER_AUDIENCES, false), { testid: 'teacher-add-notice', small: true, disabled: !myClasses.length }),
      remoteList(s.notices, list, 'No notices for teachers yet.', (n) => {
        const mine = n.authorId === user.uid;
        return noticeCard(n, mine ? {
          showAudience: true,
          onEdit: () => noticeDialog(n, user, myClasses, A.TEACHER_AUDIENCES, false),
          onDelete: () => confirmDialog(`Delete notice "${n.title}"?`, () => save(() => data.deleteNotice(services, n), `Notice "${n.title}" deleted`)),
        } : { showAudience: true });
      }));
  }
  return null;
}

// ---------- PARENT ----------

function parentView(user, tab) {
  const s = S();
  const children = A.childrenOf(user.uid, s.students.items);
  if (tab === 'profile') return profileView(user, 'purple', [['Linked children', children.map(A.fullName).join(', ') || 'None yet']]);
  if (tab === 'notices') {
    const list = A.visibleNotices(s.notices.items, 'PARENT', user.uid, children.map((c) => c.classId));
    return section('School & Class Notices', null, remoteList(s.notices, list, 'No notices for parents right now.', (n) => noticeCard(n)));
  }
  if (s.students.error) return errorBox(s.students.error);
  if (s.students.loading) return loading();
  if (!children.length) return empty('No learners are linked to your account yet. The school administrator links parents to their children.');
  const child = children.find((c) => c.uid === state.ui.childId) ?? children[0];
  const picker = children.length > 1
    ? chips(children.map((c) => ({ id: c.uid, label: A.fullName(c) })), child.uid, (id) => { state.ui.childId = id; render(); }, 'child-picker') : null;
  const cls = s.classes.items.find((c) => c.id === child.classId);
  const homework = A.assignmentsForClasses(s.assignments.items, [child.classId]);

  if (tab === 'children') {
    const educators = A.teachersOfClass(child.classId, s.teachers.items);
    const subjects = s.subjects.items.filter((x) => (cls?.subjectIds ?? []).includes(x.id));
    const open = homework.filter((a) => A.dueStatus(a.dueDate) !== A.DUE.OVERDUE).length;
    const settings = s.settings.items[0];
    return el('div', {}, picker,
      section('Learner Profile', null,
        card(text('h3', 'big', A.fullName(child), { 'data-testid': 'child-name' }), labeled('Admission number', child.studentNumber), labeled('Class', cls?.name ?? 'Not assigned'), labeled('Grade', cls?.grade)),
        card(text('h3', '', 'Class educators'), educators.length ? educators.map((t) => el('div', { class: 'educator' },
          text('strong', '', A.fullName(t), { 'data-testid': 'child-teacher' }), text('div', 'muted small', [t.email, t.phoneNumber].filter(Boolean).join(' • ')))) : text('p', 'muted', 'No teacher assigned yet.')),
        card(text('h3', '', 'Subjects'), text('p', 'muted', subjects.map((x) => x.name).join(', ') || 'No subjects listed for this class yet.')),
        card(text('h3', '', 'Homework'), text('p', 'muted', `${homework.length} assignment(s) for ${cls?.name ?? 'this class'}; ${open} not past due.`)),
        settings ? card(text('h3', '', settings.schoolName), settings.academicTerm ? labeled('Term', settings.academicTerm) : null,
          settings.contactPhone ? labeled('School phone', settings.contactPhone) : null, settings.contactEmail ? labeled('School email', settings.contactEmail) : null) : null));
  }
  if (tab === 'homework') {
    return el('div', {}, picker, section(`${child.firstName || A.fullName(child)}'s Homework & Tasks`, null,
      remoteList(s.assignments, homework, `No assignments for ${cls?.name ?? 'this class'} yet.`, (a) => assignmentCard(a))));
  }
  return null;
}

// ---------- STUDENT ----------

function studentView(user, tab) {
  const s = S();
  const record = s.students.items.find((st) => st.userId === user.uid);
  const cls = record ? s.classes.items.find((c) => c.id === record.classId) : null;
  if (tab === 'profile') return profileView(user, 'blue', [['Admission number', record?.studentNumber ?? ''], ['Class', cls?.name ?? 'Not assigned'], ['Grade', cls?.grade ?? '']]);
  if (s.students.error) return errorBox(s.students.error);
  if (s.students.loading) return loading();
  if (!record) return empty('Your login is not linked to a learner record yet. Ask the school office to link your account.');
  if (tab === 'subjects') {
    const teachers = byId(s.teachers.items, 'uid');
    const list = s.subjects.items.filter((x) => (cls?.subjectIds ?? []).includes(x.id)).sort((a, b) => a.name.localeCompare(b.name));
    return section(`My Subjects${cls ? ` — ${cls.name}` : ''}`, null, remoteList(s.subjects, list, 'No subjects are listed for your class yet.', (x) => {
      const classTeachers = (x.teacherIds ?? []).map((id) => teachers.get(id)).filter((t) => t && (t.classIds ?? []).includes(record.classId));
      return card(text('h3', '', x.name, { 'data-testid': 'student-subject' }), x.code ? text('div', 'muted small', `CAPS Code: ${x.code}`) : null,
        classTeachers.length ? text('div', 'blue small', `Teacher: ${classTeachers.map(A.fullName).join(', ')}`) : null);
    }));
  }
  if (tab === 'assignments') {
    return section('My Homework & Class Tasks', null,
      remoteList(s.assignments, A.assignmentsForClasses(s.assignments.items, [record.classId]), 'No assignments yet. Enjoy the break!', (a) => assignmentCard(a)));
  }
  if (tab === 'notices') {
    return section('School Announcements', null,
      remoteList(s.notices, A.visibleNotices(s.notices.items, 'STUDENT', user.uid, [record.classId]), 'No announcements right now.', (n) => noticeCard(n)));
  }
  return null;
}

// ---------- STAFF ----------

function staffView(user, tab) {
  const s = S();
  if (tab === 'profile') return profileView(user, 'teal');
  if (tab === 'notices') {
    return section('School Announcements', null,
      remoteList(s.notices, A.visibleNotices(s.notices.items, 'STAFF', user.uid, []), 'No announcements for staff right now.', (n) => noticeCard(n)));
  }
  if (tab === 'directory') {
    const list = [...s.teachers.items].sort((a, b) => A.fullName(a).localeCompare(A.fullName(b)));
    return section(`Academic Staff Directory (${list.length})`, null, remoteList(s.teachers, list, 'No teachers listed yet.', (t) =>
      card(text('h3', '', A.fullName(t), { 'data-testid': 'directory-teacher' }),
        text('div', 'muted small', [t.employeeNumber ? `Emp #: ${t.employeeNumber}` : null, t.email, t.phoneNumber].filter(Boolean).join(' • ')))));
  }
  return null;
}

// ---------- boot ----------

window.addEventListener('discover-ps:forget-config', () => { forgetSavedConfig(); location.reload(); });
if (services && configSource() === 'browser') console.info('Discover PS: using the Firebase config saved in this browser.');
render();
