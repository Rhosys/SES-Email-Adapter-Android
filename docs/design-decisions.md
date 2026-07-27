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

## UX & Navigation

### 8. Navigation Model
**Mirror the web sidebar** — replicate the web app's sidebar as a slide-out drawer on Android: Inbox, Quarantine, Spam, Drafts, custom Views, Rules, Templates, Labels, Settings.

### 9. Compose Experience
**Full-screen with Markdown** — dedicated full-screen compose screen with Markdown input and Edit/Preview toggle, adapted from the web app's approach.

### 10. Inbox Layout
**Simplified single list** — single Active inbox list with swipe-to-archive. Archived and All accessible from a filter/menu rather than persistent tabs.

### 11. Onboarding Flow
**Mirror web 5-step wizard** — account creation → domain DNS setup → retention selection → test email → celebration with feature tour.

## Data & Sync

### 12. Offline Behavior
**Full offline support** — read cached emails, compose drafts, queue sends, and sync everything when back online.

### 13. Attachments
**Download on demand** — show attachment metadata in thread view, download only when the user taps. Saves storage and bandwidth.

### 14. Search
**Local only** — search the local Room database for instant results. Limited to what has been fetched and synced.

## Notifications & Real-time

### 15. Notification Strategy
**Fetch on load + pull-to-refresh** — no push notifications or background polling. Emails are fetched when the user opens the app and via pull-to-refresh gesture.

### 16. Real-time Requirements
**Delivery status only** — show send delivery status updates (sent/delivered/bounced) in real-time after composing, but no live inbox updates.

## Security & Compliance

### 17. Privacy & Data Residency
**GDPR + Swiss FADP** — full compliance with both EU GDPR and Swiss Federal Act on Data Protection. Encryption at rest on-device, data export, and deletion rights.

### 18. On-Device Secrets
**EncryptedSharedPreferences** — store OAuth tokens using AndroidX Security's EncryptedSharedPreferences (hardware-backed via Android Keystore internally). No AWS credentials stored on device since the backend proxies all API calls.

## Branding & Platform

### 19. Visual Identity
**Match Numaeel web (Catppuccin)** — use the same Catppuccin color palette (Mocha dark default + Latte light) and Numaeel branding from the web app.

### 20. Platforms & Form Factors
**Android phones + tablets + Wear OS** — adaptive UI with list-detail layouts for tablets, a Wear OS companion for notifications and quick replies. iPhone will be a separate future app.
