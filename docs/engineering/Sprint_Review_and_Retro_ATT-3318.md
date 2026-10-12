# Sprint Review & Retrospective: Sprint 2026-41.10

* **Ticket**: [ATT-3318](https://atrainingtracker.atlassian.net/browse/ATT-3318) (*Review & Retro - Sprint 2026-41.10*)
* **Sprint**: `2026-41.10`
* **Branch**: `sprint/2026-41.10` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-12

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-41.10** (incorporating review deliverables from `2026-41.9`) accomplished substantial architectural consolidation across live polyline tracking geometry, sensor telemetry lifecycle management, cloud storage format parity, and in-ride visual ergonomics.

Key milestones delivered and verified on-device:
1. **Unified Polyline Progress Tracking Engine ([ATT-3266](https://atrainingtracker.atlassian.net/browse/ATT-3266))**:
   - Synthesized disparate geometric tracking loops in `LiveSegmentsRepository` and `LiveClimbsRepository` into a shared, pure Kotlin mathematical engine: [`PolylineProgressTracker.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTracker.kt).
   - Unified directional virtual gates (2D cross-product line crossings), continuous orthogonal segment projection, 3D altitude/grade interpolation, and smooth finish distance blending ($\lambda$-convergence to $0.0\text{ m}$).
2. **Cloud Format-Specific Directory Organization ([ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168))**:
   - Achieved architectural parity between Google Drive and Dropbox by dynamically routing exports and prioritized bulk imports to format-specific subdirectories (`aTrainingTracker/FIT/`, `aTrainingTracker/TCX/`, `aTrainingTracker/GPX/`) while retaining fallback support for legacy flat directories.
3. **Cockpit Telemetry & Sensor Lifecycle Decoupling ([ATT-3222](https://atrainingtracker.atlassian.net/browse/ATT-3222) & [ATT-3220](https://atrainingtracker.atlassian.net/browse/ATT-3220))**:
   - Decoupled rolling smoothing filters (exponential smoothing, moving averages) from `TrainingApplication.isPaused()`, displaying live smoothed power, cadence, and HR during pre-workout warmup while strictly isolating cumulative workout session averages (`AverageFilter`, `MaxValueFilter`).
   - Added persistent suppression for the "Missing ANT+ Adapter" dialog with an Advanced Settings toggle to re-enable warnings.
4. **Follow-Me Speed Zoom & Camera Smoothing Tuning ([ATT-3227](https://atrainingtracker.atlassian.net/browse/ATT-3227) & [ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171))**:
   - Eliminated camera "pumping" oscillations during acceleration/braking via low-pass exponential speed filtering ($\alpha = 0.10f$) and a $0.10f$ zoom hysteresis deadband.
   - Exposed speed and bearing smoothing alphas in Advanced Settings with full 9-language localization parity.
   - Calibrated 3D tilt perspective foreshortening for forward camera lookahead ($56\% \dots 80\%$ viewport height).
5. **Visual Design & Layout Harmonization**:
   - Filtered cycling power formatted as whole rounded integers in Watts ([ATT-3229](https://atrainingtracker.atlassian.net/browse/ATT-3229)), upgrading `IntegerFormatter` with mathematical half-up rounding.
   - Refactored `LiveClimbSheet` to reuse the canonical `MetricBadge` and shared `ElevationProfile` ([ATT-3264](https://atrainingtracker.atlassian.net/browse/ATT-3264)).
   - Rendered full route elevation profiles with map-matched athlete progress markers and categorized climb overlays during active route tracking ([ATT-3199](https://atrainingtracker.atlassian.net/browse/ATT-3199)).
   - Multi-dimensional sensor filtering by wireless protocol, category, and battery level with deep-link navigation from system alerts ([ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)).
   - Clean hardware teardown upon deleting active connected sensors ([ATT-3169](https://atrainingtracker.atlassian.net/browse/ATT-3169)).
   - Top-of-tab anchoring for navigation turn cues and in-ride alerts ([ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)).
   - Decoupled route preview polylines from active navigation styling ([ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172)).

In accordance with **Rule 16 ("Install Before Review")**, the sprint build was compiled from `sprint/2026-41.10` and installed onto the physical Google Pixel 10 prior to evaluation. All sprint tickets were inspected strictly one-by-one in rank order (**Rules 9 & 14**).

---

### 1.1 Review Evaluation Decisions (All 20 Tickets)

| # | Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
| :---: | :--- | :--- | :---: | :---: | :--- |
| 1 | **[ATT-2079](https://atrainingtracker.atlassian.net/browse/ATT-2079)** | Inform Athlete on Process Kill Reasons via ApplicationExitInfo | **Deferred / In Review** | `V4.9.40` | Retained in `Final Review (Human)` per Rule 28 for later field testing. |
| 2 | **[ATT-2192](https://atrainingtracker.atlassian.net/browse/ATT-2192)** | Modernize post-workout low sensor battery warning | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)`. Follow-up **[ATT-3312](https://atrainingtracker.atlassian.net/browse/ATT-3312)** logged for continuous in-workout battery persistence and live list updates. |
| 3 | **[ATT-3056](https://atrainingtracker.atlassian.net/browse/ATT-3056)** | First workout does not immediately appear in periods view | **Deferred / In Review** | `V4.9.40` | Retained in `Final Review (Human)` per Rule 28 for later review. |
| 4 | **[ATT-3177](https://atrainingtracker.atlassian.net/browse/ATT-3177)** | Configure standard tracking tabs with targeted defaults | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)` per Rule 28 for later review. |
| 5 | **[ATT-3165](https://atrainingtracker.atlassian.net/browse/ATT-3165)** | Restore workout recovery dialog visibility & guard unfinalized sessions | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)` per Rule 28 for later review. |
| 6 | **[ATT-3172](https://atrainingtracker.atlassian.net/browse/ATT-3172)** | Route preview on RoutesScreen and RouteDetailSheet hardcodes isActiveNavigation=true | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 7 | **[ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171)** | Scale Follow-Me camera lookahead & compensate tilt foreshortening | **Approved & Erledigt** | `V4.9.39` | Verified and approved ("This seems to be fixed now. Thanks a lot."). |
| 8 | **[ATT-3170](https://atrainingtracker.atlassian.net/browse/ATT-3170)** | Revert ATT-3100: Restore top-of-tab anchoring for navigation hints | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 9 | **[ATT-3169](https://atrainingtracker.atlassian.net/browse/ATT-3169)** | Defensive teardown & filtering for deleted connected sensors | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 10 | **[ATT-3167](https://atrainingtracker.atlassian.net/browse/ATT-3167)** | Navigation bar inset padding for alert snackbars | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)`. Follow-up **[ATT-3313](https://atrainingtracker.atlassian.net/browse/ATT-3313)** logged for start-of-ride and in-ride live low-battery snackbars. |
| 11 | **[ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)** | Filter sensors by protocol, category, battery with deep-link | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3314](https://atrainingtracker.atlassian.net/browse/ATT-3314)** logged for link symbol on equipment matrix tabs. |
| 12 | **[ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)** | Scan Google Drive for historical workout files during bulk import | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)`. Follow-up **[ATT-3315](https://atrainingtracker.atlassian.net/browse/ATT-3315)** logged for external file discovery / SAF picker fallback. |
| 13 | **[ATT-3168](https://atrainingtracker.atlassian.net/browse/ATT-3168)** | Organize Google Drive workouts into format subdirectories (FIT, TCX, GPX) | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 14 | **[ATT-3264](https://atrainingtracker.atlassian.net/browse/ATT-3264)** | Refactor LiveClimbSheet to reuse MetricBadge & ElevationProfile | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3316](https://atrainingtracker.atlassian.net/browse/ATT-3316)** logged for adaptive profile height based on climb height. |
| 15 | **[ATT-3266](https://atrainingtracker.atlassian.net/browse/ATT-3266)** | Unify polyline progress tracking engines (`PolylineProgressTracker`) | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 16 | **[ATT-3220](https://atrainingtracker.atlassian.net/browse/ATT-3220)** | Persistently suppress missing ANT adapter dialog after dismissal | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 17 | **[ATT-3222](https://atrainingtracker.atlassian.net/browse/ATT-3222)** | Enable filtered and smoothed sensor values in cockpit prior to start | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 18 | **[ATT-3227](https://atrainingtracker.atlassian.net/browse/ATT-3227)** | Smooth follow-me speed zoom and calibrate filter alphas in settings | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 19 | **[ATT-3229](https://atrainingtracker.atlassian.net/browse/ATT-3229)** | Format filtered cycling power as rounded integer in Watts | **Approved & Erledigt** | `V4.9.39` | Verified and approved. |
| 20 | **[ATT-3199](https://atrainingtracker.atlassian.net/browse/ATT-3199)** | Display full route elevation profile with position marker & climbs | **Approved & Erledigt** | `V4.9.39` | Verified and approved. Follow-up **[ATT-3317](https://atrainingtracker.atlassian.net/browse/ATT-3317)** logged for configurable per-tab elevation window scope. |

---

### 1.2 Backlog Follow-Up Tickets Created During Joint Review

In strict compliance with **"No code changes during Sprint Review & Lean Defect Recording"** (Rules 11 & 17), all human observations and design refinements were captured into structured backlog tickets ranked at the top of the backlog:

1. **[ATT-3317](https://atrainingtracker.atlassian.net/browse/ATT-3317)**: `Configure route elevation profile window scope and rolling lookahead distance individually per tracking tab` (Rank 1).
2. **[ATT-3316](https://atrainingtracker.atlassian.net/browse/ATT-3316)**: `Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain` (Rank 2).
3. **[ATT-3315](https://atrainingtracker.atlassian.net/browse/ATT-3315)**: `Enable import of externally copied workout files from Google Drive with file picker fallback` (Rank 3).
4. **[ATT-3314](https://atrainingtracker.atlassian.net/browse/ATT-3314)**: `Display link icon on equipment matrix tabs in Sensors screen to clarify sensor assignment` (Rank 4).
5. **[ATT-3313](https://atrainingtracker.atlassian.net/browse/ATT-3313)**: `Display low sensor battery alert snackbars on workout start and during in-ride tracking` (Rank 5).
6. **[ATT-3312](https://atrainingtracker.atlassian.net/browse/ATT-3312)**: `Persist sensor battery state continuously during workout and reflect live battery levels in sensors list` (Rank 6).

---

## 2. Topic 1 Deep Dive: Unified Polyline Progress Tracking & Directional Virtual Gates (ATT-3266)

### 2.1 The Issue & Architectural Divergence
For multiple release cycles, live tracking along polyline geometries was maintained in two parallel repositories:
1. `LiveSegmentsRepository.kt`: Used discrete nearest-vertex snapping (`Location.distanceTo(closestPoint)`), non-directional radial proximity gating ($< 25\text{m}$), and raw odometer subtraction ($\text{dist} - \text{startDist}$). This caused false starts when crossing parallel trails or switchbacks in reverse, distance drift from GPS noise, and abrupt distance jumping at segment finishes.
2. `LiveClimbsRepository.kt`: Used continuous orthogonal segment projection, but inlined its vector math locally without sharing it, and used simple radial circles rather than directional gates.

### 2.2 Mathematical Formulation of the Unified Engine
In [`PolylineProgressTracker.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/geo/PolylineProgressTracker.kt), three mathematical foundations were unified:

1. **Directional Virtual Gates (2D Vector Cross-Product Sign Flip)**:
   A virtual line segment $\overline{G_1 G_2}$ perpendicular to the track heading $\vec{v} = P_1 - P_0$ is evaluated using oriented 2D cross-products:
   $$\text{cross}(A, B, C) = (B_{\text{lat}} - A_{\text{lat}})(C_{\text{lng}} - A_{\text{lng}}) - (B_{\text{lng}} - A_{\text{lng}})(C_{\text{lat}} - A_{\text{lat}})$$
   A crossing is recognized if and only if the sign flips between successive GPS points ($\text{sign}(C_{\text{curr}}) \ne \text{sign}(C_{\text{prev}})$) and matches the forward reference sign, guaranteeing the athlete crosses in the correct forward direction.

2. **Continuous Orthogonal Segment Projection**:
   For athlete location $P$ and nearest line segment $\overline{A B}$:
   $$t = \text{clamp}\left(\frac{(P - A) \cdot (B - A)}{\|B - A\|^2}, 0.0, 1.0\right)$$
   Interpolated along-track distance and altitude:
   $$D_{\text{along}} = D_A + t \cdot (D_B - D_A)$$
   $$\text{Alt}_{\text{proj}} = \text{Alt}_A + t \cdot (\text{Alt}_B - \text{Alt}_A)$$
   $$\text{Grade} = \frac{\Delta \text{Alt}}{\Delta D} \times 100\%$$

3. **Smooth Finish Line Convergence**:
   When within finish threshold $D_{\text{blend}} = 50\text{m}$, remaining distance blends with direct geodesic distance to the finish line:
   $$\lambda = \min\left(1.0, \frac{D_{\text{rem, proj}}}{D_{\text{blend}}}\right)$$
   $$D_{\text{remaining}} = \lambda \cdot D_{\text{rem, proj}} + (1 - \lambda) \cdot \text{distToFinish}$$
   Guarantees that $D_{\text{remaining}}$ converges smoothly to exactly $0.0\text{m}$ at the finish line without discrete jumpiness.

---

## 3. Topic 2 Deep Dive: In-Ride Sensor Lifecycle & Pre-Start Smoothing Availability (ATT-3222 & ATT-3220)

### 3.1 The Problem
In earlier builds, rolling smoothing filters (`ExponentialSmoothingFilter`, `TimedMovingAverageFilter`, `NumberedMovingAverageFilter`) suppressed evaluation whenever `TrainingApplication.isPaused()` was true. Because `isPaused()` was defined as `cTrackingMode != TrackingMode.TRACKING`, tiles displayed dashes (`--`) before pressing "Start", preventing athletes from checking smoothed 3s power, smoothed heart rate, or slope percentage during warm-up.

### 3.2 Architectural Solution
1. **Decoupled Rolling Smoothing**: Rolling display filters now update continuously on valid input samples regardless of pause state.
2. **Strict Session Boundary Isolation**: In contrast, workout session accumulators (`AverageFilter`, `MaxValueFilter`) retain their `if (isPaused()) return;` guard, ensuring pre-start stationary sensor data never corrupts recorded workout averages or maxima.
3. **Filter Reset Protocol**: All filters implement `reset()`, invoked via `RESET_ACCUMULATORS_INTENT` when a workout recording starts.
4. **Persistent Dialog Suppression**: ANT+ missing adapter warnings are stored persistently in DataStore once dismissed, with an explicit recovery toggle in Advanced Tuning.

---

## 4. Topic 3 Deep Dive: Adaptive Altitude Scaling vs. Hardcoded Compact Heights (ATT-3264 & ATT-3199)

### 4.1 The Issue
During refactoring of `LiveClimbSheet.kt` to reuse `ElevationProfile.kt`, a compact height of `fixedHeight = 68.dp` was retained from the legacy `ClimbProfileCanvas`. During Joint Review, the athlete mandated:
*"The height of the elevation profile should also depend on the height of the climb. No exceptions here."*

### 4.2 Forensic Analysis & Countermeasure (Rule 36)
Hardcoding fixed heights for elevation profiles breaks the app's visual language established in Design Guidelines §2.3. Steep climbs and mountain passes (e.g. 800 m gain) appear visually identical to minor rolling hills (e.g. 20 m gain) when constrained to a flat 68 dp canvas.
- **Rule 36**: Elevation profiles must always compute their height dynamically based on the total elevation gain/span $\Delta H = H_{\max} - H_{\min}$ using canonical domain formulas (`calculateElevationProfileHeight(range)`). Hardcoding fixed heights (e.g. `68.dp`) in bottom sheets or dialogs is prohibited.
- Logged follow-up **[ATT-3316](https://atrainingtracker.atlassian.net/browse/ATT-3316)** to implement adaptive elevation gain scaling for `LiveClimbSheet`.

---

## 5. Topic 4 Deep Dive: Cloud File Visibility Under Least-Privilege OAuth Scopes (ATT-2346 & ATT-3168)

### 5.1 The Issue
During review, historical workout files copied manually into Google Drive's `TCX` folder from an external source (Dropbox) were not discovered or imported by the app's Google Drive scan.

### 5.2 Forensic Root Cause
To comply with Google Play Store security policies and avoid broad storage permissions, the app uses the least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file`. Under `drive.file`, Google Drive API queries **strictly return files and folders created or opened by the app itself**. Files copied into Google Drive via desktop sync or web browser are completely invisible to queries under `drive.file`.

### 5.3 Countermeasure (Rule 37)
- The app must not assume that cloud directory scans under `drive.file` will see externally uploaded files.
- To allow users to import external archives, the app must provide a Storage Access Framework (SAF) / Drive file picker fallback that allows users to explicitly select external files or folders, thereby granting access under `drive.file`.
- Logged follow-up **[ATT-3315](https://atrainingtracker.atlassian.net/browse/ATT-3315)**.

---

## 6. Topic 5 Deep Dive: Multi-Scale Window Scoping for Navigation Elevation Profiles (ATT-3199)

### 6.1 The Issue
`ATT-3199` delivered full route elevation profile rendering during active navigation. While effective for route overview, on long routes (e.g. 60–150 km), rendering the entire route horizontally compresses immediate terrain features, making upcoming small climbs and steep gradients imperceptible.

### 6.2 Countermeasure (Rule 38)
Athletes require multi-scale scoping tailored to their active tracking screen context:
- **Macro View (Full Route)**: Shows total route profile from $0\text{ km}$ to finish with current progress indicator (ideal for Map overview tabs).
- **Micro View (Rolling Lookahead Window)**: Shows a focused rolling window (e.g., next 5 km / 10 km) centered around the athlete with customizable lookahead distance (ideal for Cockpit / Climb tracking tabs).
- Must be configurable individually per tracking tab in `TrackingViewsDatabaseManager`.
- Logged follow-up **[ATT-3317](https://atrainingtracker.atlassian.net/browse/ATT-3317)**.

---

## 7. Retrospective Process Hardening & Governance Additions

### 7.1 New ASPICE Governance Rules (`.agents/rules/aspice_governance.md`)

* **Rule 36: Dynamic Elevation Profile Sizing ("No Fixed Heights for Elevation Profiles")**:
  All elevation profiles across tracking HUDs, bottom sheets, and route inspection views must compute their height dynamically based on the total elevation gain ($\Delta H = H_{\max} - H_{\min}$) using canonical domain algorithms (`calculateElevationProfileHeight(range)`). Hardcoding arbitrary fixed heights (e.g. `68.dp`) is strictly prohibited.
  *Origin*: Sprint 2026-41.10, ATT-3264 / ATT-3316.

* **Rule 37: Cloud Access Boundary Defense Under `drive.file` Least-Privilege Scope**:
  When integrating cloud storage APIs with restricted scopes (such as Google Drive `drive.file`), background scanning engines must account for the invariant that externally uploaded files are invisible to direct API queries. Features intended for historical recovery or bulk migration must provide a Storage Access Framework (SAF) or document picker fallback to allow athletes to explicitly grant file access.
  *Origin*: Sprint 2026-41.10, ATT-2346 / ATT-3315.

* **Rule 38: Multi-Scale Window Scoping for Route Elevation Analytics**:
  Navigation elevation tracking must support multi-scale scoping (full-route macro overview vs. rolling lookahead micro window) configurable individually per tracking tab to prevent terrain feature compression on long routes.
  *Origin*: Sprint 2026-41.10, ATT-3199 / ATT-3317.

---
