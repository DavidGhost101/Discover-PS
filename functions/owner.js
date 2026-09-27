// Shared server-side helpers for the protected Super Admin Owner account.
// Used by the Cloud Functions (index.js) and the provisioning script. Nothing here ever
// receives, stores or logs a password: passwords live only in Firebase Authentication.
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';

export const OWNER_ROLE = 'SUPER_ADMIN_OWNER';
export const SCHOOL_ID = 'discovery-primary';
export const ASSIGNABLE_ROLES = ['ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'STAFF'];
export const PROTECTED_OWNER_MESSAGE = 'Protected Super Admin Owner account.';

/** True when the Auth custom claims mark the Owner (set only by the Admin SDK). */
export function hasOwnerClaim(claims) {
  return !!claims && claims.role === OWNER_ROLE && claims.owner === true;
}

/** True when either the Firestore profile or the Auth claims identify the Owner. */
export async function isOwnerAccount(uid) {
  if (!uid) return false;
  const snap = await getFirestore().doc(`users/${uid}`).get();
  if (snap.exists && snap.get('role') === OWNER_ROLE) return true;
  try {
    const record = await getAuth().getUser(uid);
    return hasOwnerClaim(record.customClaims);
  } catch {
    return false;
  }
}

const AUDIT_KEYS = ['type', 'outcome', 'actorUid', 'actorEmail', 'targetUid', 'action', 'details'];

/**
 * Appends an entry to /auditLogs (clients cannot write this collection). Only the keys
 * above are kept, so a caller can never smuggle a password or token into the log.
 */
export async function audit(entry) {
  const clean = {};
  for (const key of AUDIT_KEYS) {
    if (entry[key] !== undefined && entry[key] !== null) clean[key] = entry[key];
  }
  if (clean.details && typeof clean.details === 'object') {
    const details = {};
    for (const [k, v] of Object.entries(clean.details)) {
      if (/pass|secret|token|credential/i.test(k)) continue;
      if (['string', 'number', 'boolean'].includes(typeof v)) details[k] = v;
    }
    clean.details = details;
  }
  await getFirestore().collection('auditLogs').add({ ...clean, at: FieldValue.serverTimestamp() });
}

/** The app's password policy (Firebase itself only requires 6 characters). */
export function passwordProblem(password) {
  if (password.length < 8) return 'must be at least 8 characters';
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) return 'must contain letters and numbers';
  return null;
}
