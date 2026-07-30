# Android App Design Decisions

## Identity & Scope

### 1. Target User Persona
**General consumers** — everyday users who want a privacy-focused email client, with SES as a hidden backend detail.

### 2. V1 Feature Scope
**Full-featured** — inbox, compose, reply/forward, drafts, attachments, search, folders/labels, spam filtering, and signatures.

### 3. Account Model
**Multi-account** — users can add multiple email addresses/identities and switch between them.

## Architecture & Backend

### 4. Inbound Email
**App polls an API** — the app periodically fetches new emails from the existing REST API backed by SES receipt storage.

### 5. Backend
**Existing backend** — the app integrates with the same backend that powers the Numaeel web app. No direct AWS access from the device.

### 6. Authentication
**OAuth/OIDC via Authress** — users sign in via Authress using social logins, passkeys, or email/password.

### 7. Storage Strategy
**Hybrid with offline** — full local Room database that syncs bidirectionally with the backend. Users can read and draft offline, and changes sync when connected.

### 8. Sync Strategy
**Persistent foreground sync** — ForegroundService maintains a persistent connection when online, instantly syncs changes both ways. Queues operations offline for replay on reconnect. The local database is the source of truth for the UI.

### 9. Conflict Resolution
**Last-write wins** — whichever change has the latest timestamp (web or mobile) overwrites the other during sync.

### 10. Database Migrations
**Room auto-migrations** — use Room's built-in auto-migration support for schema changes between app versions.

## UX & Navigation

### 11. Navigation Model
**Mirror the web sidebar** — replicate the web app's sidebar as a slide-out drawer on Android: Inbox, Quarantine, Spam, Drafts, Rules, Templates, Labels, Settings.

### 12. Compose Experience
**Full-screen with Markdown** — dedicated full-screen compose screen with Markdown input and Edit/Preview toggle.

### 13. Compose Preview
**Edit/Preview toggle** — two tabs in the compose screen to switch between Markdown input and rendered preview.

### 14. Alias Picker in Compose
**Default + bottom sheet** — default to the account's primary alias, with a "Change sender" link that opens a bottom sheet with all aliases.

### 15. Inbox Layout
**Simplified single list** — single Active inbox list with swipe-to-archive. Archived and All accessible from a filter/menu rather than persistent tabs.

### 16. Thread Detail View
**Collapsed with expand** — show latest signal expanded, older ones collapsed with sender/timestamp headers. Tap to expand.

### 17. Swipe Actions
**Popup menu** — swiping an inbox item reveals a popup with: archive, delay (followupAt), delete, and add label.

### 18. Delay / Snooze (followupAt)
**Presets + custom picker** — when tapping "delay" in the swipe popup, show preset options (1 hour, 4 hours, Tomorrow morning, Next week) plus a custom date/time picker. Archives the thread with a `followupAt` timestamp so it resurfaces later.

### 19. Bulk Actions
**Long-press multi-select** — long-press to enter multi-select mode, then tap to select more. Bulk action bar appears at the bottom with archive/delete/label.

### 20. Onboarding Flow
**Mirror web 5-step wizard** — account creation → domain DNS setup → retention selection → test email → celebration with feature tour.

### 21. Feature Tour
**Full spotlight tour** — spotlight overlay tour matching the web: 4+ steps highlighting key features with tooltip cards after onboarding.

### 22. Account Switcher
**Drawer header dropdown** — account avatar/icon in the drawer header, tap to expand a dropdown list of accounts to switch.

### 23. Unsubscribe
**One-tap in thread** — prominent unsubscribe button in thread detail for mailing lists.

### 24. Block Sender
**Overflow + confirm dialog** — in the thread detail overflow menu with a confirmation dialog to prevent accidental blocking.

## Data & Sync

### 25. Offline Behavior
**Full offline support** — read cached emails, compose drafts, queue sends, and sync everything when back online. This is first-class offline support, not a cached strategy.

### 26. Attachments
**Download on demand** — show attachment metadata in thread view, download only when the user taps. Saves storage and bandwidth.

### 27. Search
**Local only** — search the local Room database for instant results. Limited to what has been fetched and synced.

### 28. Drafts
**Both screen + inline** — dedicated Drafts screen lists all drafts, and they also appear inline when viewing the thread.

## Notifications & Real-time

