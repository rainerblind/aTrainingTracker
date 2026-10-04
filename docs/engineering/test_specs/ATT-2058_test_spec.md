# Stage 2: Requirement & Test Specification - ATT-2058: Visual Evaluation & Prototyping: Rounded Corners & Tile Spacing Variants for SensorFieldView (Rework Cycle 2: Expert Settings Integration)

**Ticket**: [ATT-2058](https://rainerblind.atlassian.net/browse/ATT-2058)  
**Sub-task**: [ATT-2402](https://rainerblind.atlassian.net/browse/ATT-2402) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-UI-258` (*Cockpit Sensor Field Visual Styling Architecture, Expert Settings Selection & Reactive Grid Layout*)  
**Test Spec ID**: `TST-UI-217`  
**Branch**: `feature/ATT-2058`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-UI-258`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-UI-258` |
| **Title** | **Cockpit Sensor Field Visual Styling Architecture, Expert Settings Selection & Reactive Grid Layout.** |
| **Category** | User Interface / Material 3 / Layout & Preferences |
| **Scope** | `SensorFieldStyle.kt`, `SensorFieldView.kt`, `SensorGridScreen.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt` |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Four Standardized Visual Variants & Naming Overhaul**:
   - The system SHALL encapsulate 4 distinct design specifications in `SensorFieldVariant`:
     - **`CLASSIC_SEAMLESS`** (`variant_0`): `RectangleShape`, `gridSpacing = 0.dp`, `elevation = 0.dp`, `1.dp outlineVariant` border. Localized user-facing title: *"Klassisch nahtlos"* / *"Classic Seamless Grid"*.
     - **`OUTLINED_TILES`** (`variant_1`): `RoundedCornerShape(6.dp)`, `gridSpacing = 4.dp`, `elevation = 0.dp`, `1.dp outlineVariant` border. Localized user-facing title: *"Moderne Sportkacheln"* / *"Modern Outlined Tiles"*.
     - **`ELEVATED_CARDS`** (`variant_2`): `RoundedCornerShape(8.dp)`, `gridSpacing = 6.dp`, `elevation = 2.dp`, borderless. Localized user-facing title: *"Erhabene Sportkarten"* / *"Elevated Sports Cards"*.
     - **`SOFT_CAPSULES`** (`variant_3`): `RoundedCornerShape(12.dp)`, `gridSpacing = 8.dp`, `elevation = 1.dp`, `1.dp outlineVariant` border, cleanly clipped 6dp vertical zone indicator bar. Localized user-facing title: *"Soft-Akzent Kapseln"* / *"Soft Accent Capsules"*.
2. **DataStore Preference Persistence**:
   - `TuningPreferencesDataStore` SHALL persist the selected variant using key `KEY_SENSOR_FIELD_VARIANT` (`tuning_sensor_field_variant`).
   - Default value SHALL be `SensorFieldVariant.CLASSIC_SEAMLESS`.
   - `TuningPreferencesDataStore.resetToDefaults()` SHALL reset `sensorFieldVariant` to `CLASSIC_SEAMLESS`.
3. **Expert Settings Integration (`AdvancedTuningDialog.kt`)**:
   - In `AdvancedTuningDialog`, Section 1 ("Cockpit & Typografie") SHALL provide a user-facing dropdown/selector (`CockpitTypographySection.kt`) allowing the athlete to select from all 4 variants.
   - `TuningSubtitleFormatter.formatCockpitSubtitle` SHALL append the active tile variant to the section header subtitle (e.g. *"System-Schriftart, Semi-Bold · Klassisch nahtlos"*).
4. **Reactive Live Cockpit Updating (`SensorGridScreen.kt`)**:
   - `SensorGridScreen` SHALL reactively observe `tuningConfig.sensorFieldVariant`.
   - When invoked with default styling parameters (`gridSpacing == 0.dp && fieldShape == RectangleShape && fieldElevation == CardDefaults.cardElevation(0.dp)`), `SensorGridScreen` SHALL dynamically resolve `shape`, `gridSpacing`, and `elevation` from `SensorFieldStyle.forVariant(tuningConfig.sensorFieldVariant)`.
   - The live tracking cockpit across all tracking tabs SHALL immediately adopt the newly selected styling upon dismissal or update in `AdvancedTuningDialog` without requiring an app restart.
5. **Preservation of Invariants**:
   - Runtime defaults SHALL remain strictly on `CLASSIC_SEAMLESS` (0dp spacing, RectangleShape, 0dp elevation), ensuring 100% backward compatibility for all athletes.
   - AMOLED pitch-black contrast (#000000), touch targets (>= 48dp), click-to-edit, long-press pick-and-place reordering (`REQ-UI-200`), LiveSegment priority, and SQLite database schemas MUST NOT be altered.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

```markdown
### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-UI-258` (*Cockpit Sensor Field Visual Styling Architecture & Grid Spacing Prototyping Variants*), targeting `SensorFieldStyle.kt`, `SensorGridScreen.kt`, `TuningPreferencesDataStore.kt`, and `AdvancedTuningDialog.kt`.
2. **Historical Origin & Commit Trace**: Introduced in `ATT-2058` (commit `ecf08c35`) during Sprint 2026-40.14 as a prototyping preview suite.
3. **Root Reason for Existing Formulation**: Originally conceived strictly for static Compose previews during sprint ideation to avoid prematurely polluting user preferences before human review.
4. **Preservation of Core Invariants**: 
   - Runtime default remains strictly on `CLASSIC_SEAMLESS` (0dp spacing, RectangleShape, 0dp elevation), ensuring 100% backward compatibility for all athletes who prefer the legacy layout.
   - AMOLED pitch-black contrast (#000000) and pick-and-place reordering (`REQ-UI-200`) remain completely intact across all 4 variants.
   - LiveSegment display priority and SQLite database immutability are fully preserved.
```

---

## 3. Test Specification (`TST-UI-217`)

| Field | Specification |
| :--- | :--- |
| **Test Case ID** | `TST-UI-217` |
| **Test Type** | Unit, Contract, Persistence, and Localization Verification |
| **Target Requirement** | `REQ-UI-258` |
| **Target Files** | `SensorFieldStyleContractTest.kt`, `TuningPreferencesDataStoreTest.kt`, `TuningSubtitleFormatterTest.kt`, `SensorGridScreen.kt` |
| **Status** | Specified |

### Test Case Breakdown

#### TST-UI-217.1: Production Default Baseline Invariance Test
- **Target**: `SensorFieldStyle` and `TuningPreferencesDefaults`.
- **Given**: Application startup or factory reset.
- **Then**:
  - `TuningPreferencesDefaults.SENSOR_FIELD_VARIANT` resolves to `SensorFieldVariant.CLASSIC_SEAMLESS`.
  - `SensorFieldStyle.forVariant(CLASSIC_SEAMLESS)` resolves `shape = RectangleShape`, `gridSpacing = 0.dp`, `defaultElevation = 0.dp`.

#### TST-UI-217.2: Variant Specification & Styling Mapping Test
- **Target**: `SensorFieldStyle.forVariant`.
- **When**: Inspecting each variant:
  - `CLASSIC_SEAMLESS`: `shape = RectangleShape`, `gridSpacing = 0.dp`, `defaultElevation = 0.dp`.
  - `OUTLINED_TILES`: `shape = RoundedCornerShape(6.dp)`, `gridSpacing = 4.dp`, `defaultElevation = 0.dp`.
  - `ELEVATED_CARDS`: `shape = RoundedCornerShape(8.dp)`, `gridSpacing = 6.dp`, `defaultElevation = 2.dp`.
  - `SOFT_CAPSULES`: `shape = RoundedCornerShape(12.dp)`, `gridSpacing = 8.dp`, `defaultElevation = 1.dp`.
- **Then**: All 4 specifications match expected geometry.

#### TST-UI-217.3: DataStore Persistence & Atomic Reset Test
- **Target**: `TuningPreferencesDataStore`.
- **When**: Writing `SensorFieldVariant.ELEVATED_CARDS` to DataStore.
- **Then**: Flow emits `TuningConfig` with `sensorFieldVariant = ELEVATED_CARDS`.
- **When**: `resetToDefaults()` is executed.
- **Then**: `sensorFieldVariant` is reset to `CLASSIC_SEAMLESS`.

#### TST-UI-217.4: Subtitle Formatter Integration Test
- **Target**: `TuningSubtitleFormatter.formatCockpitSubtitle`.
- **When**: Formatting with family, weight, and variant.
- **Then**: Result string includes the localized variant name (e.g. *"System-Schriftart, Semi-Bold · Klassisch nahtlos"*).

#### TST-UI-217.5: Zone Indicator Bar Clipping & Pick-and-Place Interaction Test
- **Target**: `SensorFieldView` with rounded shapes.
- **When**: Active zone bar rendered with 6dp width.
- **Then**: Card container clips child composables without overflowing corners.
- **When**: Field is in configuration mode and `isSelectedForMove == true`.
- **Then**: Primary selection border (`2.dp primary`) is applied regardless of active variant shape.

#### TST-UI-217.6: 9-Language Localization Parity Audit
- **Target**: `values*/strings.xml` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
- **Then**:
  - `tuning_sensor_field_variant_title` exists in all 9 files.
  - `sensor_field_variant_v0` exists in all 9 files.
  - `sensor_field_variant_v1` exists in all 9 files.
  - `sensor_field_variant_v2` exists in all 9 files.
  - `sensor_field_variant_v3` exists in all 9 files.

#### TST-UI-217.7: Clean-Room Full Suite Regression
- **Command**: `./gradlew testDebugUnitTest`
- **Criteria**: 100% pass rate across the full application test suite.

---

## 4. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-258.1` (4 Variants & Naming) | `TST-UI-217.2`, `TST-UI-217.6` | `SensorFieldStyle.kt`, `strings.xml` (all 9) | `Specified` |
| `REQ-UI-258.2` (DataStore Persistence) | `TST-UI-217.1`, `TST-UI-217.3` | `TuningPreferencesDataStore.kt` | `Specified` |
| `REQ-UI-258.3` (Settings UI Integration) | `TST-UI-217.4` | `AdvancedTuningDialog.kt`, `CockpitTypographySection.kt` | `Specified` |
| `REQ-UI-258.4` (Reactive Screen Resolution) | `TST-UI-217.1`, `TST-UI-217.5` | `SensorGridScreen.kt`, `SensorFieldView.kt` | `Specified` |
| `REQ-UI-258.5` (Preservation of Invariants) | `TST-UI-217.1`, `TST-UI-217.7` | Entire test suite | `Specified` |
