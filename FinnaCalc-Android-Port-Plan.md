# FinnaCalc → Android (Flutter / Dart) — Port Plan

The Android sibling of the iOS port plan. Same backend, same goal — feature and visual parity with the web app — different client. With this, FinnaCalc has three clients hitting the same Next.js API: the website, the Swift/SwiftUI iOS app, and this Flutter/Dart Android app.

## How to use this with Claude Code

Same pattern as the iOS repo: save this as `PLAN.md` in the new Flutter project's root, keep the FinnaCalc web repo as a sibling folder so it's reachable (`claude --add-dir ../FinnaCalc` from inside this project), and work one phase per session — a session start line is included under each phase below.

## Architecture

Identical decision to the iOS plan: this app calls the existing Next.js API unchanged, it doesn't reimplement any backend logic in Dart. No reason to repeat the full rationale here — see `PLAN.md` in the iOS repo or `CLAUDE.md` in the FinnaCalc repo if you want the full writeup.

## What "carbon copy" means in practice, Flutter-specific

Two of the three caveats from the iOS plan carry over directly: SnapTrade's connection flow is a hosted web portal (Chrome Custom Tabs here instead of `SFSafariViewController`), and the TradingView widgets get wrapped in a `webview_flutter` view rather than rebuilt natively. One caveat is new to Flutter specifically: its default widgets (`ElevatedButton`, `Card`, etc.) carry Material Design's look out of the box — ripples, elevation, Material's own color and shape defaults — which fights a pixel-precise shadcn copy harder than SwiftUI's relatively neutral defaults did on iOS. Build the `FC*` components as custom widgets composed from basics (`Container`, `GestureDetector`, `InkWell` only where you specifically want a tap ripple) rather than thin wrappers around Material's pre-styled widgets, and override `ThemeData` aggressively rather than leaning on its defaults.

## Phase 0 — Backend readiness (shared with iOS — don't redo it)

This is the exact same backend work as the iOS plan's Phase 0, in the same FinnaCalc repo. It's platform-agnostic: once it's done, it's done for every client. If it's already underway for iOS, there's nothing left to do here. If not, the two items are:

1. No API route under `app/api/` verifies a Supabase session — add Bearer-token verification to the routes that touch personal data (`plaid/*`, `snaptrade/*`, `budget-advisor`, `efile`).
2. Plaid tokens and SnapTrade credentials aren't persisted to a user — they live in a cookie or get re-exchanged per request. Move both into Supabase tables keyed by `auth.uid()`.

Full detail, exact route names, and table schemas are in the FinnaCalc repo's `CLAUDE.md`.

## Backend endpoint reference

Same 14 routes as the iOS plan, unchanged:

| Endpoint | Purpose | Used in phase |
|---|---|---|
| `POST /api/plaid/create-link-token` | Starts a Plaid Link session | 4 |
| `POST /api/plaid/transactions` | Bank transactions (90 days) | 4 |
| `POST /api/plaid/holdings` | Investment holdings via Plaid | 4 |
| `POST /api/plaid/liabilities` | Loans/credit liabilities | 4 |
| `POST /api/snaptrade/connect` | Generates brokerage connection portal URL | 5 |
| `GET /api/snaptrade/accounts` | Connected brokerage accounts | 5 |
| `POST /api/snaptrade/disconnect` | Unlinks a brokerage | 5 |
| `GET /api/stock` | Single stock quote | 5 |
| `GET /api/stock-search` | Ticker search/autocomplete | 5 |
| `GET /api/screener` | Stock screener results | 5 |
| `GET /api/top-movers` | Market top movers | 5 |
| `GET /api/market-overview` | Market summary data | 5 |
| `POST /api/budget-advisor` | AI budget recommendations | 4 |
| `POST /api/chat` | FinnaBot (Gemini, plain UTF-8 text stream) | 7 |
| `POST /api/efile` | Tax e-file submission | 6 |

## Phase 1 — Flutter project + design system