### 29. Notification Strategy
**Fetch on load + pull-to-refresh** — no push notifications or background polling. Emails are fetched when the user opens the app and via pull-to-refresh gesture (pulling down from the top of the screen).

### 30. Real-time Requirements
**Delivery status only** — show send delivery status updates (sent/delivered/bounced) in real-time after composing, but no live inbox updates.

### 31. Deferred Send (Undo)
**Notification-based undo** — immediately return to inbox after tapping send. Show a system notification with an Undo action button during the 8-second window.

### 32. Notification Channels
**Per-category channels** — separate Android notification channels for inbox, quarantine, spam, delivery status, and system alerts. Users can independently control each.

## Feature Scope on Mobile

### 33. Labels
**Full CRUD on mobile** — users can create, edit, and delete labels (name, color, emoji) directly on mobile.

### 34. Rules
**View + toggle only** — users can view existing rules and enable/disable them, but must use the web app to create or edit rules.

### 35. Templates
**View + use** — view template list, preview them, and use them in compose. Editing/creation is web-only.

### 36. Custom Views
**Exclude from mobile** — custom sidebar views (saved workflow filters) are web-only. Mobile uses the standard screens.

### 37. Workflow Panels
**All 14 workflow types** — full workflow panels matching the web: structured data display for auth, travel, payments, scheduling, conversation, CRM, package, alert, content, status, healthcare, job, support, and test.

### 38. Quarantine
**Dedicated screen + buttons** — quarantine has its own screen from the drawer. Approve/Reject buttons on each row or in the detail view.

### 39. Spam
**Separate screen** — dedicated Spam screen in the drawer matching the web, with its own list and detail views.

### 40. Admin Panel
**Full admin on mobile, behind settings toggle** — Signal Inspector, health check, raw email retrieval, reprocess actions. Hidden by default; users enable it via a toggle in settings.

### 41. Stats
**Full stats on mobile** — stats dashboard with charts (daily/monthly email volume, workflow breakdown) matching the web.

### 42. Billing
**View plan, change on web** — show current plan info and usage, but redirect to the web app for plan changes and Stripe checkout.

### 43. Support
**In-app ticket form** — submit support tickets directly from the app with category and description.

### 44. Changelog
**Post-update dialog** — shows highlights of the latest version on first launch after update, then dismissible.

## Settings

### 45. Settings Tabs
**All four tabs on mobile** — Aliases, Email & Forwarding, Profile & Security, and Team management. Full parity with the web.

### 46. Domain DNS Management
**Full DNS setup** — show MX, SPF, DKIM, DMARC records with copy buttons and re-check verification, available in settings as well as during onboarding.

### 47. Forwarding Management
**Full management** — add/remove forwarding addresses, trigger verification.

### 48. MFA / Passkeys
**Full MFA management** — add/remove passkeys, manage MFA devices, link identity providers.

## Security & Compliance

### 49. Privacy & Data Residency
**GDPR + Swiss FADP** — full compliance with both EU GDPR and Swiss Federal Act on Data Protection. Encryption at rest on-device, data export, and deletion rights.

### 50. On-Device Secrets
**EncryptedSharedPreferences** — store OAuth tokens using AndroidX Security's EncryptedSharedPreferences (hardware-backed via Android Keystore internally). No AWS credentials stored on device since the backend proxies all API calls.

### 51. Biometric Lock
**Optional biometric lock** — users can enable an app-level biometric/PIN lock in settings.

### 52. Data Export
**No export** — use the share-out feature to send email content to other apps as text. No .eml or PDF export.

## Branding & Platform

### 53. App Name
**Numaeel** — listed as "Numaeel" on the Play Store, matching the web app brand.

### 54. App Icon
**Numaeel brand icon** — use the existing Numaeel logo/mark from the web app adapted for Android adaptive icon format.

### 55. Theme
**All 4 Catppuccin flavors** — Latte (light), Frappe, Macchiato, and Mocha (dark) with a theme picker in settings.

### 56. Tablet Layout
**Split list-detail** — side-by-side list + detail pane on tablets, leveraging the extra screen space.

### 57. Splash Screen
**Animated logo splash** — Numaeel logo with a brief entrance animation (fade/scale) using the Android 12+ Splash Screen API's animated icon support.

### 58. Edge-to-Edge
**No edge-to-edge** — respect both system bars, no edge-to-edge drawing.

### 59. Typography
**System default (Roboto)** — consistent with the Android platform, no extra assets to bundle.

