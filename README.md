# FinnaCalc Android

Native Android port of FinnaCalc (finnacalc.com) — Kotlin + Jetpack Compose.
Ported screen-for-screen from the SwiftUI iOS app (`FinnaCalcIOS` repo), which
is the source of truth for design and behavior.

## Stack

- Kotlin 2.1, Jetpack Compose (Material 3 scaffolding, custom FinnaCalc theme tokens)
- Navigation Compose, ViewModel + StateFlow, DataStore
- OkHttp (+ SSE for FinnaBot streaming), kotlinx.serialization
- minSdk 26, target 35, compile 36

## Build

Requires JDK 17+ and the Android SDK (`local.properties` → `sdk.dir`).

```bash
./gradlew :app:assembleDebug
```

See [PORT_PLAN.md](PORT_PLAN.md) for phase status and iOS→Android idiom map.
