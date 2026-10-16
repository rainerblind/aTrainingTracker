# Stage 2: Requirement & Test Specification - ATT-3574: Centralize All 67 Hardcoded Compose Color Literals Across 20 Files into Color.kt and TTColor

**Ticket**: [ATT-3574](https://rainerblind.atlassian.net/browse/ATT-3574)  
**Sub-task**: [ATT-3741](https://rainerblind.atlassian.net/browse/ATT-3741) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-381` (*Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture*)  
**Test Spec ID**: `TST-UI-344`  
**Branch**: `feature/ATT-3574`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (REQ-UI-381)

### 1.1 Problem Statement & Rationale
Under **Rule 36 (Single Source of Truth & DRY)** and [docs/design_guidelines.md], all Jetpack Compose colors must be defined exclusively in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` (and exposed via the `TTColor` design system object or Compose theme palettes). Prior to ATT-3574, 67 inline `Color(0x...)` instantiations were scattered across 23 production files, duplicating climb grade calculations, re-instantiating existing tokens (`Gold`, `ConnectionStatusGreen`), and repeatedly hardcoding dark map canvas styling.

### 1.2 Functional & Architectural Requirements
The system SHALL centralize all Jetpack Compose color constants in `Color.kt` and expose semantic domain colors via `TTColor`:
1. *Centralized Token Definitions (`REQ-UI-381.1`)*:
   - AMOLED Container Tokens in `Color.kt`: `AmoledPrimaryContainer` (`#000000`), `AmoledOnPrimaryContainer` (`#FFFFFF`), `AmoledSurfaceBright` (`#1A1A1A`), `AmoledSurfaceContainerHigh` (`#121212`), `AmoledSurfaceContainerHighest` (`#000000`).
   - Climb Categories in `TTColor`: `ClimbCategoryHc` (`#B71C1C`), `ClimbCategory1` (`#D32F2F`), `ClimbCategory2` (`#EF6C00`), `ClimbCategory3` (`#FBC02D`), `ClimbCategory4` (`#388E3C`), `ClimbCategoryUncategorized` (`#757575`).
   - Climb Gradient Bands in `TTColor`: `ClimbGradeFlat` (`#4CAF50`), `ClimbGradeMild` (`#8BC34A`), `ClimbGradeMedium` (`#FFEB3B`), `ClimbGradeSteep` (`#FF9800`), `ClimbGradeSevere` (`#F44336`), `ClimbGradeExtreme` (`#9C27B0`).
   - Climb Gradient Resolver in `TTColor`: `getClimbGradeColor(gradePercent: Double): Color` evaluating the 6 discrete grade thresholds.
   - Map & Track Tokens in `TTColor`: `DarkMapCanvas` (`#121212`), `DarkMapTrackCyan` (`#00E5FF`).
   - Geofence & Location Badges in `TTColor`: `GeofenceFill` (`#332196F3`), `GeofenceStroke` (`#882196F3`), `GeofencePin` (`#2196F3`), `LocationBadgeHomeContainer` (`#E8F5E9`), `LocationBadgeHomeContent` (`#2E7D32`), `LocationBadgeWorkContainer` (`#FFF8E1`), `LocationBadgeWorkContent` (`#F57F17`), `LocationBadgeOtherContainer` (`#FFE0B2`), `LocationBadgeOtherContent` (`#E65100`).
   - Cloud & Connectivity Tokens in `TTColor`: `DropboxBlue` (`#0061FE`), `ConnectionConnected` (`#2E7D32`).
   - Cockpit & Sensor Gauges in `TTColor`: `PauseAmber` (`#F59E0B`), `SensorActiveBorder` (`#2196F3`), `SensorBorderDark` (`#383838`), `SensorBorderLight` (`#D6D6D6`), `BalanceGaugeTrack` (`#1B1B1F`), `ZoneGaugeTrack` (`#1B1B1F`), `WarningBorderLight` (`#E0E0E0`).
2. *Purge of Inline Color Literals (`REQ-UI-381.2`)*:
   - All 67 occurrences of `Color(0x` across all 23 production files outside `Color.kt` SHALL be migrated to reference their centralized `Color.kt` / `TTColor` tokens.
3. *Zero Hex Literals Enforcement (`REQ-UI-381.3`)*:
   - A static source audit test SHALL scan all `.kt` files in `app/src/main/java/` and assert **strictly 0 occurrences** of `Color(0x` outside `Color.kt`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Zero Hardcoded Hex Colors Outside Color.kt**:
  * *Given* all production Kotlin source files in `app/src/main/java/`
  * *When* running `ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt`
  * *Then* exactly 0 occurrences of `Color(0x` exist outside `Color.kt`.
* **Criterion 2: Single Source of Truth for Climb Grade Colors**:
  * *Given* `LiveClimbSheet.kt` and `ClimbDetailSheet.kt`
  * *When* rendering climb elevation profile segments
  * *Then* both delegate to `TTColor.getClimbGradeColor(gradePercent)`.
* **Criterion 3: Climb Grade Color Threshold Consistency & Boundary Invariants**:
  * *Given* exact boundary and interior grade percentages across `[-5.0, 0.0, 2.99, 3.0, 5.99, 6.0, 8.99, 9.0, 11.99, 12.0, 14.99, 15.0, 25.0]`
  * *When* calling `TTColor.getClimbGradeColor(gradePercent)`
  * *Then* the resolved colors match strictly `ClimbGradeFlat` (< 3.0%), `ClimbGradeMild` (3.0%–5.99%), `ClimbGradeMedium` (6.0%–8.99%), `ClimbGradeSteep` (9.0%–11.99%), `ClimbGradeSevere` (12.0%–14.99%), and `ClimbGradeExtreme` (>= 15.0%).

### 1.4 System Invariants
1. **Visual Fidelity Invariant**: 100% ARGB pixel-level visual fidelity: No colors, contrasts, or opacities are altered. All 67 original ARGB values are numerically preserved in `Color.kt` / `TTColor`.
2. **Binary Stability**: No public method signature breakage in domain logic or data models.
3. **Parent Ticket Human Gate**: Parent ticket Human Decision Gate remains strictly enforced.

### 1.5 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-381` (*Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture*). Amends architectural design system conventions under Rule 36. Targets `ui/theme/Color.kt`, `TTColor`, and all 23 consuming Jetpack Compose UI files.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (`ATT-1281`: Persistent Climbs Database & Live ClimbPro Cockpit Sheet), Sprint 2026-41.5 (`ATT-1399`: Live Cockpit Location & Barometer Calibration Feedback), and Sprint 2026-41.6 (`ATT-2946`: Speed-Dependent Map Zoom Calibration).
3. *Root Reason for Existing Formulation*: The "fence" (hardcoded inline `Color(0x...)` literals) was erected for high-velocity exploratory prototyping during early feature spikes without the ceremony of updating `Color.kt`. However, as the design system matures toward V4.9.40, this technical debt directly violates the Single Source of Truth & DRY principle (Rule 36), preventing centralized AMOLED/contrast theming.
4. *Preservation of Core Invariants*: All 67 color values are preserved with 100% ARGB numerical identity ($A, R, G, B$ values identical to the original hex literals), preserving pixel-perfect visual fidelity, dark mode contrast, zero UI thread crashes, and 100% full clean-room unit test suite pass rate.

---

## 2. Test Specification (TST-UI-344)

### Test Case 1: Zero Hex Literals Outside Color.kt Audit (`TST-UI-344.1`)
* **Scope**: Automated Architecture / Static Source Scan Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ThemeColorCentralizationAuditTest.kt`
* **Preconditions**: Project production sources under `app/src/main/java/` are compiled and present.
* **Action**: Scan all `.kt` files in `app/src/main/java/` line-by-line; check for `Color(0x` in any file other than `Color.kt`.
* **Expected Result**: Assert that the list of offending occurrences is empty (`assertThat(violations).isEmpty()`).

### Test Case 2: Climb Grade Color Mapping & Explicit Boundary Verification (`TST-UI-344.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ThemeColorCentralizationAuditTest.kt`
* **Preconditions**: `TTColor.getClimbGradeColor` is available.
* **Action**: Evaluate exact boundary and interior grade values:
  - `< 3.0`: -5.0, 0.0, 2.99 -> `TTColor.ClimbGradeFlat`
  - `[3.0, 6.0)`: 3.0 (exact lower boundary), 4.5, 5.99 (upper interior) -> `TTColor.ClimbGradeMild`
  - `[6.0, 9.0)`: 6.0 (exact boundary), 7.5, 8.99 -> `TTColor.ClimbGradeMedium`
  - `[9.0, 12.0)`: 9.0 (exact boundary), 10.5, 11.99 -> `TTColor.ClimbGradeSteep`
  - `[12.0, 15.0)`: 12.0 (exact boundary), 13.5, 14.99 -> `TTColor.ClimbGradeSevere`
  - `>= 15.0`: 15.0 (exact boundary), 22.0 -> `TTColor.ClimbGradeExtreme`
* **Expected Result**: Assert exact Color equality for all boundary and interior test values, confirming correct interval containment.

### Test Case 3: Climb Sheets Gradient Resolution Delegation Audit (`TST-UI-344.3`)
* **Scope**: Static Code Inspection Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ThemeColorCentralizationAuditTest.kt`
* **Preconditions**: `LiveClimbSheet.kt` and `ClimbDetailSheet.kt` are present.
* **Action**: Read source lines of both files and assert that `TTColor.getClimbGradeColor` is called, and neither file defines an inline `when` block mapping slope grade thresholds.
* **Expected Result**: Both files successfully delegate to the single shared helper.

### Test Case 4: Localization Parity & Resource Safety (`TST-UI-344.4`)
* **Scope**: Localization Parity Test
* **Target File**: `TranslationParityTest`
* **Goal**: Confirm zero missing translation keys across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% parity across all 9 locales.

### Test Case 5: Clean-Room Full Regression Suite & Visual Fidelity Verification (`TST-UI-344.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite. Migrated UI components (`LiveClimbSheet`, `ClimbDetailSheet`, `KnownLocationsScreen`, `ControlTrackingButton`) are verified against Compose previews to guarantee 100% ARGB pixel-level visual fidelity.
* **Expected Result**: All tests pass cleanly with 0 failures; zero visual regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-344.1` | Static Architecture Scan | `ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt` | `REQ-UI-381.2`, `REQ-UI-381.3` | Specified |
| `TST-UI-344.2` | Unit Test | `TTColor.getClimbGradeColor` | `REQ-UI-381.1` | Specified |
| `TST-UI-344.3` | Static Architecture Scan | `ThemeColorCentralizationAuditTest.verifyClimbSheetsDelegateToSharedHelper` | `REQ-UI-381.1`, `REQ-UI-381.2` | Specified |
| `TST-UI-344.4` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-UI-344.5` | Full Suite Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-381` | Specified |
