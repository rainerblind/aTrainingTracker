# ATT-5119 Walkthrough: 1-Tap Radar Audio Snooze, Peloton Sync & Urban Alert Suppression

**Ticket**: [ATT-5119](https://rainerblind.atlassian.net/browse/ATT-5119)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-TRK-064` | **Test Mapping**: `TST-TRK-064`  
**Branch**: `feature/ATT-5119`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-10  

---

## 1. Executive Summary

Under `ATT-5119` (governed by `REQ-TRK-064` / `TST-TRK-064`):
- **1-Tap Cockpit Audio Snooze (`REQ-TRK-064.1`)**: Added `RadarAudioSnoozeState.kt` and `RadarAudioSnoozeMode` (15m, 30m, Fast Threats Only, Rest of Ride, Resume). Wired status badge pill click in `RadarEdgeOverlay.kt` to open `RadarLightQuickSwitchSheet.kt` across all radar peripherals regardless of tail light support.
- **Muted Badge Countdown Indicator & Visual Continuity (`REQ-TRK-064.2`)**: Rendered muted pill badge with remaining minutes countdown (`15m`, `30m`) or high-threat icon (`!`, `P`, `U`, `M`), formatted via `radar_snooze_active_format`. Preserved 100% un-dimmed visual vertical rail tracking and animations during all snooze states.
- **Automatic Snooze Expiration & Handlebar Shifter Integration (`REQ-TRK-064.3`)**: Implemented 1s ticker coroutine in `RadarAlertManager.kt` auto-restoring standard audio upon timer expiry, and exposed `@JvmStatic` companion `toggleRadarAudioSnooze()` for remote handlebar/shifter button invocation (`ATT-1288`).
- **Peloton Light Mode Audio Sync (`REQ-TRK-064.4`)**: Automated chime suppression when tail light enters `PELOTON` mode while `radarPelotonAudioSync` is active, retaining `FAST_APPROACHING` red threat alerts and immediately restoring on mode change.
- **Smart Urban Congestion Suppression (`REQ-TRK-064.5`)**: Filtered repetitive approach chimes when $\ge 3$ vehicles $< 5.0\text{ m/s}$ linger for $\ge 30\text{ s}$, auto-exiting after $\ge 20\text{ s}$ clear while guaranteeing high-speed threat alarms sound immediately.
- **Settings Configuration & DataStore Persistence (`REQ-TRK-064.6`)**: Added `radarPelotonAudioSync` and `radarSmartUrbanMute` in `TuningPreferencesDataStore.kt` and settings toggles in `RadarSettingsSection.kt`.
- **9-Language Localization Parity (`REQ-TRK-064.7`)**: Integrated 11 localized strings across EN, DE, ES, FR, IT, JA, NL, PL, PT in `strings_radar.xml` with 100% parity and zero emojis.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-064.1` | `TST-TRK-064.1` | `RadarAudioSnoozeState.kt`, `RadarAlertManager.kt` | 1-Tap Snooze Gating, Modes & Manual Resume | **Verified** |
| `REQ-TRK-064.2` | `TST-TRK-064.2` | `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt` | Status Badge Countdown Pill, Touch Pass-Through | **Verified** |
| `REQ-TRK-064.3` | `TST-TRK-064.3` | `RadarAlertManager.kt` | 1s Ticker Countdown, Auto-Expiry & Remote Toggle | **Verified** |
| `REQ-TRK-064.4` | `TST-TRK-064.4` | `RadarAlertManager.kt`, `RadarLightController.kt` | Peloton Light Sync Gating & Red Threat Safety | **Verified** |
| `REQ-TRK-064.5` | `TST-TRK-064.5` | `RadarAlertManager.kt` | Urban Congestion Window Filter & Recovery | **Verified** |
| `REQ-TRK-064.6` | `TST-TRK-064.6` | `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt` | Preference Persistence & UI Settings Toggles | **Verified** |
| `REQ-TRK-064.7` | `TST-TRK-064.7` | `strings_radar.xml` (9 locales) | 9-Language Parity Contract Test | **Verified** |

---

## 3. Verification & Test Execution

- **Explicit Executed Test Scope**:
  - `Tier 1 targeted: com.atrainingtracker.trainingtracker.radar.*, com.atrainingtracker.trainingtracker.audio.radar.*, com.atrainingtracker.banalservice.devices.radar.*, com.atrainingtracker.trainingtracker.ui.tracking.radar.*, com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest, com.atrainingtracker.trainingtracker.ui.components.core.*, com.atrainingtracker.trainingtracker.localization.TranslationParityTest, com.atrainingtracker.trainingtracker.build.*`
  - Suites run: `RadarAlertManagerTest` (15m/30m/fast-only/rest-of-ride gating, 1s ticker auto-expiration, remote shifter toggle, peloton sync audio suppression, urban congestion filtering and recovery, debouncing), `RadarEdgeOverlayTest` (snooze badge text formatting, threat colors, fail-safe disconnect invariants), `TuningConfigTest` (defaults and boundary clamping), `TranslationParityTest` (100% key and format parity across all 9 locales), `ThemeColorCentralizationAuditTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`.
  - Result: **100% PASS** (exit code 0, 0 failures, 0 errors, 0 regressions under `/tmp/att_build_merge.lock`).
- **Physical Device / On-Device Checks**:
  - Executed `adb devices` (no physical devices attached; emulator boot strictly avoided per Rule 79). Protocol latch preservation across 4–8 Hz updates, badge pill touch interaction, and countdown semantics verified via Robolectric unit and Compose tests. Documented N/A for ADB screenshot capture.
