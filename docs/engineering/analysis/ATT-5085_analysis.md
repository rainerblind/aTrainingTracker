# Stage 1 Analysis: ATT-5085 - Live Rearview Radar Vertical Edge Overlay Across Tracking Tabs

**Ticket**: [ATT-5085](https://rainerblind.atlassian.net/browse/ATT-5085)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5085`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under Epic ATT-1286, cyclists riding in traffic need peripheral visual awareness of approaching vehicles across all workout tabs (Cockpit grids, Live Map, Elevation/Climb charts, Zone screens).
1. **Persistent Peripheral Awareness**: A vertical edge overlay must display approaching vehicle dots (0–140m / 175m) and threat levels (Green Clear, Amber Approaching, Red Fast Approaching).
2. **Touch & Gesture Pass-Through**: The vertical rail must not intercept `HorizontalPager` swipes, Google Map pan/zoom gestures, or Android system back-swipes; only the compact top status badge may consume click events (for reconnect/quick-action).
3. **Fail-Safe Disconnect Invariant**: The overlay must never indicate "All Clear" (Green) when the radar peripheral disconnects or drops packets (`DISCONNECTED_ERROR`).
4. **User Customization**: Expose user settings for Edge Side (`LEFT` vs `RIGHT`) and Clear-State Mode (`SLIM_RAIL`, `BADGE_ONLY`, `AUTO_HIDE_3S`) in `TuningPreferencesDataStore.kt` and `RadarSettingsSection.kt`.

---

## 2. Root Cause Analysis, Gap Analysis & Independent Peer Review
* **Architectural Gap**: `RadarStateRepository` provides a 4–8 Hz `StateFlow<RadarState>` stream (`ATT-5083`), but `TrackingTabsScreen.kt` lacks a persistent edge rail composable to visualize multi-target vectors across tab pages.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *Pointer-Input Pass-Through*: Verified that Compose layout containers (`Box`, `Canvas`) without click/pointer modifiers do not consume gestures. Touches fall through to `HorizontalPager` and Google Map. Only the top badge pill applies `Modifier.clickable`.
  - *Frame Interpolation*: Raw 4–8 Hz radar frames can cause visual jitter. Using `animateFloatAsState` (or spring/lerp) for normalized target distance ($y = 1 - d/140\text{m}$) yields smooth 60 fps dot motion.
  - *Fail-Safe Disconnect State*: When `connectionState == DISCONNECTED_ERROR`, the rail renders an amber/grey warning badge with a slashed radar icon, strictly preventing false "All Clear" signals.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (ATT-5085 - 3 SP)**:
  - Implement `RadarEdgeOverlay.kt` with high-contrast vertical rail, animated vehicle dots, and semantic threat colors.
  - Integrate `RadarEdgeOverlay` into `TrackingTabsScreen.kt` with pointer-event pass-through.
  - Add `radarEdgeSide` (`LEFT`/`RIGHT`) and `radarClearMode` (`SLIM_RAIL`/`BADGE_ONLY`/`AUTO_HIDE_3S`) to `TuningPreferencesDataStore.kt` & `RadarSettingsSection.kt`.
  - 9-language localization parity in `strings_radar.xml` (zero inline emojis).
  - Author unit and Compose contract tests (`RadarEdgeOverlayTest.kt`).
* **Out-of-Scope Non-Goals**:
  - `ATT-5086`: AudioTrack synthesized chimes and haptic alert engine (downstream ticket).
  - `ATT-5095` / `ATT-5096`: BLE V2 lateral lane-offset and vehicle sizing decoder.
  - `ATT-5102`: ANT+/BLE tail light control and automatic workout mode switching.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-TRK-062` (*Live Rearview Radar Vertical Edge Overlay*) and `TST-TRK-062`.
* **Chesterton's Fence**: `TrackingTabsScreen.kt` layers overlays (`zIndex` for banners and coachmarks). `RadarEdgeOverlay` will be placed with compact rail width (24–28 dp) and neutral zIndex so banners remain legible and touch pass-through remains intact.

---

## 5. Architectural Strategy & System Invariants
* **Component Design**: `com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlay.kt` observing `RadarStateRepository.radarState` and `TuningPreferencesDataStore`.
* **Core Invariants**:
  1. Zero touch interference with `HorizontalPager`, Live Map, or system edge navigation.
  2. Strict prohibition of green "All Clear" during `DISCONNECTED_ERROR` or uninitialized states.
  3. Zero SQLite database schema changes; 100% 9-language translation parity in `strings_radar.xml`.
* **Risk Rating**: **LOW-MEDIUM** (UI overlay isolated from hardware drivers and persistence layers; gesture pass-through easily validated via unit/Compose tests).
