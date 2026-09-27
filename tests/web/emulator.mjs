// Firebase Emulator Suite helpers (REST) for the web E2E tests.
export const PROJECT = 'demo-discovery-primary';
const AUTH = 'http://127.0.0.1:9099';
const FIRESTORE = 'http://127.0.0.1:8080';
const KEY = 'emulator-only-api-key';

async function call(method, url, body, owner = false) {
  const res = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json', ...(owner ? { Authorization: 'Bearer owner' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${url} -> ${res.status}: ${text}`);
  return text ? JSON.parse(text) : {};
}

export async function reset() {
  await call('DELETE', `${AUTH}/emulator/v1/projects/${PROJECT}/accounts`);
  await call('DELETE', `${FIRESTORE}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`);
}

export async function createAdmin(email, password) {
  const { localId } = await call('POST', `${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${KEY}`, { email, password, returnSecureToken: true });
  await call('POST', `${AUTH}/identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts:update`, { localId, emailVerified: true }, true);
  const s = (v) => ({ stringValue: v });
  await call('POST', `${FIRESTORE}/v1/projects/${PROJECT}/databases/(default)/documents:commit`, {
    writes: [{ update: {
      name: `projects/${PROJECT}/databases/(default)/documents/users/${localId}`,
      fields: { uid: s(localId), email: s(email), phoneNumber: s(''), displayName: s('Principal Admin'), role: s('ADMIN'), schoolId: s('discovery-primary'), active: { booleanValue: true }, createdAt: { nullValue: null }, updatedAt: { nullValue: null } },
    } }],
  }, true);
  return localId;
}

async function latestCode(email, type) {
  const { oobCodes } = await call('GET', `${AUTH}/emulator/v1/projects/${PROJECT}/oobCodes`);
  const match = oobCodes.filter((c) => c.email.toLowerCase() === email.toLowerCase() && c.requestType === type);
  if (!match.length) throw new Error(`No ${type} email sent to ${email}`);
  return match.at(-1).oobCode;
}

export const hasEmail = (email, type) => latestCode(email, type).then(() => true, () => false);

export async function completePasswordReset(email, newPassword) {
  await call('POST', `${AUTH}/identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=${KEY}`, { oobCode: await latestCode(email, 'PASSWORD_RESET'), newPassword });
}

export async function completeEmailVerification(email) {
  await call('POST', `${AUTH}/identitytoolkit.googleapis.com/v1/accounts:update?key=${KEY}`, { oobCode: await latestCode(email, 'VERIFY_EMAIL') });
}