### 60. Predictive Back
**Full predictive back** — shows the previous screen peeking behind during the back swipe. Android 14+ gesture support.

## Android Platform Integration

### 61. Deep Links & Intents
**Maximum intent coverage:**
- `mailto:` URI handling (default email handler)
- Share intent (receive text/URLs from other apps into compose)
- App deep links (`numaeel://` or `https://` to specific threads/screens)
- Share out (email content to other apps)
- Voice assistant actions ("Hey Google, send an email to...")
- App Shortcuts (long-press icon: Compose, Search, Drafts)
- Home screen widgets (all sizes: small unread count, medium recent list, large list + compose)
- Android Auto (hands-free email read-aloud and voice reply)

### 62. Home Screen Widget
**All sizes available** — small (unread count), medium (recent emails list), large (list + compose button). User picks the size.

### 63. Wear OS
**Notifications + actions + summaries** — new email notifications on watch with dismiss/archive actions. Incoming email summary display. Optional show of active resources like QR codes for events.

### 64. Signatures
**No signatures** — users type manually or rely on templates.

## Interaction & Polish

### 65. Animations
**Full Material Motion** — shared element transitions between list and detail, container transforms, fade-through for navigation.

### 66. Empty States
**Celebration for inbox, simple elsewhere** — mirror the web's inbox zero celebration animation. Other screens get simple icon + text + CTA.

### 67. Loading States
**Instant from cache + sync icon** — show content instantly from the local Room database. A spinning sync icon in the toolbar indicates background sync is active.

### 68. Error Handling
**Snackbar + retry** — Material Snackbar at the bottom with a Retry action. Non-blocking, dismissible, standard Android pattern.

### 69. Haptics
**Rich haptics** — haptic feedback on key actions: swipe threshold, archive, delete, send, pull-to-refresh, long-press select.

### 70. Links in Emails
**Long-press preview** — clickable links open in browser. Long-press shows URL domain and an "Open in browser" action sheet.

### 71. Remote Images
**Always load** — emails render fully on first open, no image blocking or extra taps needed.

### 72. Clipboard
**Copy buttons + text selection** — dedicated copy buttons on structured workflow data (auth codes, tracking numbers) plus standard Android text selection everywhere else.

### 73. Accessibility
**WCAG 2.1 AAA** — maximum accessibility including enhanced contrast, full TalkBack support, proper touch target sizes, content descriptions on all interactive elements.

### 74. Localization
**i18n ready** — all strings externalized in Android resource files so the system can automatically apply relevant translations.

## Technical Stack

### 75. Min SDK
**31 (Android 12)** — gains Material You dynamic colors, splash screen API, and app links natively.

### 76. Dependency Injection
**Manual (composition root)** — no DI framework. All dependencies injected manually at the composition root. Remove Hilt from the scaffold.

### 77. HTTP Client
**Retrofit + OkHttp** — industry standard, mature, excellent interceptor support.

### 78. Image Loading
**Coil** — Kotlin-first, Compose-native, coroutine-based, lightweight.

### 79. Markdown Rendering
**Markwon (AndroidView)** — mature Android Markdown renderer, extensible with plugins, wrapped in Compose via AndroidView.

### 80. JSON Serialization
**Moshi** — modern JSON library, great Retrofit integration, codegen support.

### 81. Pagination
**Paging 3 (Jetpack)** — RemoteMediator for Room + API, built-in Compose LazyColumn support.

### 82. Architecture Pattern
**Clean Architecture + MVVM** — full separation with domain/data/presentation layers, use cases, repository interfaces. ViewModels expose StateFlow to Compose UI.

### 83. Navigation
**Jetpack Navigation Compose** — official Google library, type-safe routes, deep link support built-in.

### 84. Crash Reporting
**PostHog (existing)** — use the already-integrated PostHog for crash capture and product analytics. No additional crash reporting service.

### 85. Code Shrinking
**R8 shrink only, no obfuscation** — smaller APK with dead code removal, easier crash debugging without obfuscated stack traces.

### 86. App Updates
**Play Store auto-update** — rely on standard Play Store auto-updates, no in-app prompting.

### 87. Testing Strategy
- **Unit tests** — JUnit5 + MockK for ViewModels, use cases, and repositories
- **Compose UI tests** — compose-test for screen-level interaction testing
- **Integration tests** — Hilt test modules with in-memory Room database
