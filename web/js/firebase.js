// Firebase bootstrap for the web portal. Configuration comes from, in order:
//   1. ?emulator on localhost (automated tests only) -> local Firebase Emulator Suite
//   2. window.DISCOVER_PS_FIREBASE_CONFIG from firebase-config.js (recommended)
//   3. a config saved in this browser from the setup screen
// The Firebase *web* config (apiKey, projectId, ...) is a public identifier, not a secret;
// data is protected by Authentication + firestore.rules.
import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth, connectAuthEmulator } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator } from 'firebase/firestore';

const STORAGE_KEY = 'discover-ps.firebase-config';
const PROVISIONING_APP = 'account-provisioning';
const REQUIRED = ['apiKey', 'authDomain', 'projectId', 'appId'];

let emulatorHost = null;
let services = null;

function isLocalhost() {
  return ['localhost', '127.0.0.1'].includes(location.hostname);
}

function readStoredConfig() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function validConfig(cfg) {
  return !!cfg && typeof cfg === 'object' && REQUIRED.every((k) => typeof cfg[k] === 'string' && cfg[k].trim());
}

/** Parses the text copied from the Firebase console (JSON or the JS object snippet). */
export function parseConfigText(text) {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) throw new Error('Paste the firebaseConfig object from the Firebase console.');
  const body = text.slice(start, end + 1)
    .replace(/\/\/.*$/gm, '')
    .replace(/([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:/g, '$1"$2":')
    .replace(/'/g, '"')
    .replace(/,\s*}/g, '}');
  const cfg = JSON.parse(body);
  if (!validConfig(cfg)) throw new Error(`The config must contain ${REQUIRED.join(', ')}.`);
  return cfg;
}

export function saveConfig(cfg) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(cfg));
}

export function forgetSavedConfig() {
  localStorage.removeItem(STORAGE_KEY);
}

export function configSource() {
  const params = new URLSearchParams(location.search);
  if (params.has('emulator') && isLocalhost()) return 'emulator';
  if (validConfig(window.DISCOVER_PS_FIREBASE_CONFIG)) return 'file';
  if (validConfig(readStoredConfig())) return 'browser';
  return null;
}

/** Initialises Firebase once; returns null when the site has no configuration yet. */
export function initFirebase() {
  if (services) return services;
  const source = configSource();
  if (!source) return null;
  let config;
  if (source === 'emulator') {
    const params = new URLSearchParams(location.search);
    emulatorHost = params.get('emulator') || '127.0.0.1';
    config = { apiKey: 'emulator-only-api-key', authDomain: 'localhost', projectId: params.get('project') || 'demo-discovery-primary', appId: 'emulator-web' };
  } else {
    config = source === 'file' ? window.DISCOVER_PS_FIREBASE_CONFIG : readStoredConfig();
  }
  const app = getApps().length ? getApp() : initializeApp(config);
  const auth = getAuth(app);
  const db = getFirestore(app);
  if (emulatorHost) {
    connectAuthEmulator(auth, `http://${emulatorHost}:9099`, { disableWarnings: true });
    connectFirestoreEmulator(db, emulatorHost, 8080);
  }
  services = { app, auth, db, source };
  return services;
}

/** Second Auth instance so an admin can create logins without being signed out. */
export function provisioningAuth() {
  const existing = getApps().find((a) => a.name === PROVISIONING_APP);
  if (existing) return getAuth(existing);
  const app = initializeApp(services.app.options, PROVISIONING_APP);
  const auth = getAuth(app);
  if (emulatorHost) connectAuthEmulator(auth, `http://${emulatorHost}:9099`, { disableWarnings: true });
  return auth;
}
