// Cloud Functions (callable, HTTPS) for Discovery Primary.
//
// Server-side authorization for account administration and the protected Super Admin
// Owner account. Any attempt to change, suspend, demote or delete the Owner is rejected
// with HTTP 403 (callable code "permission-denied") and written to /auditLogs.
// firestore.rules enforce the same protection for direct database writes.
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import {
  OWNER_ROLE, ASSIGNABLE_ROLES, PROTECTED_OWNER_MESSAGE, hasOwnerClaim, isOwnerAccount, audit,
} from './owner.js';

initializeApp();
const db = () => getFirestore();

// ---------- caller checks ----------

async function requireAdmin(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Please sign in again.');
  const profile = (await db().doc(`users/${uid}`).get()).data();
  const owner = profile?.role === OWNER_ROLE && hasOwnerClaim(request.auth.token);
  const admin = profile?.active === true && (profile.role === 'ADMIN' || owner);
  if (!admin) throw new HttpsError('permission-denied', 'Administrator access is required.');
  return { uid, email: request.auth.token.email ?? '', owner };
}

function requireUid(value) {
  if (typeof value !== 'string' || !value || value.length > 128 || value.includes('/')) {
    throw new HttpsError('invalid-argument', 'A valid user id is required.');
  }
  return value;
}

/** Rejects (403) and audits any administrative action aimed at the Owner. */
async function guardOwner(caller, targetUid, action, details = {}) {
  if (!(await isOwnerAccount(targetUid))) return;
  await audit({
    type: 'PROTECTED_OWNER_ACTION_BLOCKED', outcome: 'DENIED', action,
    actorUid: caller.uid, actorEmail: caller.email, targetUid, details,
  });
  throw new HttpsError('permission-denied', PROTECTED_OWNER_MESSAGE);
}

async function auditAdminAction(caller, targetUid, action, details = {}) {
  await audit({ type: 'ADMIN_ACTION', outcome: 'OK', action, actorUid: caller.uid, actorEmail: caller.email, targetUid, details });
}

// ---------- account administration ----------

/** Changes another user's role, active flag, name or phone number. */
export const adminUpdateUser = onCall(async (request) => {
  const caller = await requireAdmin(request);
  const data = request.data ?? {};
  const uid = requireUid(data.uid);
  await guardOwner(caller, uid, 'UPDATE_USER', {
    requestedRole: typeof data.role === 'string' ? data.role : '',
    requestedActive: typeof data.active === 'boolean' ? data.active : '',
  });

  const update = { updatedAt: FieldValue.serverTimestamp() };
  if (data.role !== undefined) {
    if (!ASSIGNABLE_ROLES.includes(data.role)) throw new HttpsError('invalid-argument', 'Choose a valid role.');
    update.role = data.role;
  }
  if (data.active !== undefined) {
    if (typeof data.active !== 'boolean') throw new HttpsError('invalid-argument', 'active must be true or false.');
    update.active = data.active;
  }
  for (const key of ['displayName', 'phoneNumber']) {
    if (data[key] === undefined) continue;
    if (typeof data[key] !== 'string' || data[key].length > 100) throw new HttpsError('invalid-argument', `Invalid ${key}.`);
    update[key] = data[key].trim();
  }
  if (uid === caller.uid && (('role' in update && update.role !== 'ADMIN') || update.active === false)) {
    throw new HttpsError('failed-precondition', 'You cannot remove your own administrator access.');
  }
  const ref = db().doc(`users/${uid}`);
  if (!(await ref.get()).exists) throw new HttpsError('not-found', 'That user no longer exists.');
  await ref.update(update);
  await auditAdminAction(caller, uid, 'UPDATE_USER', { role: update.role ?? '', active: update.active ?? '' });
  return { ok: true };
});

