# Discovery Primary LMS (Android)

Kotlin / Jetpack Compose app backed by Firebase Authentication and Cloud Firestore.

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
5. Release signing (optional): set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.
   Keystores and `.env` files are git-ignored; never commit them.

## Build and test

```bash
./gradlew :app:assembleDebug            # build
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:lintDebug                # lint

# Security rules + auth flows against the Firebase emulators (Node 20+ and Java 17+)
cd tests/firestore-rules && npm install && npm test

# End-to-end UI tests on a device/emulator against the Firebase emulators
cd tests/firestore-rules && npx firebase emulators:exec --only auth,firestore \
  --project demo-discovery-primary "cd ../.. && ./gradlew :app:connectedDebugAndroidTest"
```

CI (`.github/workflows/ci.yml`) runs all of the above on every push.
