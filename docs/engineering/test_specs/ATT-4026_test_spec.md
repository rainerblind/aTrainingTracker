# Stage 2: Requirement & Test Specification - ATT-4026: Move hardcoded user-visible strings, units and content descriptions to resources

**Ticket**: [ATT-4026](https://rainerblind.atlassian.net/browse/ATT-4026)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4026`  
**Requirement Mapping**: `REQ-UI-429` (*Global UI Localization Parity, Unit Adaptability & Accessibility*)  
**Test Spec ID**: `TST-UI-394`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-UI-429`)

### 1.1 Problem Statement & Rationale
UI components contain hardcoded German strings, unlocalized English `contentDescription`s, missing TalkBack descriptions on interactive `IconButton`s, and hardcoded metric assumptions ignoring `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.

### 1.2 Functional & Architectural Requirements
1. **Resource Extraction & 9-Locale Parity (`REQ-UI-429.1`)**: System SHALL extract all hardcoded literals (`ImportBackupTabsScreen`, `MainActivityWithNavigation`, `ZoneCalculatorDialog`, `LapEditBottomSheet`, `PeriodSummaryCard`, marker overlays) to `strings.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with positional specifiers (`%1$s`).
2. **Compose Screen Unit Adaptability (`REQ-UI-429.2`)**: `CropWorkoutScreen` SHALL format distance and speed via `DistanceFormatter` and `SpeedFormatter` matching user unit preference. `WorkoutFilterBottomSheet` SHALL dynamically adapt distance (mi) and elevation (ft) presets and parse values correctly for imperial users.
3. **Accessibility & TalkBack Semantics (`REQ-UI-429.3`)**: Interactive `IconButton`s with `contentDescription = null` in cluster screens, `LapEditBottomSheet`, and `ImportBackupTabsScreen` SHALL provide localized descriptions. Canvas charts SHALL expose semantic TalkBack descriptions.
4. **Preservation of Invariants (`REQ-UI-429.4`)**: Zero changes to SQLite schema or database SI unit storage; 100% translation parity; zero test regressions.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Unit & Language Adaptability)**:
  * *Given* app language French and unit preference Imperial,
  * *When* `CropWorkoutScreen` or `WorkoutFilterBottomSheet` is displayed,
  * *Then* labels appear in French, distance values display in miles, and filter conversion parses miles to meters accurately.
* **AC-2 (Accessibility Coverage)**:
  * *Given* TalkBack screen reader active,
  * *When* navigating icon buttons and visual charts,
  * *Then* meaningful localized actions and semantic descriptions are announced without unlabeled icons.
* **AC-3 (Translation Parity)**:
  * *Given* the 9 supported locales,
  * *When* `TranslationParityTest` runs,
  * *Then* 100% parity is verified with zero missing keys or format specifier mismatches.

### 1.4 System Invariants
* Underlying database telemetry and calculation algorithms remain strictly in standard SI units (meters, seconds, mps).
* Conversions occur strictly at the UI display and input boundary.

---

## 2. Test Specification (`TST-UI-394`)

* **`TST-UI-394.1` (9-Language Parity Audit)**: `TranslationParityTest.kt` asserting 100% string key existence and positional specifier consistency across `values/` and `values-{de,es,fr,it,ja,nl,pl,pt}/`.
* **`TST-UI-394.2` (Crop Screen Unit Adaptability)**: `CropWorkoutScreenTest.kt` asserting distance ("mi") and speed ("mph") formatting when `MyUnits.IMPERIAL` is active versus metric ("km", "km/h").
* **`TST-UI-394.3` (Filter BottomSheet Imperial Conversion)**: `WorkoutFilterBottomSheetTest.kt` asserting imperial presets (miles/feet) and correct meter threshold parsing (`miles * 1609.344`).
* **`TST-UI-394.4` (TalkBack Accessibility Semantics)**: `UiAccessibilityContractTest.kt` verifying non-null localized `contentDescription` on interactive icon buttons and chart semantic modifiers.
* **`TST-UI-394.5` (Clean-Room Full Suite Regression)**: `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Verification Method | Requirement Clause | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-394.1` | Localization | `TranslationParityTest.kt` | `REQ-UI-429.1`, `REQ-LOC-001`, `REQ-UI-122` | Specified |
| `TST-UI-394.2` | Unit / UI | `CropWorkoutScreenTest.kt` | `REQ-UI-429.2` | Specified |
| `TST-UI-394.3` | Unit / UI | `WorkoutFilterBottomSheetTest.kt` | `REQ-UI-429.2`, `REQ-UI-157` | Specified |
| `TST-UI-394.4` | Accessibility | `UiAccessibilityContractTest.kt` | `REQ-UI-429.3` | Specified |
| `TST-UI-394.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-UI-429.4`, `REQ-PRO-001` | Specified |
