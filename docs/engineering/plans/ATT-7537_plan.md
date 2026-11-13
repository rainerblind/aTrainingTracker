# Stage 3 Implementation Plan - ATT-7537: Full-Screen Radar Overlay Mode

## 1. Architecture & SWE.2 Component Decomposition
- **Preferences & Settings Layer**:
  - `settings/TuningPreferencesDataStore.kt`: Extend `RadarEdgeSide` enum to `LEFT`, `FULL_SCREEN`, `RIGHT`. Backward-compatible fallback via `safeEnumValueOf(raw, RIGHT)` is preserved.
  - `ui/settings/radar/RadarSettingsSection.kt`: Update `TuningSegmentedButtonRow` to present `Links | Vollbild | Rechts` and conditionally hide `RadarClearMode` selector when `FULL_SCREEN` is active (`REQ-TRK-062.5b`).
  - `ui/settings/tuning/AdvancedTuningAccordion.kt`: Refactor `formatCyclingRadarSubtitle` from binary ternary to `when (edgeSide)` covering `FULL_SCREEN` (`REQ-TRK-062.5c`).
- **Tracking & Live Overlay Layer**:
  - `ui/tracking/radar/RadarEdgeOverlay.kt`: Add pure kinematics helper `RadarOverlayMath.calculateFullScreenHorizontalFraction` mapping lateral lane offsets and `RadarTrafficOrientation` across tracking screen width. When `edgeSide == FULL_SCREEN`, expand container to `fillMaxSize()`, place `RadarStatusBadgePill` at `TopEnd`, render zero rail background when clear/disconnected, and render high-contrast vehicle badges (translucent threat background, white border, upward interpolation) on active threats with 100% touch pass-through (`REQ-TRK-062.1a`, `062.3a`, `062.4a`).
  - `ui/tracking/trackingtabs/TrackingTabsScreen.kt`: Bind `Modifier.fillMaxSize()` and `Alignment.Center` when `FULL_SCREEN` is configured (`REQ-TRK-062.1a`).
- **Localization Layer**:
  - `res/values*/strings_radar.xml`: Add `radar_overlay_mode_fullscreen` across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) (`REQ-TRK-062.6a`).

## 2. Atomic Implementation Steps
1. **Step 1 - DataStore Model & Accordion Subtitle Extension**:
   - In `TuningPreferencesDataStore.kt`, update `enum class RadarEdgeSide { LEFT, FULL_SCREEN, RIGHT }`.
   - In `AdvancedTuningAccordion.kt`, update `formatCyclingRadarSubtitle` to use `when (edgeSide)`.
   - Extend `RadarAccordionSectionTest.kt` with `FULL_SCREEN` subtitle test case.
2. **Step 2 - 9-Language Localization Parity**:
   - Add `radar_overlay_mode_fullscreen` to `strings_radar.xml` across all 9 locales.
   - Verify parity via `TranslationParityTest.kt`.
3. **Step 3 - Settings UI & Clear Mode Gating**:
   - In `RadarSettingsSection.kt`, update `TuningSegmentedButtonRow` options to `listOf(LEFT, FULL_SCREEN, RIGHT)`.
   - Wrap `RadarClearMode` segmented row in `if (tuningConfig.radarEdgeSide != RadarEdgeSide.FULL_SCREEN)`.
4. **Step 4 - Full-Screen Overlay Kinematics & Rendering**:
   - In `RadarOverlayMath`, implement `calculateFullScreenHorizontalFraction` handling `LaneState` and `RadarTrafficOrientation`.
   - In `RadarEdgeOverlay.kt`, implement `FULL_SCREEN` branching: `TopEnd` status pill, zero rail when clear/error, and `RadarFullScreenThreatOverlay` with 100% pointer pass-through.
   - In `TrackingTabsScreen.kt`, adapt modifier alignment and sizing for `FULL_SCREEN`.
5. **Step 5 - Automated Test Coverage & Verification**:
   - Add test cases in `RadarEdgeOverlayTest.kt` verifying full-screen normalization, zero rail when clear, and lateral coordinate mapping.
   - Run targeted test suite: `com.atrainingtracker.trainingtracker.ui.tracking.radar.*`, `com.atrainingtracker.trainingtracker.ui.settings.tuning.*`, `TranslationParityTest`.
   - Run `python3 tools/verify_requirement_governance.py`.

## 3. Invariant Protection & System Properties
- **Zero Touch Interception**: Container and vehicle markers attach zero pointer/click modifiers, guaranteeing unhindered `HorizontalPager` swipes and MapView gestures (`REQ-TRK-062.3`).
- **Upward Kinematics**: Approaching vehicles strictly move from bottom ($y = 1.0$) upward ($y = 0.0$) toward rider.
- **Fail-Safe Disconnect**: `DISCONNECTED_ERROR` displays amber hazard badge and strictly prohibits green "All Clear".
- **Zero Schema Migrations**: Preferences persisted as DataStore string; zero SQLite migrations.

## 4. UI Consistency & Design Guidelines Compliance
- Uses `TuningSegmentedButtonRow` with `icon = {}` and single-line German label (`Vollbild`) adhering to `docs/design_guidelines.md` §1.4 and §2.4.
- Semi-transparent vehicle badges (85% alpha) with high-contrast borders ensure underlying Cockpit metrics remain readable.

## 5. Traceability Matrix
- `Step 1` -> `REQ-TRK-062.5a, 062.5c` -> `TST-TRK-062.3, TST-TRK-062.4`
- `Step 2` -> `REQ-TRK-062.6a` -> `TST-TRK-062.5`
- `Step 3` -> `REQ-TRK-062.5b` -> `TST-TRK-062.4`
- `Step 4` -> `REQ-TRK-062.1a, 062.3a, 062.4a` -> `TST-TRK-062.1, TST-TRK-062.2`
- `Step 5` -> `REQ-PRO-001` -> `TST-TRK-062.6`