**You do this part:**
- Flutter SDK installed, `flutter doctor` reporting clean for Android.
- `flutter create` the project; set `minSdkVersion 21` in `android/app/build.gradle` (Plaid's Android SDK requires it).
- Add to `pubspec.yaml`: `supabase_flutter`, `google_sign_in`, `plaid_flutter`, `flutter_custom_tabs`, `webview_flutter`, `dio`, `provider`, `fl_chart`.
- Google Cloud Console: create an Android OAuth client ID, which needs your app's package name **and** SHA-1 certificate fingerprint. Get the debug one with:
  ```
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
  ```
  Add both the Android client ID and your existing web client ID to Supabase's Google provider config (Supabase needs the web client ID specifically as the `serverClientId` for native sign-in to work).
- If you'll support OAuth-redirect banks in Plaid: register your Android package name under Plaid Dashboard → Team Settings → API → Allowed Android Package Names, and publish `https://finnacalc.com/.well-known/assetlinks.json` (Android's equivalent of the iOS `apple-app-site-association` file) for App Links verification.

Same exact color tokens as the iOS plan, pulled from `app/globals.css`:

| Token | Light | Dark |
|---|---|---|
| background | 0 0% 100% | 222.2 84% 4.9% |
| foreground | 222.2 84% 4.9% | 210 40% 98% |
| card / popover | 0 0% 100% | 222.2 84% 4.9% |
| primary | 221.2 83.2% 53.3% | 217.2 91.2% 59.8% |
| secondary / muted / accent | 210 40% 96% | 217.2 32.6% 17.5% |
| destructive | 0 84.2% 60.2% | 0 62.8% 30.6% |
| border / input | 214.3 31.8% 91.4% | 217.2 32.6% 17.5% |
| ring | 221.2 83.2% 53.3% | 224.3 76.3% 48% |
| radius | 0.75rem (12dp) | — |

Flutter ships HSL-to-RGB conversion natively, so no custom extension is needed — just:

```dart
Color hsl(double h, double s, double l) =>
    HSLColor.fromAHSL(1.0, h, s / 100, l / 100).toColor();
```

**Session start:** "Read the Phase 1 section of PLAN.md. Build a `theme.dart` with light/dark `ColorScheme`s using the HSL table and the `hsl()` helper given. Then build `FCButton` (primary/secondary/destructive/ghost), `FCCard`, `FCTextField`, and `FCBadge` as custom widgets — not wrapped Material widgets — matching the variants in `../FinnaCalc/components/ui`."

## Phase 2 — Auth + navigation shell

Build an `AuthService` (`ChangeNotifier`, via the `provider` package) wrapping `supabase_flutter`: email/password sign in/up via `signInWithPassword`/`signUp`, native Google sign-in via `google_sign_in` → `signInWithIdToken`, sign out, and session state via `supabase.auth.onAuthStateChange`. Apple sign-in doesn't have a native-dialog equivalent on Android — skip it, or fall back to Supabase's generic web-based `signInWithOAuth` if you want it available at all. Build the nav shell with a `BottomNavigationBar` or `NavigationRail`, mirroring `components/header.tsx`'s structure.

**Session start:** "Implement Phase 2 from PLAN.md. Reference `../FinnaCalc/lib/auth.tsx` and `../FinnaCalc/components/header.tsx` for the exact auth methods and nav structure to mirror."

## Phase 3 — Standalone calculators

Same list as iOS, no backend or SDK dependency: `loan-calculator`, `roi-calculator`, `break-even-calculator`, `profit-margin-calculator`, `startup-cost-calculator`, `pricing-calculator`, `cash-flow-calculator`, `emergency-fund-calculator`, `employee-contractor-calculator`.

**Session start:** "Implement Phase 3 from PLAN.md, one calculator at a time, starting with loan-calculator. Port the calculation logic and form layout from `../FinnaCalc/app/loan-calculator` exactly."

## Phase 4 — Budgeting + Plaid

