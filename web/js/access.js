// Pure presentation logic shared by every portal (mirrors the Android app's
// domain/SchoolAccess.kt). Firestore rules are the security boundary; these decide relevance.

export const SCHOOL_ID = 'discovery-primary';
export const ROLES = ['ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'STAFF'];
export const AUDIENCE_ALL = 'ALL';
export const ADMIN_AUDIENCES = ['ALL', 'PARENT', 'TEACHER', 'STUDENT', 'STAFF'];
export const TEACHER_AUDIENCES = ['PARENT', 'STUDENT'];
export const MIN_PASSWORD_LENGTH = 8;

/** Protected Super Admin Owner: granted only server-side (Admin SDK custom claim + profile). */
export const OWNER_ROLE = 'SUPER_ADMIN_OWNER';

/** Roles an administrator may assign (never the Owner role). */
export function parseRole(role) {
  const r = typeof role === 'string' ? role.trim().toUpperCase() : '';
  return ROLES.includes(r) ? r : null;
}

export function isOwner(user) {
  return !!user && user.role === OWNER_ROLE;
}

/** Which portal a profile opens: the Owner uses the administrator console. */
export function portalRole(role) {
  return role === OWNER_ROLE ? 'ADMIN' : parseRole(role);
}

/** Milliseconds for a Firestore Timestamp, Date or millis number; null when absent. */
export function millis(value) {
  if (value == null) return null;
  if (typeof value === 'number') return value;
  if (value instanceof Date) return value.getTime();
  if (typeof value.toMillis === 'function') return value.toMillis();
  if (typeof value.seconds === 'number') return value.seconds * 1000 + Math.floor((value.nanoseconds || 0) / 1e6);
  return null;
}

const byNewest = (a, b) => (millis(b.createdAt) ?? 0) - (millis(a.createdAt) ?? 0);

export function fullName(p) {
  const n = `${p?.firstName ?? ''} ${p?.lastName ?? ''}`.trim();
  return n || p?.studentNumber || p?.email || 'Unnamed';
}

export function userLabel(u) {
  return u?.displayName || u?.email || u?.phoneNumber || u?.uid || '';
}

/** Notices a member should see, newest first. */
export function visibleNotices(notices, role, uid, classIds) {
  const ids = new Set(classIds ?? []);
  if (role === 'ADMIN') return [...notices].sort(byNewest);
  return notices.filter((n) => {
    const authoredByMe = n.authorId === uid;
    const audience = n.published === true && (n.targetRole === AUDIENCE_ALL || n.targetRole === role);
    const classMatches = !n.classId || ids.has(n.classId);
    return authoredByMe || (audience && classMatches);
  }).sort(byNewest);
}

/** Assignments for the given classes, soonest due first, undated last. */
export function assignmentsForClasses(assignments, classIds) {
  const ids = new Set(classIds ?? []);
  return assignments.filter((a) => ids.has(a.classId)).sort((a, b) => {
    const x = millis(a.dueDate); const y = millis(b.dueDate);
    if (x == null && y == null) return 0;
    if (x == null) return 1;
    if (y == null) return -1;
    return x - y;
  });
}

export function teacherClasses(teacher, classes) {
  if (!teacher) return [];
  const ids = new Set(teacher.classIds ?? []);
  return classes.filter((c) => ids.has(c.id)).sort((a, b) => a.name.localeCompare(b.name));
}

export function childrenOf(parentUid, students) {
  return students.filter((s) => (s.parentIds ?? []).includes(parentUid))
    .sort((a, b) => fullName(a).localeCompare(fullName(b)));
}

export function teachersOfClass(classId, teachers) {
  return teachers.filter((t) => (t.classIds ?? []).includes(classId))
    .sort((a, b) => fullName(a).localeCompare(fullName(b)));
}

export const DUE = {
  NO_DUE_DATE: 'No due date',
  UPCOMING: 'Upcoming',
  DUE_TODAY: 'Due today',
  OVERDUE: 'Past due',
};

export function dueStatus(dueDate, now = new Date()) {
  const ms = millis(dueDate);
  if (ms == null) return DUE.NO_DUE_DATE;
  const due = new Date(ms);
  const sameDay = due.getFullYear() === now.getFullYear() && due.getMonth() === now.getMonth() && due.getDate() === now.getDate();
  if (sameDay) return DUE.DUE_TODAY;
  return ms < now.getTime() ? DUE.OVERDUE : DUE.UPCOMING;
}

/** Items added and removed between two relationship lists. */
export function diff(before, after) {
  const b = new Set((before ?? []).filter((x) => x && x.trim()));
  const a = new Set((after ?? []).filter((x) => x && x.trim()));
  return { added: [...a].filter((x) => !b.has(x)), removed: [...b].filter((x) => !a.has(x)) };
}

// ---------- validation (returns an error message or null) ----------

const EMAIL = /^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

export const validate = {
  email(v) {
    if (!v || !v.trim()) return 'Email address is required.';
    return EMAIL.test(v.trim()) ? null : 'Enter a valid email address.';
  },
  password(v) {
    if (!v) return 'Password is required.';
    if (v.length < MIN_PASSWORD_LENGTH) return `Password must contain at least ${MIN_PASSWORD_LENGTH} characters.`;
    if (!/[0-9]/.test(v) || !/[A-Za-z]/.test(v)) return 'Password must contain letters and numbers.';
    return null;
  },
  required(v, field) {
    return v && String(v).trim() ? null : `${field} is required.`;
  },
  phone(v) {
    if (!v || !v.trim()) return null;
    const cleaned = v.replace(/[\s\-()]/g, '');
    return /^\+?[0-9]{9,15}$/.test(cleaned) ? null : 'Enter a valid phone number.';
  },
};

/** South African mobile numbers to E.164 (e.g. 082 123 4567 -> +27821234567). */
export function normalizeSouthAfricanPhone(phone) {
  const cleaned = (phone ?? '').replace(/[\s\-()]/g, '');
  if (cleaned.startsWith('+27')) return cleaned;
  if (cleaned.startsWith('27')) return `+${cleaned}`;
  if (cleaned.startsWith('0')) return `+27${cleaned.slice(1)}`;
  return cleaned;
}

export function formatDate(ts) {
  const ms = millis(ts);
  if (ms == null) return 'No due date';
  return new Date(ms).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(ts) {
  const ms = millis(ts);
  if (ms == null) return '';
  return new Date(ms).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** yyyy-mm-dd (local) for <input type="date">. */
export function toDateInput(ts) {
  const ms = millis(ts);
  if (ms == null) return '';
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** <input type="date"> value to a Date at 23:59 local time (end of the due day). */
export function fromDateInput(value) {
  if (!value) return null;
  const [y, m, d] = value.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d, 23, 59, 0);
}
