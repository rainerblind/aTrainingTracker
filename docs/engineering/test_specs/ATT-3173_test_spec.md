# Stage 2: Requirement & Test Specification - ATT-3173

## 1. Requirement Specification (`REQ-UI-346`)

### Formal Requirement Clause
| Field | Value |
| :--- | :--- |
| **Requirement ID** | `REQ-UI-346` |
| **Title** | Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen |
| **Primary Artifact** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt` |
| **Amends** | `REQ-UI-339` |
| **Design Guidelines** | `docs/design_guidelines.md` §2.3 |
| **Target Release** | `V4.9.39` |
| **Status** | In Progress |

### Architectural Specification & Constraints
The system SHALL render the elevation profile on tracking screens (`SensorGridScreen.kt`) at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) determined by `calculateElevationProfileHeight(range)` regardless of whether the embedded map is enabled or disabled, eliminating vertically stretched or oversized profile rendering when the map is hidden (ATT-3173, amending `REQ-UI-339` and aligning with Design Guidelines §2.3):

1. **Elevation Profile Surface Constraints (`SensorGridScreen.kt`)**:
   - The wrapping `Surface` for `ElevationProfile` SHALL NOT apply `Modifier.weight(1f)`.
   - The wrapping `Surface` SHALL declare `Modifier.fillMaxWidth().padding(top = 8.dp)` without vertical weight or forced parent height filling.

2. **Canonical Elevation Profile Invocation (`SensorGridScreen.kt`)**:
   - `ElevationProfile` SHALL be invoked with `pathPoints`, `currentDistance`, and `modifier = Modifier.fillMaxWidth()`, leaving `fillParentHeight` to its default value (`false`).
   - This guarantees that `totalCanvasHeight` evaluates to `cachedData.adaptiveHeight` ($70\text{dp} \dots 200\text{dp}$) computed via `calculateElevationProfileHeight(range)`.

3. **Sensor Grid Flexible Weight & Containment**:
   - The scrollable sensor grid `Column` SHALL retain `Modifier.weight(1f, fill = false)`.
   - When few sensor fields exist (1–2 rows), the sensor grid wraps its content naturally without stretching.
   - When many sensor fields exist (4–6 rows), the sensor grid scrolls smoothly within the remaining space above the elevation profile and never pushes the elevation profile off-screen.

4. **Preservation of System Invariants**:
   - When `showMap == true`, the map retains primary flexible space (`weight(1f)`), with `ElevationProfile` rendering below it.
   - When `state.pathPoints.size < 2`, the elevation profile remains suppressed.
   - Ambient `NavigationHintsOverlay` positioning, map recomposition barrier isolation, and 100% full-suite unit test pass rate MUST be strictly preserved.

---

### Chesterton's Fence Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`).
3. **Root Reason for Existing Formulation**:
   - In `ATT-3107`, `weight(1f)` and `fillParentHeight = true` were applied to prevent the elevation profile from being compressed by unconstrained sensor tiles. However, this over-corrected and forced the profile to consume half the screen on modern high-resolution displays.
4. **Preservation of Core Invariants**:
   - Sensor grid scrollability via `weight(1f, fill = false)`, canonical adaptive height via `calculateElevationProfileHeight(range)`, and 100% clean-room test pass rate remain strictly preserved.

---

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Sole Profile Canonical Height)**:
  - *Given* an active workout tracking tab with `showElevationProfile = true` and `showMap = false`,
  - *When* `SensorGridScreen` renders in `ScreenMode.TRACKING`,
  - *Then* `ElevationProfile` renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) and does NOT stretch to 50%+ of screen height.
* **AC-2 (Sensor Grid Flexibility)**:
  - *Given* 1–2 sensor rows with map disabled and elevation profile enabled,
  - *When* the screen renders,
  - *Then* sensor tiles wrap content height naturally without forced spacing, and the elevation profile is anchored neatly below.
* **AC-3 (Multi-Row Sensor Grid Scrollability)**:
  - *Given* 6 sensor rows with map disabled and elevation profile enabled,
  - *When* the screen renders,
  - *Then* the sensor grid is vertically scrollable within the remaining space and does not crowd out the elevation profile.
* **AC-4 (Map & Profile Coexistence)**:
  - *Given* both map and elevation profile enabled,
  - *When* the screen renders,
  - *Then* the map retains `weight(1f)` and the elevation profile renders below it at its standard adaptive height.

---

## 2. Test Specification (`TST-UI-306`)

### Test Suite Details
| Test ID | Method / Scope | Target Component | Verifies |
| :--- | :--- | :--- | :--- |
| `TST-UI-306.1` | `testElevationProfile_doesNotApplyWeightWhenMapDisabled` | `SensorGridScreenLayoutTest.kt` | Surface does not apply `Modifier.weight(1f)` when `!showMap` |
| `TST-UI-306.2` | `testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize` | `SensorGridScreenLayoutTest.kt` | Profile receives `fillMaxWidth()` and default `fillParentHeight` |
| `TST-UI-306.3` | `testSensorGrid_appliesWeightConstraintWithFillFalse` | `SensorGridScreenLayoutTest.kt` | Sensor grid retains `weight(1f, fill = false)` |
| `TST-UI-306.4` | `testElevationProfile_guardedByPathPointsSize` | `SensorGridScreenLayoutTest.kt` | Rendering guarded by `state.pathPoints.size >= 2` |
| `TST-UI-306.5` | `testAdaptiveHeight_boundsAndFormula` | `ElevationProfileLayoutTest.kt` | `calculateElevationProfileHeight` respects $[70\text{dp}, 200\text{dp}]$ |
| `TST-UI-306.6` | `TranslationParityTest` | Localization Suite | 100% parity across 9 locales (0 new strings) |
| `TST-UI-306.7` | Clean-room regression | Full Unit Test Suite | 100% test pass rate across 2376+ unit tests |

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Verification Method |
| :--- | :--- | :--- |
| `REQ-UI-346.1` (Surface Unweighted) | `TST-UI-306.1` | Static AST / source contract assertion |
| `REQ-UI-346.2` (Default Canvas Height) | `TST-UI-306.2`, `TST-UI-306.5` | Static contract + mathematical unit test |
| `REQ-UI-346.3` (Sensor Grid Bounded) | `TST-UI-306.3` | Source layout contract test |
| `REQ-UI-346.4` (Invariants Preserved) | `TST-UI-306.4`, `TST-UI-306.6`, `TST-UI-306.7` | Guard test + Localization test + Full test suite |