`plaid_flutter` wraps Plaid's real native Android/iOS/web SDKs (it's a well-maintained community package, not published by Plaid directly, but it's the standard choice and uses the same underlying native code Plaid ships). Flow: `PlaidLink.create(configuration: LinkTokenConfiguration(token: ...))` then `PlaidLink.open()`, fed by `/api/plaid/create-link-token`. Covers `bank-connect.tsx`, `debt-card.tsx`, `budget-advisor.tsx`, and everything under `app/budgeting`.

**Session start:** "Implement Phase 4 from PLAN.md. Reference `../FinnaCalc/components/bank-connect.tsx`, `debt-card.tsx`, and `budget-advisor.tsx`. Use the plaid_flutter package's LinkTokenConfiguration flow."

## Phase 5 — Investing, SnapTrade, and market data

`brokerage-connect.tsx` → open the URL from `/api/snaptrade/connect` with `flutter_custom_tabs`, using a custom URL scheme (the same one as the iOS app, e.g. `finnacalc://snaptrade-callback`, registered as an Android intent filter) as the redirect target. One thing to watch: `flutter_custom_tabs`' own docs note that coordinating Custom Tabs with App Links/deep-link redirects isn't officially supported in all cases — test the redirect-back path early rather than assuming it'll just work, and fall back to `webview_flutter` for this one flow if it doesn't.

Market data and investing views (`stocks-page.tsx`, `bonds-page.tsx`, `safe-investment-options.tsx`, `investing-options.tsx`, `markets-dashboard.tsx`, `dashboard-screener.tsx`, `dashboard-watchlist.tsx`) → `fl_chart` for anything currently in `recharts`. `tradingview-chart.tsx`, `-mini.tsx`, `-news.tsx` → the same widget URLs wrapped in `webview_flutter` for v1.

**Session start:** "Implement Phase 5 from PLAN.md. Start with `brokerage-connect.tsx`'s SnapTrade flow using flutter_custom_tabs and a custom URL scheme, then move to the market data views."

## Phase 6 — Taxes

Same scope and same warning as iOS: the largest, riskiest phase. `tax-calculator` and `taxes` routes, `tax-calculators.tsx`, `tax-education.tsx`, `tax-filing-interface.tsx`, all of `components/tax-engine/` (~450KB), and `/api/efile`.

**Session start:** "Don't write any Dart yet. Read through `../FinnaCalc/components/tax-engine` and produce an inventory of its calculation modules and how they depend on each other, so we can port it module by module instead of all at once."

## Phase 7 — FinnaBot chat

`/api/chat` returns a plain UTF-8 text stream. `dio` can consume this directly with `ResponseType.stream` and listening to the byte stream, appending chunks as they arrive — no SSE parsing needed, same as iOS.

**Session start:** "Implement Phase 7 from PLAN.md. Reference `../FinnaCalc/components/Chatbot.tsx`. Stream `/api/chat`'s response using dio with ResponseType.stream, appending raw text chunks to the active message as they arrive."

## Phase 8 — Remaining pages + polish

`about`, `advising`, `education` (`financial-education-hub.tsx`), `premium`, `pricing-calculator`, `sign-in`/`sign-up` forms, `privacy`, `terms`. Then: app icon from the existing logo assets in `public/`, splash screen, a dark-mode pass, accessibility (TalkBack labels, text scaling), and an internal testing track on Google Play.

## Suggested project structure

```
lib/
  app/                     entry point, root navigation
  core/
    networking/            Dio client, endpoint definitions, auth header injection
    auth/                  AuthService (wraps supabase_flutter)
    design_system/         theme.dart, FCButton, FCCard, FCTextField, FCBadge
  features/
    calculators/
    budgeting/
    investing/
    taxes/
    chat/
  models/                  classes matching the API's JSON shapes (with fromJson/toJson)
```

## Suggested order

Same logic as iOS: Phase 0 and 1 first, since nothing visual can start before the design system exists. Phase 2 next for auth and navigation. Phase 3 (calculators) is the fastest way to prove the whole pipeline works before tackling Plaid or SnapTrade. Phases 4 through 7 don't block each other — order them by your own priorities rather than a hard dependency. Phase 6 (taxes) is worth saving for last among them regardless, since it's the biggest single chunk of logic to port faithfully. Phase 8 is always last.
