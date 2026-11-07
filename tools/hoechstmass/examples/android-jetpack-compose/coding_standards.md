# Android Jetpack Compose Coding Standards & Engineering Invariants

This document specifies the authoritative software engineering (SWE.1 / SWE.2) standards for modern Android development with Jetpack Compose, established and proven across 53+ sprints in **aTrainingTracker**.

---

## 1. Core Engineering Principles

### 1.1 Reuse Before Create & DRY (Rule 36)
* Always inspect existing packages before implementing a new helper, calculator, or formatter.
* **Canonical Formatters**: All telemetry metrics (distance, speed, pace, heart rate, elevation, power) MUST route through canonical formatters (e.g. `DistanceFormatter`, `SpeedFormatter`, `PaceFormatter`).
* **Strict Prohibition**: Manual string concatenation or raw formatting (e.g., `String.format("%.1f km", dist / 1000f)` or `"${meters} m"`) in UI composables or ViewModels is strictly forbidden. It causes subtle rounding discrepancies, violates user preference units (imperial vs. metric), and breaks internationalization.

---

## 2. Jetpack Compose State Hygiene & Recomposition Integrity

### 2.1 State Hoisting & Unidirectional Data Flow (UDF)
* Composables must be stateless whenever possible. Events flow up (callbacks / lambdas); state flows down (`StateFlow` / immutable data classes).
* Hoist state to the ViewModel or screen container.

### 2.2 Recomposition Safety
* **No Side-Effects in Composition**: Never execute database calls, network requests, or shared preference writes directly within the body of a `@Composable`.
* **State Mutation Invariant**: Never write to a `MutableState` during composition or inside a `remember { }` calculation. All state mutations must occur inside event callbacks or `LaunchedEffect`.
* **Immutable State Models**: Mark all UI state models `@Immutable` or `@Stable`. Collections passed to composables must be wrapped in `ImmutableList` or read-only collections to avoid unnecessary recomposition loops.

---

## 3. Asynchronous Concurrency & Threading

### 3.1 Coroutine Dispatcher Governance
* Never hardcode `Dispatchers.IO` or `Dispatchers.Default` inside ViewModels or domain interactors. Always inject `CoroutineDispatcher` instances to enable deterministic unit test mocking via `StandardTestDispatcher`.
* **Sensor & BLE Isolation**: Real-time sensor processing pipelines (1,000 Hz packets) must process on dedicated background threads and transfer buffered aggregates to `StateFlow` on `Dispatchers.Default`.

---

## 4. Room Database & Persistence Standards

### 4.1 Schema Migration Integrity
* `fallbackToDestructiveMigration()` is strictly prohibited in production builds.
* Every database schema alteration must include a formal `Migration(N, N+1)` and a companion `MigrationTest` verifying data preservation for existing athlete records.

---

## 5. Multi-Locale Parity (9 Languages)
* All user-facing strings must be localized across all 9 supported locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`).
* Format specifiers (`%1$s`, `%2$d`, `%.1f`) must have 100% position and type parity across all translation resource files.
