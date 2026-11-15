# Stage 3: Implementation Plan - ATT-7759: Centralized Telemetry Color Palette & Global Graph Preferences

**Ticket**: [ATT-7759](https://rainerblind.atlassian.net/browse/ATT-7759)  
**Sub-task**: *(Pending creation on submit)* (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.7`  
**Requirement Mapping**: `REQ-UI-530` (`REQ-UI-530.1`..`REQ-UI-530.4`)  
**Test Mapping**: `TST-UI-530` (`TST-UI-530.1`..`TST-UI-530.5`)  
**Branch**: `feature/ATT-7759`  
**Author**: AI Agent 1 (Pro Engineer)  
**Date**: 2026-11-15  

---

## 1. Problem Description & Background

`TelemetryMetricGraph.kt` hardcodes raw `TTColor.Zone1..5` and `TTColor.WPrimeBalGreen` accents lacking Light/Dark/AMOLED contrast tuning, and no global per-metric graph color preference store exists. `ATT-7759` centralizes an 8-color Light/Dark-aware `TelemetryGraphColor` palette (+ `THEME_DEFAULT`) in `Color.kt`, adds `TelemetryGraphColorPreferences` (`SharedPreferences`), and wires `TelemetryMetricGraph.kt` to resolve `accentColor` dynamically.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-530` (*Centralized Light/Dark Telemetry Color Palette, Metric Defaults & Global Graph Color Persistence*)
* **Test Mapping**: `TST-UI-530` (`TST-UI-530.1`..`TST-UI-530.5`)

---

## 3. System Invariants & Preserved Behavior

1. **Token & Chart Invariants**: `TTColor.Zone1..7` and `TTColor.WPrimeBalGreen` unchanged; `TelemetryMetricGraph` paddings (`50.dp`/`25.dp`/`24.dp`), zone bands, scrubbing, and histograms unchanged.
2. **Color Centralization**: Zero `Color(0x...)` literals outside `Color.kt` (`ThemeColorCentralizationAuditTest`).
3. **Safe Enum Deserialization**: `SharedPreferences` reads use `safeEnumValueOf` (`REQ-ARC-001` / `SafeEnumDeserializationAuditTest`).
4. **Parent Human Gate Invariance**: Parent ticket terminates at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Centralized Palette (`ui/theme/Color.kt`)
* Define `TelemetryGraphColor` enum (`THEME_DEFAULT`, `CRIMSON`, `VIOLET`, `EMERALD`, `AMBER`, `TEAL`, `MAGENTA`, `CYAN`, `CORAL`) with `lightColor`, `darkColor`, and `resolveColor(colorScheme: ColorScheme, isDarkTheme: Boolean = TTColor.relativeLuminance(colorScheme.surface) < 0.5): Color` (`THEME_DEFAULT` $\rightarrow$ `colorScheme.primary`). All 8 colors $\ge 3.0:1$ contrast on `#FFFFFF`, `#16161A`, `#000000`.

### Component 2: Global Preference Store (`settings/TelemetryGraphColorPreferences.kt`)
* `TelemetryGraphColorPreferences` object backed by `SharedPreferences` (defaulting to `PreferenceManager.getDefaultSharedPreferences(context)` with `runCatching` fallback for previews/tests):
  * Family key mapping for `TelemetryMetricType` and `SensorType` (`HR`/`HEART_RATE` $\rightarrow$ `CRIMSON`, `POWER*` $\rightarrow$ `VIOLET`, `W_PRIME_BAL` $\rightarrow$ `EMERALD`, `HRV_DFA_A1` $\rightarrow$ `AMBER`, `HRV_RMSSD` $\rightarrow$ `TEAL`, `SPEED`/`PACE`/cockpit sensors $\rightarrow$ `THEME_DEFAULT`).
  * Methods: `getDefaultColor`, `getGraphColor`, `setGraphColor`, `resetGraphColor`, `resetAllGraphColors`, `resolveAccentColor`, `preferenceKey`.

### Component 3: Graph Wiring (`ui/map/TelemetryMetricGraph.kt`)
* Replace hardcoded `TTColor.Zone*`/`WPrimeBalGreen` `when` block in `TelemetryMetricGraph` with `TelemetryGraphColorPreferences.resolveAccentColor(context, metricType, colorScheme)` plus `DisposableEffect` observing `OnSharedPreferenceChangeListener` so live preference updates recompose immediately.

### UI Consistency (Rule 23)
* **Reference screen / component**: `TelemetryMetricGraph.kt` (`WorkoutDetail`, `WorkoutSummary`) and `Color.kt` (`TTColor`).
* **Reused components**: `TelemetryMetricGraph`, `TTColor.relativeLuminance`, `MaterialTheme.colorScheme.primary`.
* **Theme tokens**: Light `#FFFFFF`, Dark `#16161A`, AMOLED `#000000` surface-luminance adaptive `TelemetryGraphColor` tokens in `Color.kt`.
* **New one-off styles & justification**: None; all tokens centralized in `Color.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

1. **Step 1 (`REQ-UI-530.1`)**: Add `TelemetryGraphColor` enum and `PALETTE_COLORS` list to `app/src/main/java/.../ui/theme/Color.kt`.
2. **Step 2 (`REQ-UI-530.2`, `REQ-UI-530.3`)**: Create `app/src/main/java/.../settings/TelemetryGraphColorPreferences.kt` with `TelemetryMetricType` and `SensorType` family mappings, `SharedPreferences` CRUD, reset APIs, and `safeEnumValueOf`.
3. **Step 3 (`REQ-UI-530.4`)**: Wire `TelemetryMetricGraph.kt` `accentColor` to `TelemetryGraphColorPreferences` + `OnSharedPreferenceChangeListener` + surface luminance detection.
4. **Step 4 (`TST-UI-530.1`..`5`)**: Add `TelemetryGraphColorContrastTest.kt`, `TelemetryGraphColorPreferencesTest.kt`, and `TelemetryMetricGraphColorTest.kt` (including `@GraphicsMode(NATIVE)` Compose Light/Dark/AMOLED rendering & screenshot capture).
5. **Step 5 (Targeted Tier-1 Verification)**: Run targeted `--tests` (`TelemetryGraphColor*`, `TelemetryMetricGraph*`, `ThemeColorCentralizationAuditTest`, `SafeEnumDeserializationAuditTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`, `TranslationParityTest`) + `:app:lintDebug` + `:app:compileDebugAndroidTestKotlin` + scoped `:app:detekt`.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted Tier-1 Gradle tests, Robolectric Compose visual checks, Lint/Detekt, and `verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-7759`; cleanly revertible without affecting `sprint/2026-46.7`.
