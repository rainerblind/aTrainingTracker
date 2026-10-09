# Stage 2: Requirement & Test Specification - ATT-2467: GPX Replay Mock Ride with Simulated GPS Location for Desk Testing

**Ticket**: [ATT-2467](https://atrainingtracker.atlassian.net/browse/ATT-2467)  
**Sub-task**: [ATT-2934](https://atrainingtracker.atlassian.net/browse/ATT-2934) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Unscheduled` (Developer Testing Tooling)  
**Active Sprint**: `Human Review`  
**Branch**: `feature/ATT-2467`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Test Type | Target File | Status |
|:---|:---|:---|:---|:---|
| `REQ-TOOL-001` Clause 1 | `TST-TOOL-001.1` | Unit Test | `tools/test_fake_gps.py` | Specified |
| `REQ-TOOL-001` Clause 2 | `TST-TOOL-001.2` | Unit Test | `tools/test_fake_gps.py` | Specified |
| `REQ-TOOL-001` Clause 3 | `TST-TOOL-001.3` | Unit Test | `tools/test_fake_gps.py` | Specified |
| `REQ-TOOL-001` Clause 4 | `TST-TOOL-001.4` | Integration Test | `tools/test_fake_gps.py` | Specified |
| `REQ-TOOL-001` Clause 5 | `TST-TOOL-001.5` | System / Regression | `tools/test_fake_gps.py` / Clean-room | Specified |

---

## 2. Test Specifications

### TST-TOOL-001.1: Geodesic Math & Bearing Calculation
- **Objective**: Verify Haversine distance, forward azimuth bearing, and linear interpolation along route polylines.
- **Given**: Two GPS coordinates with known distance (e.g. Munich Marienplatz to Odeonsplatz, 850m).
- **When**: `haversine_distance()` and `calculate_bearing()` are computed.
- **Then**: Distance matches reference within 0.1% margin of error, and bearing accurately evaluates heading across all 4 quadrants ($[0^\circ, 360^\circ)$).

### TST-TOOL-001.2: GPX Parsing & Sample Route Generation
- **Objective**: Verify extraction of trackpoints from GPX XML files and built-in sample routes.
- **Given**: A standard GPX file or sample route identifier (`munich`, `fork`, `straight`).
- **When**: Parsed by `GpxParser`.
- **Then**: Points are extracted with latitude, longitude, and elevation. Empty or malformed tags are handled gracefully.

### TST-TOOL-001.3: Speed Scaling, Scrubbing & Off-Route Deviation
- **Objective**: Verify simulation advancement, speed multipliers, seeking, and perpendicular deviation.
- **Given**: An active simulation state at base speed 25 km/h.
- **When**: Speed multiplier is toggled to 2x, 5x, 10x.
- **Then**: Progression distance scales proportionally.
- **When**: Scrubbing to progress ratio 0.5.
- **Then**: Position immediately snaps to midpoint of the track.
- **When**: Deliberate off-route deviation is toggled on (`[d]`).
- **Then**: Output coordinates shift perpendicularly by 75 meters relative to travel heading.

### TST-TOOL-001.4: ADB Command Generation & Clean Teardown
- **Objective**: Verify ADB shell commands for registering, injecting, and removing the test provider.
- **Given**: A target device serial.
- **When**: Provider initialization, location update, and teardown commands are formatted.
- **Then**: Commands match Android `cmd location providers` syntax, and exit handlers guarantee `remove-test-provider gps` is called on termination.

### TST-TOOL-001.5: Android Clean-Room Regression Guard
- **Objective**: Ensure that 0 files inside `app/` are modified, and full test suite passes with 0 regressions.
- **Given**: Unmodified application codebase.
- **When**: `./gradlew testDebugUnitTest` is executed.
- **Then**: 100% test pass rate with zero regressions.
