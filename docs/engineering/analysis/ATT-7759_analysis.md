# Stage 1 Analysis: ATT-7759 - Centralized Light/Dark telemetry color palette and global graph color preferences

**Ticket**: [ATT-7759](https://rainerblind.atlassian.net/browse/ATT-7759)  
**Sub-task**: *(Pending creation on submit)* (`[Analysis]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.7`  
**Branch**: `feature/ATT-7759`  
**Author**: AI Agent 1 (Pro Engineer)  
**Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation

`TelemetryMetricGraph.kt` (lines 194–203) currently hardcodes raw `TTColor.Zone1..5` tokens (`Zone4` Red for HR, `Zone5` Violet for Power, `Zone3` Orange for HRV DFA α1, `Zone1` Chartreuse for HRV rMSSD) and `TTColor.WPrimeBalGreen` for W'bal, while `SPEED` and `PACE` use `colorScheme.primary`. Raw `TTColor.Zone*` colors lack Light/Dark adaptation (e.g., `Zone1` `#7FFF00` fails contrast on `#FFFFFF`; `Zone5` `#9400D3` is dim on Dark `#16161A` and AMOLED `#000000`), and no global per-metric graph color preference store exists.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * Verified `TelemetryMetricGraph.kt:194-203` on `sprint/2026-46.7` (`533d88b1`): `accentColor` drives the curve stroke, vertical gradient fill (`alpha = 0.20f..0.02f`), single-point dot, scrubbing halo/dot, and `nonZonedHistogram` (`TelemetryHistogramCalculator.computeLinearMarginalHistogram`).
  * Verified `Theme.kt:38-150`: Light surface is `#FFFFFF`, Dark surface is `#16161A` (`DarkBackground = #121214`), and AMOLED surface is `#000000`. Because `CockpitThemeMode.ALWAYS_DARK` activates dark/AMOLED `ColorScheme` even when `isSystemInDarkTheme()` is `false`, theme resolution must inspect `MaterialTheme.colorScheme.surface` luminance (`TTColor.relativeLuminance(colorScheme.surface) < 0.5`) rather than relying solely on `isSystemInDarkTheme()`.
  * Verified `ThemeColorCentralizationAuditTest.kt`: zero `Color(0x...)` literals are allowed outside `Color.kt`.
  * Verified `SafeEnumDeserializationAuditTest.kt`: `SharedPreferences` reads must use `safeEnumValueOf` / safe companion lookup, never raw `Enum.valueOf(...)`.
  * Verified `SourceTextReadingAuditContractTest.kt`: new tests must execute genuine runtime/Robolectric/Compose tests without reading `app/src/main` source text.
  * **Blind Spot Identified**: `WPrimeBalGreen` (`#10B981`) has contrast `2.53:1` on `#FFFFFF` (< `3.0:1` WCAG AA non-text minimum). Separating Light (`#059669`, `3.77:1`) and Dark (`#10B981`, `> 7.5:1`) variants inside `TelemetryGraphColor.EMERALD` resolves this cleanly.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add `TelemetryGraphColor` (8 Light/Dark-aware colors + `THEME_DEFAULT`) in `ui/theme/Color.kt` meeting WCAG 2.1 non-text contrast ($\ge 3.0:1$) on `#FFFFFF`, `#16161A`, and `#000000`.
  * Add a global `SharedPreferences`-backed preference store (`TelemetryGraphColorPreferences`) keyed by metric / `SensorType` family with metric defaults (HR=Crimson, Power=Violet, W'bal=Emerald, DFA α1=Amber, rMSSD=Teal, Speed/Pace & cockpit sensors=`THEME_DEFAULT`) and reset support.
  * Update `TelemetryMetricGraph.kt` to resolve `accentColor` via `TelemetryGraphColorPreferences` + theme luminance.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Cockpit sparkline (`SensorFieldView.kt`) wiring and Settings/Picker UI dialogs (deferred to subsequent `ATT-5908` tickets).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-206` (*Continuous Telemetry Metric Graphs*), `docs/requirements/req_ui_design_system.md` (lines 3994–4019).
* **Historical Origin & Commit Trace**: Introduced in `ATT-1740`, extended in `ATT-3544` (`3db57718`) and `ATT-2145` (`dcb8f15f`).
* **Root Reason for Existing Formulation**: Hardcoded `TTColor.Zone4`/`Zone5` was chosen as an ad-hoc semantic shortcut before a dedicated Light/Dark graph palette existed.
* **Preservation of Core Invariants**: Padding (`50.dp`/`25.dp`/`24.dp`), Y/X axes, zone bands (`TelemetryZoneMath`), scrubbing, and headings remain completely untouched; `REQ-UI-530` is reserved as a net-new requirement refining accent color resolution.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-UI-530` (and `TST-UI-530`) in `docs/requirements/req_ui_design_system.md` / `docs/tests/tst_ui_design_system.md`, refining accent color resolution of `REQ-UI-206`.
* **Affected Architecture Elements**: `ui/theme/Color.kt`, new `settings/TelemetryGraphColorPreferences.kt`, and `ui/map/TelemetryMetricGraph.kt`. Zero DB schema, service, or `strings.xml` changes.
* **Verification Impact & Test Surface**: New `TelemetryGraphColorPreferencesTest.kt` + `TelemetryGraphColorContrastTest.kt` (or combined `TelemetryGraphColor*Test.kt`), existing `TelemetryMetricGraph*Test`, `ThemeColorCentralizationAuditTest`, `SafeEnumDeserializationAuditTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`, `TranslationParityTest`.
* **Risk Assessment & Invariants**: `LOW` risk. Preserves zone band colors (`TTColor.Zone1..7`), `TTColor.WPrimeBalGreen`, `TelemetryMetricGraph` geometry/gestures, and downgrade-safe `SharedPreferences` enum deserialization.

---

## 6. Architectural Strategy & High-Level Solution

1. Define `TelemetryGraphColor` in `ui/theme/Color.kt` (`THEME_DEFAULT`, `CRIMSON`, `VIOLET`, `EMERALD`, `AMBER`, `TEAL`, `MAGENTA`, `CYAN`, `CORAL`) with `lightColor`, `darkColor`, and `resolveColor(colorScheme, isDarkTheme)`.
2. Implement `TelemetryGraphColorPreferences` in `settings/` mapping both `TelemetryMetricType` and `SensorType` families to canonical keys, supporting `getGraphColor`, `setGraphColor`, `resetGraphColor`, `resetAllGraphColors`, and `resolveAccentColor(...)` using `safeEnumValueOf`.
3. Update `TelemetryMetricGraph.kt` to resolve `accentColor` reactively via `TelemetryGraphColorPreferences` + `OnSharedPreferenceChangeListener` + `TTColor.relativeLuminance(colorScheme.surface) < 0.5`.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**: `TTColor.Zone1..7` and `TTColor.WPrimeBalGreen` unaltered; zero `Color(0x...)` outside `Color.kt`; safe enum deserialization (`REQ-ARC-001`); zero DB/service mutations; Human Decision Gate enforced.
* **Risk Rating**: **LOW** — Pure UI theme token, stateless `SharedPreferences` helper, and single-site `accentColor` resolver replacement in `TelemetryMetricGraph.kt`.
