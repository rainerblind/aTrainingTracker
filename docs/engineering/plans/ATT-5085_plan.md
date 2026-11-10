# Stage 3 Implementation Plan: ATT-5085 - Live Rearview Radar Vertical Edge Overlay Across Tracking Tabs

**Ticket**: [ATT-5085](https://rainerblind.atlassian.net/browse/ATT-5085) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5085`  
**Requirement Mapping**: `REQ-TRK-062` (`062.1`–`062.6`) | **Test Spec**: `TST-TRK-062` (`062.1`–`062.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **RadarEdgeOverlay Component (`RadarEdgeOverlay.kt`)**:
  - Located in `com.atrainingtracker.trainingtracker.ui.tracking.radar`.
  - Outer rail container is completely unconsumed (zero `clickable`, zero `pointerInput` consumption), ensuring touch and scroll events pass directly to `HorizontalPager`, Google Map pan/zoom, and Android system edge gestures (`REQ-TRK-062.3`).
  - Top status badge pill: Compact clickable surface (`Modifier.clickable { onBadgeClick() }`) for tap-to-reconnect and quick actions.
  - Multi-target rendering: Normalizes target ranges ($y = 1 - d/d_{\max}$, $d_{\max} = 140\text{m}$) with `animateFloatAsState` for smooth 60 fps interpolation between 4–8 Hz sensor updates (`REQ-TRK-062.1`).
  - Threat state styling: Green clear road, Amber for `APPROACHING`, pulsing Red for `FAST_APPROACHING` (`REQ-TRK-062.2`).
  - Fail-safe disconnect invariant: `DISCONNECTED_ERROR` or `DISCONNECTED` displays an amber/grey hazard warning badge, strictly preventing false "All Clear" signals (`REQ-TRK-062.4`).
  - Clear-state behavior: Supports `SLIM_RAIL` (default unobtrusive green line), `BADGE_ONLY`, and `AUTO_HIDE_3S` (auto-hides rail after 3s of clear road).
* **Tuning Preferences & Settings (`TuningPreferencesDataStore.kt` & `RadarSettingsSection.kt`)**:
  - Enums: `RadarEdgeSide` (`LEFT`, `RIGHT`, default `RIGHT`), `RadarClearMode` (`SLIM_RAIL`, `BADGE_ONLY`, `AUTO_HIDE_3S`, default `SLIM_RAIL`).
  - Persist in `TuningPreferencesDataStore.kt` and expose in `RadarSettingsSection.kt` via Material 3 `SingleChoiceSegmentedButtonRow` for binary edge choice and `FilterChip` for clear-mode options per `docs/design_guidelines.md` (`REQ-TRK-062.5`).
* **TrackingTabsScreen Integration (`TrackingTabsScreen.kt`)**:
  - Host `RadarEdgeOverlay` within the main content `Box`, aligned to `Alignment.CenterStart` (Left) or `Alignment.CenterEnd` (Right), active during `ScreenMode.TRACKING` whenever a `BIKE_RADAR` sensor is present or simulating.
* **UI Consistency & Localization**:
  - Colors: Theme-based semantic tokens from `MaterialTheme.colorScheme` and `TTColor` (zero hardcoded hex literals).
  - Localization: 100% 9-language translation parity in `strings_radar.xml` (zero inline emojis) (`REQ-TRK-062.6`).

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Core Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Preferences Model | `TuningPreferencesDataStore.kt`<br>`TuningPreferencesDefaults.kt` | Define `RadarEdgeSide` and `RadarClearMode` enums; add DataStore keys, flow properties, and update methods with defaults. | `REQ-TRK-062.5`<br>`TST-TRK-062.4` |
| **2** | Settings UI | `RadarSettingsSection.kt` | Add edge-side segmented button and clear-mode filter chips to `RadarSettingsSection.kt` with reactive DataStore binding. | `REQ-TRK-062.5`<br>`TST-TRK-062.4` |
| **3** | Edge Overlay | `RadarEdgeOverlay.kt` | Implement composable with unconsumed rail Box, clickable top badge, 60 fps dot interpolation, threat colors, and fail-safe disconnect. | `REQ-TRK-062.1-4`<br>`TST-TRK-062.1-3` |
| **4** | Cockpit Screen | `TrackingTabsScreen.kt` | Embed `RadarEdgeOverlay` in top-level content Box gated by radar presence/state, with left/right alignment and touch pass-through. | `REQ-TRK-062.1,3`<br>`TST-TRK-062.1` |
| **5** | Unit Tests | `RadarEdgeOverlayTest.kt`<br>`TuningPreferencesDataStoreTest.kt` | Author unit tests verifying kinematics, normalization, threat color mapping, fail-safe disconnect state, and preference persistence. | `REQ-TRK-062.1-5`<br>`TST-TRK-062.1-4` |
| **6** | Parity & Regression | `TranslationParityTest.kt`<br>Targeted Suite | Verify 9-language parity and run targeted test suite (`ui.tracking.radar.*`, `settings.*`, `build.*`). | `REQ-PRO-001`<br>`TST-TRK-062.5,6` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production edits until Gate 3 audit passes (`tools/jira_util.py check-gate`).
* **Touch Pass-Through**: The vertical rail must not capture pointer events, ensuring unhindered `HorizontalPager` and Map gestures.
* **Fail-Safe Disconnect**: Zero false green display when radar connection is lost or in error.
* **Database & Schema**: Zero SQLite schema migrations; purely in-memory UI and DataStore preference persistence.
* **Risk Rating**: **LOW-MEDIUM** (Isolated visual overlay and preference additions; easily validated via unit/Compose tests).
