# Stage 5 Verification & Walkthrough - ATT-2467: GPX Replay Mock Ride with Simulated GPS Location for Desk Testing

**Ticket**: [ATT-2467](https://atrainingtracker.atlassian.net/browse/ATT-2467)  
**Sub-task**: [ATT-2937](https://atrainingtracker.atlassian.net/browse/ATT-2937) (`[Test]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Unscheduled` (Developer Testing Tooling)  
**Active Sprint**: `Human Review`  
**Branch**: `feature/ATT-2467`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

To enable thorough desk verification of navigation features during the agile Sprint Review ceremony (Ceremony 2), ticket **ATT-2467** delivers a standalone external developer simulation CLI tool: `tools/fake_gps.py`.

Per Product Owner directive, **zero modifications were made to the Android application source code (`app/`)**. Instead, the tool streams high-precision simulated GPS fixes from any GPX route directly into the connected physical Google Pixel 10 (or emulator) via ADB's native `cmd location providers` test provider interface.

### Unblocked Verification Reviews
With `tools/fake_gps.py`, the following sprint backlog review items can now be verified at the desk without riding outdoors:
1. **ATT-1841 / REQ-MAP-023**: Active Route Rendering & Directional Chevrons on Map.
2. **ATT-1450 / REQ-MAP-029**: Turn-by-Turn Navigation Cues & Distance Countdown HUD.
3. **ATT-1955 / REQ-MAP-031, ATT-2873, ATT-2874**: In-Ride Fork Route Selection & Decision Alerts.
4. **ATT-2462**: "Take Me Home" Return Navigation.

---

## 2. Capabilities & Interactive Controls

The tool features an interactive, real-time terminal dashboard with non-blocking single-keypress hotkeys:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                ATT-2467: GPX REPLAY MOCK RIDE SIMULATOR                │
├────────────────────────────────────────────────────────────────────────┤
│ Route:   Munich Olympic Park Scenic Loop                               │
│ Device:  66020DLCR002FL  |  Status: [PLAYING]  [ON ROUTE]              │
├────────────────────────────────────────────────────────────────────────┤
│ Coords:   48.173294,  11.546423   |  Bearing:   41.1° (NE)             │
│ Speed:     25.0 km/h (1.0x)       |  Altitude: 510.0 m                 │
│ Distance:  0.02 /  4.82 km        |  Elapsed:  00:03                   │
│ Progress: [██░░░░░░░░░░░░░░░░░░░░]   0.4%                              │
├────────────────────────────────────────────────────────────────────────┤
│ Hotkeys: [Space] Play/Pause  | [1/2/5/0] 1x/2x/5x/10x  | [+/-] Speed   │
│          [d] Deviate Off-Route | [r] Rejoin Route      | [</>] Scrub   │
│          [s] Stop / Reset      | [q] Quit & Clean Exit                 │
└────────────────────────────────────────────────────────────────────────┘
```

### Hotkey Reference
| Key | Action | Description |
|:---|:---|:---|
| `[Space]` | **Play / Pause** | Toggles between active playback and stationary pause. |
| `[1]` / `[2]` / `[5]` / `[0]` | **Speed Multiplier** | Sets speed multiplier to $1\times, 2\times, 5\times, 10\times$ (e.g. $25\text{ km/h} \to 125\text{ km/h}$). |
| `[+]` / `[-]` | **Adjust Speed** | Adjusts base speed in increments of $\pm 5\text{ km/h}$. |
| `[d]` | **Deviate Off-Route** | Shifts injected coordinates $75\text{ m}$ perpendicular to travel heading, triggering off-route warnings. |
| `[r]` | **Rejoin Route** | Snaps coordinates back to the polyline corridor. |
| `[<]` / `[>]` | **Scrub / Seek** | Jumps backward / forward along the route by 10% distance increments. |
| `[s]` | **Stop / Rewind** | Rewinds progress to $0.0\text{ km}$ and pauses. |
| `[q]` / `Ctrl+C` | **Safe Exit** | Automatically tears down test provider and restores real hardware GPS. |

---

## 3. How to Use (Product Owner Quickstart)

### Step 1: Connect Your Google Pixel 10 via USB
Ensure USB debugging is enabled on the phone. Verify connection:
```bash
adb devices
# Output: 66020DLCR002FL device
```

### Step 2: Launch the Simulator
To replay the built-in Munich Olympic Park route:
```bash
python3 tools/fake_gps.py --sample munich
```

To replay the Isar fork route (specifically tailored for ATT-1955 fork alert review):
```bash
python3 tools/fake_gps.py --sample fork
```

To replay an external GPX file:
```bash
python3 tools/fake_gps.py --gpx /path/to/my_ride.gpx --speed 28
```

### Step 3: Observe in aTrainingTracker
Launch **aTrainingTracker** on the Pixel 10:
- Open Cockpit Tracking or Map views.
- The phone receives moving GPS location fixes identical to riding outdoors.
- Active chevrons, turn cues, and fork alerts react dynamically to the simulation!

---

## 4. Verification & Clean-Room Regression Results

1. **Python Unit Tests (`tools/test_fake_gps.py`)**:
   - Geodesic distance (Haversine): PASSED
   - Cardinal forward bearing math: PASSED
   - Off-route perpendicular displacement: PASSED
   - GPX XML trackpoint parsing: PASSED
   - Simulation state engine & speed scaling: PASSED
   - Result: **13 of 13 tests passed (100% success)**.

2. **On-Device Live Verification (Google Pixel 10 `66020DLCR002FL`)**:
   - Shell UID 2000 mock location appops granted: PASSED
   - Test provider registration (`gps`): PASSED
   - Live location fix injection at 1 Hz: PASSED
   - Safe lifecycle teardown (`remove-test-provider`): PASSED

3. **Android Clean-Room Regression (`./gradlew testDebugUnitTest`)**:
   - Verified 100% test pass rate with zero code modifications to `app/`.
