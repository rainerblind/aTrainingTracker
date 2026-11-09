# Stage 1 Analysis: ATT-5899 - WorkoutClusterEngine: Discriminate Parallel A->B Corridors & Avoid Apex Degeneracy

**Ticket**: [ATT-5899](https://atrainingtracker.atlassian.net/browse/ATT-5899)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-5899`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **`apex ≈ end` Degeneracy on Point-to-Point (A $\rightarrow$ B) Routes**: Currently, `findApexFromPoints(start, points)` and `TrackerService` define the Apex coordinate (`maxDispLat/Lng`) solely as the point maximizing distance from `start`. On one-way commutes (e.g. Sindelfingen $\rightarrow$ Schönaich in `bike2home_kurz_screen.png`), the point farthest from `start` is `end` itself, causing the blue Apex marker to collapse onto the red End marker.
* **Loss of Corridor Discrimination**: Collapsing `apex` onto `end` renders the 20% Apex similarity term ($s_3$) a duplicate of the End term ($s_2$). Parallel routes (e.g., eastern forest vs. central city, laterally separated by 1.5–2.0 km) share identical start, end, apex ($\approx$ end), distance, and elevation, preventing `suggestCluster` from discriminating between split variants.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: Geodesic distance $d(\text{start}, P)$ increases monotonically along directed A $\rightarrow$ B tracks, peaking at destination B. In `WorkoutClusterEngine.kt` (lines 291–303) and `TrackerService.java` (lines 2758–2771), apex is evaluated solely against `startPos` without endpoint context.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `bike2home_kurz_screen.png` via `read_file`: verified blue distance icon (`ic_distance`) sits directly on top of the red stop icon in Schönaich.
  * Verified mathematical property of confocal ellipses: For foci $A$ and $B$, $d(A, P) + d(P, B) \ge d(A, B)$. For closed loops ($A = B$), $d(A, P) + d(P, A) \equiv 2 \cdot d(A, P)$, preserving existing loop behavior 1:1 while finding the point of maximum lateral deflection on A $\rightarrow$ B routes.
  * Audited `WorkoutClusterCorridorDetector.kt`: station sampling $\{0.15, 0.30, 0.50, 0.70, 0.85\}$ runs in $< 1\text{ ms}$ per candidate and can be leveraged in `scoreClusters` when clusters have `previewPaths`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Generalize `findApexFromPoints(start, points, end = null)` in `WorkoutClusterEngine.kt` to maximize elliptical distance sum $d(\text{start}, P) + d(P, \text{end})$ for point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), with 50% track midpoint fallback for collinear paths.
  * Update `TrackerService.java` (session finalization) to use non-degenerate apex calculation.
  * Enhance `scoreClusters` / `suggestCluster` to accept polyline coordinates/stations and apply corridor distance tie-breaking against cluster `previewPaths` when endpoints match ($s_1, s_2 \le 0.5$).
  * Maintain 100% loop-route (`start ≈ end`) clustering backward compatibility.
* **Out-of-Scope**: Altering global macro-clustering threshold preferences in `ClusterTuningScreen` or re-clustering existing user databases without explicit migration trigger.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-SET-063` (*Workout Cluster Apex Arithmetic Mean & Live-Session Tracking Synchronization*), introducing `REQ-SET-097` (*Point-to-Point Corridor Discrimination & Non-Degenerate Apex Calculation*).
* **Historical Origin & Commit Trace**: Commit `76506c0d` (`ATT-498`, sprint `2026-40.16`).
* **Root Reason for Existing Formulation**: ATT-498 ensured the apex was an authentic on-track coordinate rather than an off-track centroid. Distance from `start` alone was used assuming loop tracks where max displacement occurs midway.
* **Preservation of Core Invariants**: For loops ($d(\text{start}, \text{end}) < 150\text{ m}$), $d(A, P) + d(P, B) = 2 \cdot d(A, P)$ holds strictly, ensuring identical apex coordinates and 100% backward compatibility for all existing loop unit tests.

---

## 5. Architectural Strategy, Solution & Call-Site Audit
* **Non-Degenerate Apex Calculation**:
  * In `WorkoutClusterEngine.findApexFromPoints(start, points, end)`: If `end != null && distanceBetween(start, end) >= 150f`, evaluate $d(\text{start}, P) + d(P, \text{end})$. If max sum exceeds direct distance $d(\text{start}, \text{end}) + 20\text{ m}$, select the maximizing point; otherwise select the track point nearest the 50% distance midpoint.
* **Corridor Discrimination in Scoring**:
  * In `scoreClusters`: When candidate clusters have $s_1 \le 0.5$ and $s_2 \le 0.5$, and member polylines are available, evaluate corridor deviation against cluster `previewPaths` via `WorkoutClusterCorridorDetector.computeCorridorDistance` to rank the true corridor variant first.
* **Call-Site Audit**:
  * Update `TrackerService.java:2758-2771` to invoke `findApexFromPoints(startPos, points, endPos)`.
  * Update `WorkoutClusterEngine.learnFromRoute` and `onWorkoutFinished`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Closed-loop apex and similarity scores remain 100% identical; thread safety under `clusterLock`; zero regressions on existing cluster test suites.
* **Risk Rating**: **MEDIUM** — Core mathematical scoring refinement in engine, mitigated by pure mathematical formulation, backward-compatible loop fallback, and comprehensive targeted unit tests.
