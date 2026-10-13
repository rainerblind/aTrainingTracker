# Stage 3: Implementation Plan - ATT-2467: Standalone External ADB GPX Replay Tool

**Ticket**: [ATT-2467](https://atrainingtracker.atlassian.net/browse/ATT-2467)  
**Sub-task**: [ATT-2935](https://atrainingtracker.atlassian.net/browse/ATT-2935) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Unscheduled` (Developer Testing Tooling)  
**Active Sprint**: `Human Review`  
**Branch**: `feature/ATT-2467`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Overview & Strategy

Implement `tools/fake_gps.py` as an external developer simulation CLI tool. It connects to Android test devices via ADB, establishes a native test location provider (`gps`), and replays GPX routes at configurable speeds with real-time interactive terminal controls. 

**Zero modifications are made to `app/`**.

---

## 2. Implementation Steps

### Step 1: Core Geodesics, GPX Parsing & Sample Tracks (`tools/fake_gps.py`)
- Haversine distance calculation and forward azimuth bearing formula.
- Linear interpolation between route coordinates $(P_i, P_{i+1})$.
- Perpendicular coordinate offset for deliberate off-route deviation.
- GPX XML parser extracting trackpoints (`lat`, `lon`, `ele`, `time`).
- Built-in sample routes:
  - `munich`: Munich Olympic Park loop (~4.5 km).
  - `fork`: Route branching corridor for ATT-1955 fork alert verification.
  - `straight`: 2 km high-speed test corridor.

### Step 2: ADB Test Provider Lifecycle Management
- Auto-detect connected ADB device or accept `--device <serial>`.
- Configure test provider:
  ```bash
  adb shell appops set 2000 android:mock_location allow
  adb shell cmd location providers add-test-provider gps --supportsAltitude --supportsSpeed --supportsBearing
  adb shell cmd location providers set-test-provider-enabled gps true
  ```
- Location fix injection:
  ```bash
  adb shell cmd location providers set-test-provider-location gps --location <lat>,<lon> --accuracy <acc> --time <ms>
  ```
- Robust teardown via `atexit` and `signal`:
  ```bash
  adb shell cmd location providers remove-test-provider gps
  ```

### Step 3: Interactive Terminal UI & Hotkey Loop
- Non-blocking single-keypress input handling via `termios` / `select`.
- Real-time ANSI dashboard showing progress bar, speed, distance, lat/lon, heading, and hotkey legend.
- Hotkeys:
  - `[Space]`: Toggle Play / Pause.
  - `[s]`: Stop simulation / rewind.
  - `[1]`, `[2]`, `[5]`, `[0]`: 1x, 2x, 5x, 10x speed multipliers.
  - `[+]` / `[-]`: +/- 5 km/h base speed.
  - `[d]`: Toggle 75m perpendicular deviation off-route.
  - `[r]`: Rejoin route.
  - `[<]` / `[>]`: Scrub backward / forward by 10%.
  - `[q]` / `Ctrl+C`: Clean exit.

### Step 4: Unit Testing & Verification (`tools/test_fake_gps.py`)
- Unit tests covering distance math, bearing, interpolation, deviation offset, GPX parsing, and command generation.
- Full clean-room regression check on app codebase: `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Guardrails
- **Invariant 1**: App codebase (`app/`) remains 100% untouched.
- **Invariant 2**: Clean exit always removes the test provider so hardware GPS returns immediately.
- **Invariant 3**: 100% unit test pass rate across python tests and gradle tests.
