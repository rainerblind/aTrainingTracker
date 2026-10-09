# Stage 1: Problem Domain & Feature Analysis - ATT-2467: GPX Replay Mock Ride with Simulated GPS Location for Desk Testing

**Ticket**: [ATT-2467](https://atrainingtracker.atlassian.net/browse/ATT-2467)  
**Sub-task**: [ATT-2933](https://atrainingtracker.atlassian.net/browse/ATT-2933) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Unscheduled` (Developer Testing Tooling)  
**Active Sprint**: `Human Review`  
**Branch**: `feature/ATT-2467`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

Outdoor sports tracking, turn-by-turn navigation, and route guidance features in **aTrainingTracker** inherently depend on real-time movement through physical space. Key capabilities developed across recent sprints include:
- **Turn-by-Turn Navigation Cues (ATT-1450 / `REQ-MAP-029`)**: Approaching/immediate turn HUD banners, distance countdowns, and directional auditory chimes.
- **Active Route Rendering & Directional Chevrons (ATT-1841 / `REQ-MAP-023`)**: Prominent high-contrast Electric Emerald polyline with forward azimuth chevrons.
- **In-Ride Fork Route Selection & Decision Alerts (ATT-1955 / `REQ-MAP-031`, ATT-2873, ATT-2874)**: Real-time fork detection along common outbound corridors, route divergence warnings, and floating decision cards.
- **Proximity Route Ordering (ATT-2459)**: Sorting stored routes based on distance to athlete's current location.
- **"Take Me Home" Return Navigation (ATT-2462)**: Snapping to home corridors and calculating elevation-aware dynamic ETA.
- **Live Workout Recording (`TrackerService`, `LiveWorkoutSession`)**: 1 Hz GPS trackpoint sampling, speed decay watchdog, and distance accumulation.

### Operational Pain Point & Sprint Bottleneck
During agile Sprint Reviews (Ceremony 2) and daily development at the desk, neither the Product Owner (Rainer Blind) nor developers can verify these features on physical test hardware (Google Pixel 10) without going outside and riding a bike along specific routes. 

Consequently, six tickets were blocked from final acceptance and had to be carried over or placed into deferred review states across multiple sprints (Retro Sprint 2026-40.16, ATT-2389; Sprint 2026-41.5, ATT-2873/ATT-2874).

### Key Architectural Constraint: Zero In-App Modifications
The Product Owner explicitly requested that **no changes be made within the Android app itself**. Instead, an external developer tool (`tools/fake_gps.py`) shall inject mock GPS data from the outside via ADB into Android's native location subsystem, mimicking real-world movement seamlessly without touching production code or requiring third-party mock location apps.

---

## 2. External System Architecture via Android ADB Test Providers

Android provides a native, low-level testing interface within `LocationManagerService` accessible via ADB shell without third-party mock apps:

```text
┌────────────────────────────────────────────────────────┐
│  Host Workstation / CLI (Developer Environment)        │
│  tools/fake_gps.py                                     │
│  - Parses GPX tracks or generates sample test routes   │
│  - Calculates geodesic distance, bearing, interpolation│
│  - Interactive CLI dashboard (curses / ANSI)           │
│  - Controls: Play/Pause, 1x/2x/5x/10x, Deviate, Scrub  │
└──────────────────────────┬─────────────────────────────┘
                           │ adb shell commands via USB
                           ▼
┌────────────────────────────────────────────────────────┐
│  Android OS (Google Pixel 10 / Emulator)               │
│  cmd location providers (UID 2000 shell)               │
│  - appops set 2000 android:mock_location allow         │
│  - add-test-provider gps                               │
│  - set-test-provider-enabled gps true                  │
│  - set-test-provider-location gps --location <lat,lon> │
└──────────────────────────┬─────────────────────────────┘
                           │ System LocationManager broadcasts
                           ▼
┌────────────────────────────────────────────────────────┐
│  aTrainingTracker Application (UNMODIFIED)             │
│  - SpeedAndLocationDevice_GPS (standard GPS listener)  │
│  - FusedLocationProvider                               │
│  - BANALServiceRepository.currentLocation              │
│  - Active Chevrons (ATT-1841)                          │
│  - Turn-by-Turn HUD (ATT-1450)                         │
│  - Fork Alerts (ATT-1955)                              │
└────────────────────────────────────────────────────────┘
```

### Advantages of External Tool Architecture
1. **0% Code Pollution**: Production APK and debug APK remain 100% clean and identical to production releases. No mock code paths, flags, or test bypasses exist in the app.
2. **Native System Transparency**: Because the mock location is injected at the Android framework level into the system `gps` provider, every component in `aTrainingTracker` receives locations exactly as if real satellites were tracking the phone.
3. **No 3rd-Party Mock App Required**: Uses Android's built-in `cmd location providers` command and shell UID 2000 permissions.
4. **Instant Safe Teardown**: Upon exit (`q` or Ctrl+C), `cmd location providers remove-test-provider gps` is executed immediately, returning the device to real hardware GPS automatically.

---

## 3. Tool Functional Requirements

1. **GPX & Track Ingestion**:
   - Parses standard GPX files (`--gpx <path>`).
   - Includes built-in sample routes (`--sample munich`, `--sample fork`, `--sample straight`) enabling instant testing without searching for external files.
2. **Realistic Movement & Geodesics**:
   - Haversine distance calculation and linear interpolation along route polylines.
   - Forward travel bearing computation ($	heta \in [0^\circ, 360^\circ)$).
   - 1 Hz tick cadence matching standard GPS receiver update rates.
3. **Speed Scaling & Interactive Controls**:
   - Dynamic speed multipliers: 1x, 2x, 5x, 10x.
   - Base speed adjustments (+/- 5 km/h).
   - Play/Pause toggle (`[Space]`).
   - Stop & rewind (`[s]`).
   - Scrubbing forward/backward (`[<]` / `[>]`).
4. **Deliberate Off-Route Deviation Mode**:
   - Toggles 75-meter perpendicular deviation (`[d]`).
   - Drives off-route alerts in `aTrainingTracker` (testing ATT-1450 off-route recalculation and ATT-1955 fork divergence).
   - Instant rejoin (`[r]`).
5. **Interactive Real-Time Terminal Dashboard**:
   - Visual progress bar, speed telemetry, elapsed distance/time, current Lat/Lon, and hotkey legend.

---

## 4. ASPICE Traceability & Governance Compliance

- **Requirement**: `REQ-TOOL-001` (*Standalone External ADB GPX Replay & Mock GPS Simulation Tool for Desk Verification*)
- **Test Specification**: `TST-TOOL-001`
- **Impact on App Code**: Zero files modified in `app/`.
- **Governing Epic**: `ATT-2466` (*Developer Testing Tools*).