/** Suspends (disables) or re-enables another user's login in Firebase Authentication. */
export const adminSetAccountDisabled = onCall(async (request) => {
  const caller = await requireAdmin(request);
  const uid = requireUid(request.data?.uid);
  const disabled = request.data?.disabled === true;
  await guardOwner(caller, uid, disabled ? 'SUSPEND_ACCOUNT' : 'UNSUSPEND_ACCOUNT');
  if (uid === caller.uid) throw new HttpsError('failed-precondition', 'You cannot suspend your own account.');
  await getAuth().updateUser(uid, { disabled });
  if (disabled) await getAuth().revokeRefreshTokens(uid);
  await auditAdminAction(caller, uid, disabled ? 'SUSPEND_ACCOUNT' : 'UNSUSPEND_ACCOUNT');
  return { ok: true };
});

/** Deletes another user's login and profile. */
export const adminDeleteUser = onCall(async (request) => {
  const caller = await requireAdmin(request);
  const uid = requireUid(request.data?.uid);
  await guardOwner(caller, uid, 'DELETE_USER');
  if (uid === caller.uid) throw new HttpsError('failed-precondition', 'You cannot delete your own account.');
  await getAuth().deleteUser(uid).catch((e) => { if (e.code !== 'auth/user-not-found') throw e; });
  await db().doc(`users/${uid}`).delete();
  await auditAdminAction(caller, uid, 'DELETE_USER');
  return { ok: true };
});

// ---------- sessions & security events ----------

/** Signs the caller out on every device (revokes all refresh tokens). */
export const revokeMySessions = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Please sign in again.');
  await getAuth().revokeRefreshTokens(uid);
  if (await isOwnerAccount(uid)) {
    await audit({ type: 'SESSIONS_REVOKED', outcome: 'OK', actorUid: uid, actorEmail: request.auth.token.email ?? '', targetUid: uid });
  }
  return { ok: true };
});

const SIGNED_IN_EVENTS = ['LOGIN', 'LOGOUT'];
const ANONYMOUS_EVENTS = ['AUTH_FAILURE', 'PASSWORD_RESET_REQUESTED'];
const recent = new Map();

function throttled(key, ms = 2000) {
  const now = Date.now();
  if ((recent.get(key) ?? 0) > now - ms) return true;
  recent.set(key, now);
  if (recent.size > 1000) recent.clear();
  return false;
}

/**
 * Records Owner security events. Always answers {ok:true} so it reveals nothing about
 * which accounts exist; only events concerning the Owner account are written.
 */
export const recordSecurityEvent = onCall(async (request) => {
  const type = request.data?.type;
  if (SIGNED_IN_EVENTS.includes(type)) {
    const uid = request.auth?.uid;
    if (!uid || !(await isOwnerAccount(uid))) return { ok: true };
    const email = request.auth.token.email ?? '';
    await audit({ type: type === 'LOGIN' ? 'OWNER_LOGIN' : 'OWNER_LOGOUT', outcome: 'OK', actorUid: uid, actorEmail: email, targetUid: uid });
    if (type === 'LOGIN') {
      const profile = (await db().doc(`users/${uid}`).get()).data();
      const verified = profile?.role === OWNER_ROLE && profile?.active === true && hasOwnerClaim(request.auth.token);
      await audit({ type: 'OWNER_ROLE_VERIFICATION', outcome: verified ? 'VERIFIED' : 'MISMATCH', actorUid: uid, actorEmail: email, targetUid: uid });
    }
    return { ok: true };
  }
  if (ANONYMOUS_EVENTS.includes(type)) {
    const email = typeof request.data?.email === 'string' ? request.data.email.trim().toLowerCase() : '';
    if (!email || email.length > 320) return { ok: true };
    let record;
    try {
      record = await getAuth().getUserByEmail(email);
    } catch {
      return { ok: true };
    }
    if (!(await isOwnerAccount(record.uid)) || throttled(`${type}:${record.uid}`)) return { ok: true };
    await audit({
      type: type === 'AUTH_FAILURE' ? 'OWNER_AUTH_FAILURE' : 'OWNER_PASSWORD_RESET_REQUESTED',
      outcome: type === 'AUTH_FAILURE' ? 'FAILED' : 'OK',
      actorUid: request.auth?.uid ?? '', targetUid: record.uid,
    });
    return { ok: true };
  }
  throw new HttpsError('invalid-argument', 'Unknown event type.');
});
