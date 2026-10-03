# Stage 2: Requirement & Test Specification - ATT-2058: Visual Evaluation & Prototyping: Rounded Corners & Tile Spacing Variants for SensorFieldView

**Ticket**: [ATT-2058](https://rainerblind.atlassian.net/browse/ATT-2058)  
**Sub-task**: [ATT-2227](https://rainerblind.atlassian.net/browse/ATT-2227) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2058`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (`REQ-UI-258`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-UI-258` |
| **Title** | **Cockpit Sensor Field Visual Styling Architecture & Grid Spacing Prototyping Variants.** |
| **Category** | User Interface / Material 3 / Layout & Prototyping |
| **Scope** | `SensorFieldView.kt`, `SensorGridScreen.kt` |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Parameterized Tile Styling in `SensorFieldView`**:
   - `SensorFieldView` SHALL support optional styling parameters (defaulting strictly to baseline production values):
     - `shape: Shape = RectangleShape`
     - `border: BorderStroke? = if (isSelectedForMove) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`
     - `elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)`
2. **Parameterized Grid Spacing in `SensorGridScreen`**:
   - `SensorGridScreen` (and `TrackingTabGridContent`) SHALL support configurable inter-tile spacing parameters (defaulting strictly to `0.dp`):
     - `gridSpacing: Dp = 0.dp`
     - When `gridSpacing > 0.dp`, outer `Column` SHALL apply `verticalArrangement = Arrangement.spacedBy(gridSpacing)` and inner `Row` SHALL apply `horizontalArrangement = Arrangement.spacedBy(gridSpacing)`.
3. **Four Standardized Visual Variants**:
   - The system SHALL encapsulate 4 distinct design specifications in `SensorFieldStyle`:
     - **Variant 0 (Baseline / Status Quo)**: `RectangleShape`, `gridSpacing = 0.dp`, `elevation = 0.dp`, `1.dp outlineVariant` border.
     - **Variant 1 (Modern Outlined Sport Tiles)**: `RoundedCornerShape(6.dp)`, `gridSpacing = 4.dp`, `elevation = 0.dp`, `1.dp outlineVariant` border.
     - **Variant 2 (Elevated Sports Cards)**: `RoundedCornerShape(8.dp)`, `gridSpacing = 6.dp`, `elevation = 2.dp`, borderless / subtle border.
     - **Variant 3 (Soft Accent Capsule)**: `RoundedCornerShape(12.dp)`, `gridSpacing = 8.dp`, `elevation = 1.dp`, `1.dp outlineVariant` border, cleanly clipped 6dp vertical zone indicator bar.
4. **High-Fidelity Preview Suite**:
   - The system SHALL provide comprehensive, multi-theme Jetpack Compose `@Preview` composables (`PreviewCockpitVariant0_Baseline`, `PreviewCockpitVariant1_OutlinedTiles`, `PreviewCockpitVariant2_ElevatedCards`, `PreviewCockpitVariant3_Capsules`, and side-by-side comparison previews) rendered across both AMOLED Dark Mode (`AmoledDarkColorScheme`) and Light Mode.
5. **Preservation of Production Invariants**:
   - Runtime defaults SHALL remain strictly on Variant 0; touch target sizes, click-to-edit, long-press pick-and-place reordering (`REQ-UI-200`), LiveSegment priority, and SQLite database schemas MUST NOT be altered.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid for Sunlight Legibility*) and `REQ-UI-200` (*Cockpit Sensor Grid Pick & Place*).
2. **Historical Origin & Commit Trace**:
   - `ATT-1264` (commit `6bfb0e35`) and `ATT-1629` (commit `3b692bc1`).
3. **Root Reason for Existing Formulation**:
   - Baseline `RectangleShape` with 0dp spacing was used because early rounded-corner experiments lacked synchronized grid spacing, causing awkward diamond-shaped intersection voids at tile corners.
4. **Preservation of Core Invariants**:
   - Default production values are strictly preserved (Variant 0); AMOLED high contrast and semi-bold typography (`REQ-UI-171`) remain intact; pick-and-place selection (`REQ-UI-200`) remains functional across all variants.

---

## 3. Test Specification (`TST-UI-217`)

| Field | Specification |
| :--- | :--- |
| **Test Case ID** | `TST-UI-217` |
| **Test Type** | Structural Unit Contract & Composable Preview Verification |
| **Target Requirement** | `REQ-UI-258` |
| **Target Files** | `SensorFieldStyleContractTest.kt`, `SensorFieldView.kt`, `SensorGridScreen.kt` |
| **Status** | Specified |

### Test Case Breakdown

#### TST-UI-217.1: Production Default Baseline Invariance Test
- **Target**: `SensorFieldView` and `SensorGridScreen`.
- **Given**: Default parameters invoked without explicit overrides.
- **When**: Inspecting resolved tile shape, border, elevation, and grid spacing.
- **Then**:
  - `tileShape` resolves to `RectangleShape`.
  - `gridSpacing` resolves to `0.dp`.
  - `cardElevation` resolves to `0.dp`.
  - Default border resolves to `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`.

#### TST-UI-217.2: Prototyping Variant Specification Resolution Test
- **Target**: `SensorFieldStyle` object / helper.
- **When**: Inspecting properties of each defined variant:
  - `Variant 0`: `shape = RectangleShape`, `spacing = 0.dp`, `elevation = 0.dp`.
  - `Variant 1`: `shape = RoundedCornerShape(6.dp)`, `spacing = 4.dp`, `elevation = 0.dp`.
  - `Variant 2`: `shape = RoundedCornerShape(8.dp)`, `spacing = 6.dp`, `elevation = 2.dp`.
  - `Variant 3`: `shape = RoundedCornerShape(12.dp)`, `spacing = 8.dp`, `elevation = 1.dp`.
- **Then**: All properties match the design specification exactly.

#### TST-UI-217.3: Zone Indicator Bar Clipping & Pick-and-Place Interaction Test
- **Target**: `SensorFieldView` with `RoundedCornerShape`.
- **When**: Field has active zone color (`fieldState.zoneColor != Color.Transparent`) and `showLeftBar = true`.
- **Then**: Card container clips child composables (including 6dp indicator spacer) to the card shape without overflowing corners.
- **When**: Field is in configuration mode and `isSelectedForMove == true`.
- **Then**: Primary selection border (`2.dp primary`) is applied regardless of active variant shape.

#### TST-UI-217.4: Jetpack Compose Preview Suite Coverage Test
- **Target**: `SensorFieldView.kt` and `SensorGridScreen.kt`.
- **When**: Inspecting declared Compose previews via AST.
- **Then**: Previews exist for:
  - `PreviewCockpitVariant0_Baseline`
  - `PreviewCockpitVariant1_OutlinedTiles`
  - `PreviewCockpitVariant2_ElevatedCards`
  - `PreviewCockpitVariant3_Capsules`
  - Previews render both Dark AMOLED and Light themes.

#### TST-UI-217.5: Clean-Room Regression Test Suite
- **Command**: `./gradlew testDebugUnitTest`
- **Criteria**: 100% pass rate across the full application unit test suite.
