# Stage 2: Requirement & Test Specification - ATT-2624: Harmonize cockpit tile grid spacing between preview and tracking screen and refine thick border ergonomics

**Ticket**: [ATT-2624](https://atrainingtracker.atlassian.net/browse/ATT-2624)  
**Sub-task**: [ATT-2696](https://atrainingtracker.atlassian.net/browse/ATT-2696) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2624`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-292`)

### REQ-UI-292: Cockpit Tile Grid Spacing Harmonization, Proportional Border Separation, and Ergonomic Content Clearance

The system SHALL harmonize cockpit tile grid spacing between the settings live preview card (`CockpitTypographySection.kt`) and the live tracking cockpit (`SensorGridScreen.kt`), dynamically scale inter-tile spacing with border thickness and corner radius, and provide ergonomic inner content clearance (ATT-2624):

1. **Centralized Dynamic Grid Spacing Resolution (`SensorFieldStyle.kt`)**:
   - `SensorFieldStyle.Companion` SHALL provide `fun resolveGridSpacing(variant: SensorFieldVariant, cornerRadius: Dp, borderThickness: Dp): Dp`.
   - When `variant == SensorFieldVariant.CLASSIC_SEAMLESS`, `cornerRadius <= 0.dp`, and `borderThickness <= 1.0.dp`, it SHALL return `0.dp`, preserving the seamless edge-to-edge default production grid.
   - When `borderThickness > 1.0.dp` or `cornerRadius > 0.dp`, it SHALL proportionally scale inter-tile spacing according to outline thickness and corner geometry:
     - `thicknessPadding = (borderThickness - 1.0.dp).coerceAtLeast(0.dp) * 1.5f`
     - `cornerPadding = if (cornerRadius > 0.dp) (cornerRadius * 0.15f).coerceAtLeast(1.dp) else 0.dp`
     - `val computed = baseStyle.gridSpacing.coerceAtLeast(2.dp) + thicknessPadding + cornerPadding`
     - clamped via `coerceIn(2.dp, 12.dp)`.
   - This ensures tiles with thick outlines (e.g. 2.0–4.0 dp) float with sufficient whitespace (6–10 dp) instead of colliding with adjacent tiles.

2. **100% WYSIWYG Parity between Live Cockpit & Settings Preview (`SensorGridScreen.kt`, `CockpitTypographySection.kt`)**:
   - `SensorGridScreen.kt` SHALL compute `effectiveSpacing` using `SensorFieldStyle.resolveGridSpacing(tuningConfig.sensorFieldVariant, tuningConfig.sensorFieldCornerRadius.dp, tuningConfig.sensorFieldBorderThickness.dp)` when `gridSpacing == 0.dp && fieldShape == RectangleShape`.
   - `CockpitTypographySection.kt` SHALL excise the hardcoded `baseStyle.gridSpacing.coerceAtLeast(6.dp)` and compute `effectiveSpacing` using `SensorFieldStyle.resolveGridSpacing(sensorFieldVariant, sensorFieldCornerRadius.dp, sensorFieldBorderThickness.dp)`.
   - Both the live tracking grid and settings preview card SHALL evaluate to identical inter-tile spacing under all slider configurations.

3. **Ergonomic Inner Padding Clearance (`PreviewCockpitTile`)**:
   - In `PreviewCockpitTile`, horizontal and vertical content padding SHALL dynamically accommodate `borderThickness` (`horizontal = (4.dp + borderThickness / 2).coerceAtLeast(4.dp)`, `vertical = (8.dp + borderThickness / 2).coerceAtLeast(8.dp)`), preventing numbers and labels from touching or feeling cramped against thick border strokes.

4. **Preserved Invariants**:
   - Zero new string resources required.
   - Default preset baselines (0 dp, 4 dp, 6 dp, 8 dp) remain preserved.
   - Move-selection highlight and drag-and-drop mechanics in configuration mode remain intact.
   - File modularity boundaries (< 400 lines) remain strictly preserved.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Refines Clause 1 of `REQ-UI-277` (*Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming*) and Clause 1 & Clause 6 of `REQ-UI-276` (*Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast*).
2. **Historical Origin & Commit Trace**:
   - Commit `2f5c759a` (Sprint 2026-41.1, ATT-2456): Granular sliders for corner radius, border thickness, and border contrast.
   - Commit `cd1c7849` (Sprint 2026-41.1, ATT-2457): Preview tile styling with hardcoded `coerceAtLeast(6.dp)`.
3. **Root Reason for Existing Formulation**: Grid spacing was previously defined as static preset values on `SensorFieldStyle`, leaving dynamic slider adjustments in `AdvancedTuningDialog` unable to influence grid spacing and prompting an ad-hoc `coerceAtLeast(6.dp)` clamp in the settings preview.
4. **Preservation of Core Invariants**:
   - Production baseline defaults (0 dp seamless grid for `CLASSIC_SEAMLESS`), preset baseline anchors, selection border overrides, and full clean-room unit test pass rate remain strictly preserved.

---

### Acceptance Criteria (Given-When-Then)

* **Scenario 1: Baseline Seamless Grid Invariant**
  - **Given** the baseline `CLASSIC_SEAMLESS` preset (0 dp radius, 1.0 dp border),
  - **When** evaluating grid spacing,
  - **Then** `SensorFieldStyle.resolveGridSpacing` SHALL return `0.dp`.

* **Scenario 2: Dynamic Spacing Expansion for Thick Borders**
  - **Given** an athlete increasing border thickness to 3.0 dp or 4.0 dp,
  - **When** evaluating grid spacing,
  - **Then** `SensorFieldStyle.resolveGridSpacing` SHALL return expanded whitespace $\ge 6\text{ dp}$ (up to 10 dp).

* **Scenario 3: WYSIWYG Parity Between Preview and Live Screen**
  - **Given** the settings live preview card in `CockpitTypographySection.kt` and the tracking screen in `SensorGridScreen.kt`,
  - **When** adjusting variant, corner radius, or border thickness,
  - **Then** both components SHALL compute identical inter-tile grid spacing.

* **Scenario 4: Inner Padding Compensation**
  - **Given** thick border strokes in `PreviewCockpitTile`,
  - **When** rendered,
  - **Then** internal content padding SHALL expand to prevent metric text from clipping or crowding the border.

---

## 2. Test Specification (`TST-UI-252`)

### TST-UI-252: Cockpit Tile Grid Spacing Harmonization and Thick Border Ergonomics Verification

1. **SensorFieldStyle Dynamic Spacing Unit Tests (`SensorFieldStyleContractTest.kt`)**:
   - Verify `resolveGridSpacing` for `CLASSIC_SEAMLESS` baseline (0 dp radius, 1.0 dp border) returns `0.dp`.
   - Verify `resolveGridSpacing` for `OUTLINED_TILES` baseline (8 dp radius, 2.0 dp border) returns $\ge 6\text{ dp}$.
   - Verify `resolveGridSpacing` with thick borders (3.0 dp, 4.0 dp) scales spacing to $\ge 8\text{ dp}$ preventing border collision.
   - Verify `resolveGridSpacing` clamps spacing within `[2.dp, 12.dp]` when non-baseline.

2. **Settings Live Preview Contract Tests (`CockpitPreviewTileContractTest.kt`)**:
   - Verify `CockpitTypographySection` calls `SensorFieldStyle.resolveGridSpacing`.
   - Verify preview tile horizontal arrangement uses `effectiveSpacing` without hardcoded 6.dp clamp.
   - Verify `PreviewCockpitTile` adapts padding with border thickness.

3. **SensorGridScreen Contract Tests (`SensorFieldStyleContractTest.kt`)**:
   - Verify `SensorGridScreen` resolves `effectiveSpacing` via `SensorFieldStyle.resolveGridSpacing`.

4. **Clean-Room Regression Suite Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Implementation File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-292.1` (Dynamic Spacing Resolution) | `TST-UI-252.1` | `SensorFieldStyle.kt` | Unit Tests (`SensorFieldStyleContractTest.kt`) |
| `REQ-UI-292.2` (WYSIWYG Live & Preview Parity) | `TST-UI-252.2`, `TST-UI-252.3` | `SensorGridScreen.kt`, `CockpitTypographySection.kt` | Structural Contract Tests |
| `REQ-UI-292.3` (Inner Padding Clearance) | `TST-UI-252.2` | `CockpitTypographySection.kt` | Structural Contract Tests |
| `REQ-UI-292.4` (Preserved Invariants & Modularity) | `TST-UI-252.4` | Full Project | Clean-Room Suite (`testDebugUnitTest`) |
