# TODO

Open work on the Android app, most blocking first.

---

## Blocking a working build

### ~~Authress application ID~~ — resolved

Now defaults to `app_2EAWGEdtzaeCj7b45DsDtt`, taken from the web app's
`VITE_AUTHRESS_APPLICATION_ID`. Still overridable with
`-PauthressApplicationId=<id>` per environment.

### ~~OAuth endpoints~~ — resolved

The hardcoded `/authorize` and `/oauth/token` paths were invented and neither
exists. Authress publishes discovery at
`https://login.rhosys.cloud/.well-known/openid-configuration`:

```
authorization_endpoint  https://login.rhosys.cloud            (the issuer root)
token_endpoint          https://login.rhosys.cloud/api/authentication/oauth/tokens
code_challenge_methods  S256
scopes_supported        openid, profile
```

AuthressAuthManager now discovers these at runtime via
`AuthorizationServiceConfiguration.fetchFromIssuer` instead of hardcoding paths.
The requested scope dropped `email` and `offline_access`, neither of which is
advertised; refresh tokens come from the `refresh_token` grant, which is.

### OAuth redirect is claimed twice

`MainActivity` declares an intent filter for `ch.rhosys.email:/oauth2redirect`
(`AndroidManifest.xml`), and AppAuth's own `RedirectUriReceiverActivity` claims
the same scheme through the `appAuthRedirectScheme` manifest placeholder
(`app/build.gradle.kts`). Two components match the same redirect, so resolution
is non-deterministic.

If MainActivity wins, sign-in breaks silently: it never reads the incoming
intent — there is no `onNewIntent` override and `getIntent()` appears nowhere in
`app/src` — so the authorization code is dropped. AppAuth needs its own receiver
to complete the exchange, which makes the comment on the placeholder
("our redirect is actually captured by MainActivity's intent-filter") backwards.

Fix is most likely to delete the MainActivity filter and let AppAuth handle it.
Worth doing alongside the Authress application id, since both block login.

Separately, a custom-scheme redirect can be registered by any app on the device.
Prefer an HTTPS App Link redirect on `email.rhosys.cloud` once assetlinks.json is
served (see the Play Store section).

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
| MFA / passkey management | No endpoints |
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
