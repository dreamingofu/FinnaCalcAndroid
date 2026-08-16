# FinnaCalc Android — working notes

Native Kotlin + Jetpack Compose port of the SwiftUI app at `../FinnaCalcIOS`
(source of truth for design/behavior). Calls the Next.js API at
`https://www.finnacalc.com`. See PORT_PLAN.md for phase status and idiom map.

## Build / verify

No Android Studio on this machine. JDK is Homebrew OpenJDK 21:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Both must pass before opening a PR. `local.properties` (gitignored) points
`sdk.dir` at `~/Library/Android/sdk`.

## Workflow

Branch per phase/task → PR → squash-merge. Never commit directly to `main`.

## Conventions (ported from the iOS repo — full text in ../FinnaCalcIOS/CLAUDE.md)

- **Never display a fabricated financial figure.** `—` while loading, invite
  when empty, omit what can't be derived honestly.
- **Label instruments honestly** — SPY is "S&P 500 ETF", never the index itself.
- **Colors only via Theme tokens** (both palettes); no fixed hexes in screens.
- **Money/figures use the mono figure style**, not the sans face.
- **Destructive actions confirm first** and name exactly what is deleted.
- **Deleting budget items must reschedule notifications.**
- **Document deliberate deviations** from the iOS app in the file header, with
  the reason.
