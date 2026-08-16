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
- **1. Design system** — port `Core/DesignSystem` (Theme.swift tokens both palettes, figure/mono type styles, FCButton/FCCard/FCTextField/FCBadge equivalents, Motion). Bundle IBM Plex fonts.
- **2. Auth + shell** — Supabase auth (`Core/Auth`), RootView tab shell (5 tabs: Home, Budgeting, Investing, Taxes, Education), account sheet, splash, appearance setting.
- **3. Calculators** — `Features/Calculators`: nine standalone calculators.
- **4. Budgeting + Plaid** — `Core/Networking` API client, `Features/Budgeting` (BudgetStore, dashboard, editor, advisor), Plaid link.
- **5. Investing** — SnapTrade, market data, portfolio ledger, charts.
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
