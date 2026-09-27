# Discovery Primary LMS (Android + Web)

- **Android app** (`app/`): Kotlin / Jetpack Compose.
- **Web portal** (`web/`): plain HTML/CSS/JavaScript, no build step, works on phones and desktops.

Both use the same Firebase project (Authentication + Cloud Firestore), the same data and the
same `firestore.rules`, so anything done on the website shows up in the app and vice versa.

## Roles

| Role | How the account is created | Sees |
| --- | --- | --- |
| ADMIN | First one bootstrapped in the Firebase console (below); others by an admin | Everything in the school; manages users, teachers, learners, classes, subjects, assignments, notices, settings |
| TEACHER | Admin → Teachers → Add Teacher | Own classes and the learners in them; assignments for own classes; teacher and own notices |
| PARENT | Self-registration (pending until an admin approves) or Admin → Students → "+ Parent account" | Only linked children, their class, teachers, homework; parent notices |
| STUDENT | Admin → Students → "+ Learner login" | Own record, class subjects, homework, student notices |
| STAFF | Admin → Users & Parents → Create Account | Staff directory and staff notices |

Access is enforced by `firestore.rules`, not by the app. Accounts created by an admin
receive an email with a link to set their own password.

## Setup

1. **Firebase project** – in the Firebase console add an Android app with package
   `com.aistudio.discoveryprimary.kdpmsx`, download `google-services.json` and place it at
   `app/google-services.json`. Without it the app shows "Firebase is not configured" and
   sign-in is disabled.
2. **Authentication** – enable the *Email/Password* provider (and *Phone* if you want SMS
   sign-in for parents; phone sign-in also needs the app's SHA-1/SHA-256 fingerprints added
   in the console). Optionally customise the email templates (verification / password reset).
3. **Security rules** – deploy them: `npx firebase-tools deploy --only firestore:rules --project <your-project-id>`.
4. **First administrator** – create a user under *Authentication → Users*, copy its UID and
   create the document `users/<UID>` in Firestore with:
   `uid` = UID, `email`, `displayName`, `phoneNumber` = "", `role` = "ADMIN",
   `schoolId` = "discovery-primary", `active` = true (boolean).
   On first sign-in the admin is asked to verify the email address.
5. **Website** – in the Firebase console add a *Web app*, copy `web/firebase-config.example.js`
   to `web/firebase-config.js` and paste its config (these values are public identifiers, not
   secrets). Then publish it, either:
   - Firebase Hosting: `npx firebase-tools deploy --only hosting,firestore:rules --project <your-project-id>`,
     or automatically from GitHub: add the repository secrets `FIREBASE_PROJECT_ID`,
     `FIREBASE_SERVICE_ACCOUNT` (service account JSON key with the *Firebase Admin* role) and
     `FIREBASE_WEB_CONFIG`; every push to `main` then runs `.github/workflows/firebase-hosting.yml`
     → `https://<your-project-id>.web.app`, or
   - GitHub Pages: *Settings → Pages → Source: GitHub Actions*; every push to `main` runs
     `.github/workflows/pages.yml` → `https://<owner>.github.io/<repo>/`. Add that domain under
     *Authentication → Settings → Authorized domains*.
   If `firebase-config.js` is missing, the site shows a setup screen instead of failing.
   For the GitHub Pages deployment you can instead store the config JSON in the repository
   secret `FIREBASE_WEB_CONFIG` (nothing is committed).
6. Release signing (optional): set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.
   Keystores and `.env` files are git-ignored; never commit them.

## Build and test

```bash
./gradlew :app:assembleDebug            # build
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:lintDebug                # lint

# Security rules + auth flows against the Firebase emulators (Node 20+ and Java 17+)
cd tests/firestore-rules && npm install && npm test

# Web portal: unit + Playwright browser tests (desktop and mobile) against the emulators
cd tests/web && npm install && npx playwright install chromium && npm test

# Android end-to-end UI tests on a device/emulator against the Firebase emulators
cd tests/firestore-rules && npx firebase emulators:exec --only auth,firestore \
  --project demo-discovery-primary "cd ../.. && ./gradlew :app:connectedDebugAndroidTest"
```

CI (`.github/workflows/ci.yml`) runs all of the above on every push and uploads the debug APK
(`app-debug-apk` artifact). Add the repository secret `GOOGLE_SERVICES_JSON` (contents of
`google-services.json`) so that APK connects to your Firebase project.
