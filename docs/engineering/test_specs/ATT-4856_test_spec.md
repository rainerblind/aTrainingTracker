# Stage 2: Requirement & Test Specification - ATT-4856: [Feature] Make clear when paused

**Ticket**: [ATT-4856](https://rainerblind.atlassian.net/browse/ATT-4856)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Test Engineer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification

### `REQ-TRK-045`: Clear Non-Recording Status Indication: Unified HUD Banner and Grid Dimming
- `REQ-TRK-045.1`: In `ScreenMode.TRACKING`, when workout recording is not active (`trackingMode != TrackingMode.TRACKING`), `SensorGridScreen` SHALL display an enlarged top HUD status banner with high-contrast amber warning styling (`DarkWarningContainer` / `LightWarningContainer`, `DarkOnWarningContainer` / `LightOnWarningContainer`, `DarkWarningAction` / `LightWarningAction`).
  - `READY`: Displays "Not Started" (`cockpit_status_not_started`), icon `Icons.Default.PlayCircle`, and action button "Start" (`Start`), dispatching `REQUEST_START_TRACKING`.
  - `MANUAL_PAUSED`: Displays "Paused" (`cockpit_status_paused`), icon `Icons.Default.PauseCircle`, and action button "Resume Now" (`cockpit_auto_pause_resume`), dispatching `REQUEST_RESUME_FROM_PAUSED`.
  - `AUTO_PAUSED`: Displays "Auto-Paused" (`cockpit_auto_pause_badge`), icon `Icons.Default.PauseCircle`, and action button "Resume Now" (`cockpit_auto_pause_resume`), dispatching `REQUEST_RESUME_FROM_PAUSED`.
- `REQ-TRK-045.2`: In `ScreenMode.TRACKING`, whenever `trackingMode != TrackingMode.TRACKING` (`READY`, `PAUSED`, `AUTO_PAUSED`), sensor grid content SHALL be rendered with `alpha = 0.6f` via compositing graphics layer, while the top status banner remains at full `1.0f` brightness. In `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`, grid content SHALL remain undimmed (`alpha = 1.0f`).
- `REQ-TRK-045.3`: `SensorGridScreen` SHALL register a broadcast receiver for `TrainingApplication.TRACKING_STATE_CHANGED`, updating both `trackingMode` and `isAutoPaused` synchronously.
- `REQ-TRK-045.4`: All new string resources (`cockpit_status_not_started`, `cockpit_status_paused`) SHALL maintain complete translation parity across all 9 supported languages.

---

## 2. Acceptance Criteria (Given-When-Then)

- **AC-1 (Ready / Not Started)**: *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is `READY`, *When* the screen renders, *Then* the top HUD banner displays "Not Started" with a "Start" action button, and the grid is dimmed to `alpha = 0.6f`.
- **AC-2 (Manual Paused)**: *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is manually `PAUSED`, *When* the screen renders, *Then* the top HUD banner displays "Paused" with a "Resume Now" action button, and the grid is dimmed to `alpha = 0.6f`.
- **AC-3 (Auto-Paused)**: *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is auto-paused (`PAUSED` + `isAutoPaused`), *When* the screen renders, *Then* the top HUD banner displays "Auto-Paused" with a "Resume Now" action button, and the grid is dimmed to `alpha = 0.6f`.
- **AC-4 (Tracking Active)**: *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is `TRACKING`, *When* the screen renders, *Then* no top HUD banner is displayed and the grid is at `alpha = 1.0f`.
- **AC-5 (Configuration / Preview Mode)**: *Given* the screen is in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`, *When* tracking is `READY` or `PAUSED`, *Then* no top HUD banner is displayed and the grid is at `alpha = 1.0f`.

---

## 3. Test Case Specification & Traceability Matrix

| Test ID | Method / Test Class | Target Requirement | Description | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-045.1` | Unit (`SensorGridStatusTest`) | `REQ-TRK-045.1`, `REQ-TRK-045.3` | Verify `resolveCockpitStatus` across modes (`READY`, `PAUSED`, `TRACKING`, `CONFIGURATION`, `PREVIEW`) | Planned |
| `TST-TRK-045.2` | Unit (`SensorGridStatusTest`) | `REQ-TRK-045.2` | Verify `resolveGridAlpha` dimming (0.6f vs 1.0f) across screen and tracking modes | Planned |
| `TST-TRK-045.3` | Unit (`TranslationParityTest`) | `REQ-TRK-045.4` | Verify 9-language translation parity for status banner strings | Planned |
| `TST-TRK-045.4` | Audit (`ThemeColorCentralizationAuditTest`) | `REQ-TRK-045.1` | Verify color token centralization without hardcoded colors in `SensorGridScreen.kt` | Planned |

---

## 4. System Invariants

1. **Telemetry Streaming Continuity**: Real-time sensor readings continue updating continuously in Compose cards even when recording is paused or unstarted.
2. **True Zone Colors**: Grid dimming applies overall alpha without tinting or hue-shifting heart rate / power training zone colors.
3. **Touch Interaction Integrity**: Field clicks, edits, map toggles, and double-tap bike bell gestures continue to operate without interference.
