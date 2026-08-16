# FinnaCalc Android — native port plan

Native Kotlin + Jetpack Compose port of **FinnaCalcIOS** (SwiftUI, ~163 files /
~51k LOC at `../FinnaCalcIOS`). The iOS app is the source of truth: same
screens, same behavior, same API (`https://www.finnacalc.com`). Where SwiftUI
idioms don't map 1:1, use the closest Compose equivalent (noted below).

## Idiom map

| iOS (SwiftUI) | Android (Compose) |
|---|---|
| `@StateObject` / `ObservableObject` | `ViewModel` + `StateFlow` |
| `@EnvironmentObject` | constructor injection / `CompositionLocal` |
| `@AppStorage` | Jetpack DataStore (preferences) |
| `TabView` | `Scaffold` + `NavigationBar` + `NavigationHost` |
| `.sheet` | `ModalBottomSheet` / dialog destinations |
| `NavigationStack` | Navigation Compose |
| `NotificationCenter` cross-tab posts | shared `MutableSharedFlow` event bus |
| `URLSession` + `AsyncSequence` (SSE) | OkHttp + okhttp-sse |
| `Theme` tokens (light/dark) | custom Compose theme object (no Material color scheme reliance) |
| IBM Plex Sans + Mono (bundled) | same fonts via `res/font` |
| SF Symbols | Material icons (closest match), custom vectors where needed |

## Phases (each = branch → PR → squash-merge, build must pass)

- **0. Scaffold** — Gradle/AGP/Kotlin/Compose project, package `com.finnacalc.android`. ✅
- **1. Design system** — port `Core/DesignSystem` (Theme.swift tokens both palettes, figure/mono type styles, FCButton/FCCard/FCTextField/FCBadge equivalents, Motion). Bundle IBM Plex fonts. ✅
- **2. Auth + shell** — Supabase auth (`Core/Auth`), RootView tab shell (5 tabs: Home, Budgeting, Investing, Taxes, Education), account sheet, splash, appearance setting. ✅ (no Sign in with Apple on Android; Plans/Feedback/About sections follow their phases)
- **3. Calculators** — `Features/Calculators`: eleven standalone calculators (incl. Retirement + Compound Interest), temporary hub on the Home tab until Phase 8. ✅
- **4. Budgeting + Plaid** — `Features/Budgeting` + `Core/Plaid`. Split in two PRs:
  - **4a** — data layer: models, ChargeSchedule + date engine, BudgetStore, BankLedgerStore, TransactionCategorizer, CSV/statement parser, GoalProgress/Emoji/Ring/CategoryStyle, Plaid models + service. 18 unit tests. ✅
  - **4b** — UI part 1: Paper token layer, Budgeting hub, My Budget editor (month slots, caps, donut, item sheet with subscription schedules), month/cap/snapshot/history/account/period sheets, Plaid Link SDK flow, CSV import. ✅
  - **4c** — UI part 2: Goals, History, Subscriptions, Budget Analysis (advisor + BudgetFindings + SubscriptionDetector). ✅ (goal-alert + charge notifications land in Phase 8; the advisor's written AI report rides on chat in Phase 7)
- **5. Investing** — SnapTrade, market data, portfolio ledger, charts. Split in three PRs:
  - **5a** — services + data: Market models/service, SnapTrade models/service (+ cookie jar for its session), Brandfetch/Logo.dev logo URLs, WatchlistStore, SectorCatalog, PortfolioAnalytics engine, PortfolioFundamentalsStore. 14 unit tests. ✅
  - **5b** — market UI: Investing tab root with universal search, Discover (highlight carousel, news, category tiles), watchlist card, screener (list + heatmap), stock detail with the Canvas chart (line/candles, scrub, pinch-zoom, scales), sector pages. ✅ (ETF/bonds/safe reference pages fold into Phase 8's remaining pages)
  - **5c** — brokerage UI: connect flow, portfolio ledger + analytics views, order ticket, trade tracker, investing goals.
- **6. Taxes** — `Features/Taxes/Engine` (1040 engine, pure Kotlin + unit tests), interview UI, filing.
- **7. FinnaBot chat** — streaming chat (`Features/Chat`), shell-level conversation state.
- **8. Remaining + polish** — Education, Pages, Plans, Feedback, Goals (+ widget as Glance app widget), notifications (SubscriptionNotifier), real app icon, Home dashboard.

## Conventions carried over from iOS (see ../FinnaCalcIOS/CLAUDE.md)

- **Never display a fabricated financial figure.** `—` while loading; invite when empty; omit what can't be derived honestly.
- **Label instruments honestly** — SPY is "S&P 500 ETF", never "the index".
- Colors only via Theme tokens; light + dark both first-class.
- Money/figures use the mono figure style, not the sans face.
- Destructive actions confirm first and name what is deleted.
- Deleting budget items reschedules notifications.
- Document deliberate deviations from iOS in the file header, with reason.

## Verification gate (every PR)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest lint
```
