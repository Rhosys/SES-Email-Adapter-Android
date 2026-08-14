# TODO

Open work on the Android app, most blocking first.

---

## Blocking a working build

### ~~Authress application ID~~ — resolved

Now defaults to `app_2EAWGEdtzaeCj7b45DsDtt`, taken from the web app's
`VITE_AUTHRESS_APPLICATION_ID`. Still overridable with
`-PauthressApplicationId=<id>` per environment.

### ~~OAuth endpoints and redirect handling~~ — resolved

Authress is not a plain OAuth provider, so there is no authorize/token exchange
to point at. The app now ports @authress/login-react-native directly:

```
POST /api/authentication                              -> authenticationUrl + authenticationRequestId
open authenticationUrl in a Custom Tab (the real browser, so passkeys work)
redirect to ch.rhosys.email://auth/callback           -> code + authenticationRequestId
POST /api/authentication/{id}/tokens                  -> session cookies
```

The session lives in the `authorization` and `user` cookies rather than an
access/refresh pair. `userIsLoggedIn()` refreshes it via `PATCH /session` and is
called on every route change, per the SDK's own recommendation; `waitForToken()`
supplies the Authorization header.

The duplicate redirect claim is gone with AppAuth: MainActivity is now the only
component matching the scheme, and it forwards the redirect through
`onNewIntent` as the SDK's Android setup describes.

The browser deliberately does not share cookies with the app — it does not need
to. The token exchange is made by the app's own HTTP client, so the session
cookie arrives there.

### App name

The user-visible name is still **Numaeel**, an invented brand. It appears in:

- `app/src/main/res/values/strings.xml` — home screen label
- `app/src/main/res/values/themes.xml` — `Theme.Numaeel`
- `presentation/auth/LoginScreen.kt`, `BiometricLockScreen.kt`
- `presentation/onboarding/OnboardingScreen.kt`, `FeatureTourDialog.kt`
- `presentation/navigation/AppScaffold.kt`
- `sync/SyncForegroundService.kt` — the sync notification
- `wear/src/main/res/values/strings.xml`, `wear/.../WearMainActivity.kt`
- `res/drawable/ic_launcher_foreground.xml` — placeholder "N" monogram
- `res/xml/shortcuts.xml` — `numaeel://` deep link scheme

Storage keys (`numaeel.db`, `numaeel_prefs`, `numaeel_secure_prefs`) are
deliberately excluded: they are invisible to users, and renaming them orphans
data on installed builds. Leave them unless a migration is worth writing.

---

## Not yet verified against a live backend

Nothing in the app has made a real call to `email.rhosys.cloud`. The routes and
response shapes come from the published OpenAPI document, but that document
declares **no request bodies** for any write operation, so every request body was
transcribed from the web client (`SES-Email-Adapter-UI/src/lib/api.ts`). Expect
mismatches on the first real round trip, particularly for:

- `PATCH /accounts/{id}/threads/{threadId}` — status, labels, followupAt
- `POST /accounts/{id}/threads/{threadId}/signals` — draft creation
- `PUT  /accounts/{id}/threads/{threadId}/signals/{id}` — draft update
- `POST /accounts/{id}/signals/{id}/quarantineResponse`

Worth asking the backend to add request bodies to the spec.

---

## Gaps the API does not currently support

These were removed rather than faked. Each needs a backend change before the UI
can come back.

| Feature | What is missing |
|:--|:--|
| Read / unread | No field or endpoint anywhere in the API. Inbox rows use `urgency` instead |
| Compose a new thread | Drafts post to `/threads/{threadId}/signals`; there is no route for a draft with no thread. Reply and forward work |
| Send later / undo send | No scheduling parameter, no cancel route. Sending is immediate |
| Attachment download | Attachments carry a fixed `url` and are opened directly; there is no download endpoint |
| MFA / passkey management | Not on the email API — but the login service has `GET`/`DELETE /api/session/devices`, which the SDK exposes as getDevices/deleteDevice. The Settings tab could be rebuilt against those |
| Billing | `billingPlan` is readable on an account, but there are no billing endpoints |
| Support tickets | No endpoint. `SupportData` in the spec is a signal workflow type, not a ticket API |
| Per-address sender blocking | Sender policy applies to a whole domain on an alias |

---

## Deferred by choice

- **Spam screen** — no API concept. Filtered mail surfaces under Quarantine
- **Admin screen** — `/healthcheck` and per-signal `reprocess` / `raw` are real
  and could back a reduced version whenever it is wanted

---

## Play Store

The app links intent filter claims `email.rhosys.cloud/mail`. Verification needs
`https://email.rhosys.cloud/.well-known/assetlinks.json` listing this package and
the **Play App Signing** SHA-256 fingerprint — not the upload key. Until that is
served, `autoVerify` fails and the domain claim is rejected.
