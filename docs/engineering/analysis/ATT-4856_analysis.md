# Stage 1: Problem Domain & Root Cause Analysis - ATT-4856: [Feature] Make clear when paused

**Ticket**: [ATT-4856](https://rainerblind.atlassian.net/browse/ATT-4856)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Problem Domain & Forensic Root Cause

In `SensorGridScreen.kt`, only `AUTO_PAUSED` (`TrackingMode.PAUSED && isAutoPaused`) currently displays a top visual indicator via a small `AutoPauseHudBanner` pill. `MANUAL_PAUSED` (`TrackingMode.PAUSED && !isAutoPaused`) and `READY` (`TrackingMode.READY`, not yet recording) render no banner or status indication on the sensor tab. Because real-time telemetry (heart rate, cadence, speed) continues streaming into Compose cards even when not recording, athletes easily mistake a paused or unstarted workout for an active recording session.

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)

1. **State Exposure in `SensorGridScreen.kt`**: `SensorGridScreen` currently only tracks `isAutoPaused = TrainingApplication.isAutoPaused()`. It must track both `trackingMode` (`TrainingApplication.getTrackingMode()`) and `isAutoPaused`, updated on `TrainingApplication.TRACKING_STATE_CHANGED`.
2. **Unified HUD Banner (`NonRecordingStatusBanner`)**:
   - `READY`: Displays *Not Started* (`cockpit_status_not_started`), icon `Icons.Default.PlayCircle`, action button *Start* (`Start`), dispatching `REQUEST_START_TRACKING`.
   - `MANUAL_PAUSED`: Displays *Paused* (`cockpit_status_paused`), icon `Icons.Default.PauseCircle`, action button *Resume Now* (`cockpit_auto_pause_resume`), dispatching `REQUEST_RESUME_FROM_PAUSED`.
   - `AUTO_PAUSED`: Displays *Auto-Paused* (`cockpit_auto_pause_badge`), icon `Icons.Default.PauseCircle`, action button *Resume Now* (`cockpit_auto_pause_resume`), dispatching `REQUEST_RESUME_FROM_PAUSED`.
   - Visual styling: High-contrast amber warning palette (`DarkWarningContainer` / `LightWarningContainer`, `DarkOnWarningContainer` / `LightOnWarningContainer`), enlarged typography (`titleSmall` / `labelLarge`), padding, and 36.dp action button.
3. **Sensor Grid Dimming (`alpha = 0.6f`)**:
   - In `ScreenMode.TRACKING`, when `trackingMode != TrackingMode.TRACKING` (`READY`, `PAUSED`, `AUTO_PAUSED`), sensor grid content is rendered with `alpha = 0.6f` via `Modifier.graphicsLayer { this.alpha = 0.6f }`.
   - The HUD banner floats above with full `alpha = 1.0f`.
   - `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW` remain undimmed (`alpha = 1.0f`).

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. *Original Requirement & Target*: Extends `REQ-TRK-018` (auto-pause HUD banner) into `REQ-TRK-045` (unified non-recording cockpit status). Targets `SensorGridScreen.kt`.
2. *Historical Origin & Commit Trace*: `ATT-45` (introduced `AutoPauseHudBanner`), `ATT-3574` (centralized warning colors), `ATT-4836` (sport auto-pause settings).
3. *Root Reason for Existing Formulation*: Originally, manual pause navigated the user away to the control drawer or was considered obvious from the start/stop screen. With sensor tabs serving as the primary cockpit, athletes need immediate status visibility without checking drawer menus.
4. *Preservation of Core Invariants*: Sensor streaming, double-tap bell gesture, live segment/climb bottom sheets, and true training zone color definitions are preserved without hue shifts.

---

## 4. Scope Grounding & Call Sites

- **In-Scope**:
  - `SensorGridScreen.kt`: Broadcast receiver for tracking state, unified `NonRecordingStatusBanner`, and grid alpha dimming (both dual-pane and single-pane).
  - Pure helper / state resolution logic with unit tests.
  - 9-language localization parity for new strings (`cockpit_status_not_started`, `cockpit_status_paused`).
- **Out-of-Scope**: Altering bottom sheet behavior, drawer navigation, or recording engine state machines.

---

## 5. Invariants & Risk Assessment

- **Risk Level**: **LOW**. Pure UI presentation and broadcast dispatch; zero alterations to database or service state machines.
- **Invariants**: 100% test pass rate across existing unit/theme/contract tests.
