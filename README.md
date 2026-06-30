# FinnaCalc — Android

A Flutter/Dart port of [FinnaCalc](https://finnacalc.com) (the Next.js web app), targeting
feature and visual parity. This client calls the **existing Next.js API** rather than
re-implementing any backend logic — the website, the SwiftUI iOS app, and this app all
share one backend.

See [the port plan](FinnaCalc-Android-Port-Plan.md) for the full phased build plan, the
design tokens, and the API endpoint reference.

## Architecture

```
lib/
  app/                   entry point, root navigation shell
  core/
    config/              runtime config (reads assets/.env)
    networking/          Dio client, endpoint definitions, auth header injection
    auth/                AuthService (wraps supabase_flutter)
    design_system/       theme.dart + FCButton, FCCard, FCTextField, FCBadge, tokens
    util/                formatters (currency / percent) ported from lib/format.ts
  features/
    calculators/         standalone calculators (loan, roi, break-even, …)
    budgeting/           budgeting + Plaid
    investing/           stocks, bonds, markets, SnapTrade
    taxes/               tax engine + tax UI
    chat/                FinnaBot streaming chat
    pages/               static pages (about, privacy, terms, …)
  models/                JSON-shape models matching the API
```

The UI deliberately avoids Material's stock look (`ElevatedButton`, `Card`, ripples,
elevation) in favour of custom `FC*` widgets built to match the web app's shadcn/ui design.

## Configuration

Public client config is bundled from `assets/.env`. Copy [.env.example](.env.example) into
`assets/.env` and fill the blanks with the web app's **public** values (Supabase URL + anon
key, the Google *web* OAuth client ID). Never put server secrets in this file.

## Build

```bash
flutter pub get
flutter analyze
flutter test
flutter run        # on a connected Android device/emulator
```

Requires Flutter 3.44+ / Dart 3.12+. `minSdkVersion` is Flutter's managed default (24),
which satisfies Plaid (≥21) and modern AndroidX plugins.

## Status

All eight phases of the port are implemented (271 passing tests; `flutter analyze`
clean; debug APK builds):

1. **Design system** — shadcn/ui tokens + `FC*` widgets (light + dark).
2. **Auth + navigation** — Supabase (email/password + native Google), bottom-nav shell.
3. **Calculators** — all nine standalone calculators, math ported 1:1 with golden tests.
4. **Budgeting + Plaid** — dashboard, expense pie, debt card, streaming AI budget advisor.
5. **Investing** — market data, SnapTrade, Plaid portfolio + charts, TradingView webviews.
6. **Taxes** — the full ~6.5k-line 1040 engine ported to pure Dart with **176 golden tests**,
   plus the interview UI.
7. **FinnaBot chat** — streaming `/api/chat`.
8. **Pages + polish** — about/advising/premium/privacy/terms, education hub, app icon,
   splash screen, dark-mode toggle (More screen), and accessibility labels.

Dark mode is available via the **More** screen (header ⋯). The backend-dependent features
(auth, Plaid, SnapTrade, market data, AI) are built to the web's exact API contracts and need
the corresponding credentials in `assets/.env` and the backend's Phase 0 work to run live.
