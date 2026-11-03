# Sprint Review & Retrospective: Sprint 2026-42.1

* **Ticket**: [ATT-3432](https://atrainingtracker.atlassian.net/browse/ATT-3432) (*Review & Retro - Sprint 2026-42.1*)
* **Sprint**: `2026-42.1`
* **Branch**: `sprint/2026-42.1` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-13

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-42.1** delivered major architectural milestones across navigation prompt rendering, route clustering governance, live climb and segment cockpit ergonomics, real-time sensor battery telemetry, and the canonical restructuring of engineering standards into the **Document Triad**.

In accordance with **Rule 10 ("Install Before Review")**, the review ceremony commenced by compiling the latest integrated build from `sprint/2026-42.1` and deploying it directly to the attached physical Google Pixel 10 test hardware via `./gradlew installDebug`. All sprint backlog tickets were evaluated strictly one-by-one in backlog rank order (**Rules 7 & 10**).

---

### 1.1 Review Evaluation Decisions (All 26 Tickets)

| # | Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
| :---: | :--- | :--- | :---: | :---: | :--- |
| 1 | **[ATT-3277](https://atrainingtracker.atlassian.net/browse/ATT-3277)** | Use favorite route icon for clusters in workout summary and edit views | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Disambiguates route clusters (`ic_favorite_route`) from point pins (`my_locations`). |
| 2 | **[ATT-3319](https://atrainingtracker.atlassian.net/browse/ATT-3319)** | Ensure Segment and Climb bottom sheets always pop up to full height on new active entity | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Bottom sheets reliably expand to full height when new entity activates. |
| 3 | **[ATT-3320](https://atrainingtracker.atlassian.net/browse/ATT-3320)** | Configurable auto-switch to unique route in advanced navigation settings | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Added user toggle in Advanced Settings with 9-language localization. |
| 4 | **[ATT-3321](https://atrainingtracker.atlassian.net/browse/ATT-3321)** | Expose turn cue countdown distance slider in advanced navigation settings | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Interactive slider (30m–300m) with 10m steps and persistence. |
| 5 | **[ATT-3322](https://atrainingtracker.atlassian.net/browse/ATT-3322)** | Make navigation hints and fork info opaque and remove transparency setting | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Overlays rendered solid 100% opaque for daylight readability; transparency slider removed. |
| 6 | **[ATT-3324](https://atrainingtracker.atlassian.net/browse/ATT-3324)** | Use numeric distance values instead of formatted strings when prioritizing active live segments | **Approved (i.O.)** | `V4.9.39` | Verified by code audit & tests. Fixed lexicographical string sorting bug at 1km boundary. |
| 7 | **[ATT-3325](https://atrainingtracker.atlassian.net/browse/ATT-3325)** | Replace persistent option with 10s option for navigation cue dismiss duration | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Follow-up **[ATT-3450](https://atrainingtracker.atlassian.net/browse/ATT-3450)** logged to replace chips with 1s–10s slider to prevent row wrap. |
| 8 | **[ATT-3326](https://atrainingtracker.atlassian.net/browse/ATT-3326)** | Automatically unselect route when user is no longer on an automatically selected route | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Distinguishes auto-selected vs manually chosen routes; clears auto-selected on off-route. |
| 9 | **[ATT-3310](https://atrainingtracker.atlassian.net/browse/ATT-3310)** | Configure default sensor data filtering on initial install | **Deferred / In Review** | — | Retained in `Final Review (Human)` for later review. |
| 10 | **[ATT-3317](https://atrainingtracker.atlassian.net/browse/ATT-3317)** | Configure route elevation profile window scope and rolling lookahead distance individually per tracking tab | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Per-tab full route vs rolling lookahead window with 15% rider centering. |
| 11 | **[ATT-3316](https://atrainingtracker.atlassian.net/browse/ATT-3316)** | Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Removed fixed 68dp box; profile height scales monotonically 70dp–200dp with climb relief. |
| 12 | **[ATT-3315](https://atrainingtracker.atlassian.net/browse/ATT-3315)** | Enable import of externally copied workout files from Google Drive with file picker fallback | **Rejected (n.i.O.)** | — | Returned to `Analysis`. User intended direct automated Drive scan discovery, not file picker fallback. |
| 13 | **[ATT-3314](https://atrainingtracker.atlassian.net/browse/ATT-3314)** | Display link icon on equipment matrix tabs in Sensors screen to clarify sensor assignment | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Added link icon to Räder / Schuhe matrix tabs. |
| 14 | **[ATT-3312](https://atrainingtracker.atlassian.net/browse/ATT-3312)** | Persist sensor battery state continuously during workout and reflect live battery levels in sensors list | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Continuous session tracking in `TrackerService` and live BLE GATT battery notifications. |
| 15 | **[ATT-3313](https://atrainingtracker.atlassian.net/browse/ATT-3313)** | Display low sensor battery alert snackbars on workout start and during in-ride tracking | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Follow-up **[ATT-3451](https://atrainingtracker.atlassian.net/browse/ATT-3451)** logged to use two-level warning colors (amber/red) instead of blue. |
| 16 | **[ATT-3323](https://atrainingtracker.atlassian.net/browse/ATT-3323)** | Separate ASPICE governance rules from UI design guidelines and technical coding standards | **Approved (i.O.)** | `V4.9.39` | Decoupled monolithic 36 rules into canonical Document Triad (`aspice_governance.md`, `design_guidelines.md`, `coding_standards.md`). |
| 17 | **[ATT-2079](https://atrainingtracker.atlassian.net/browse/ATT-2079)** | Inform Athlete on Process Kill Reasons via ApplicationExitInfo | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)` for later review. |
| 18 | **[ATT-2346](https://atrainingtracker.atlassian.net/browse/ATT-2346)** | Scan Google Drive for historical workout files during bulk import | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)` pending completion of ATT-3315. |
| 19 | **[ATT-3167](https://atrainingtracker.atlassian.net/browse/ATT-3167)** | Post-workout low battery snackbar is occluded by Android system navigation bar | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Added `navigationBarsPadding()` on `SnackbarHost` across root scaffolds. |
| 20 | **[ATT-2192](https://atrainingtracker.atlassian.net/browse/ATT-2192)** | Modernize post-workout low sensor battery warning with system notification and in-app snackbar | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Eliminated modal AlertDialog; dual-channel notification and snackbar. |
| 21 | **[ATT-3165](https://atrainingtracker.atlassian.net/browse/ATT-3165)** | Restore workout recovery dialog visibility and guard unfinalized sessions on tracking start | **Deferred / In Review** | `V4.9.39` | Retained in `Final Review (Human)` for further manual testing. |
| 22 | **[ATT-3377](https://atrainingtracker.atlassian.net/browse/ATT-3377)** | Support simulated exit reason injection and PyQt6 desktop GUI for process kill simulator | **Approved (i.O.)** | `V4.9.39` | Verified. Debug injection channel in `ProcessExitReasonHelper` and native PyQt6 desktop tool `simulate_kill.py --gui`. |
| 23 | **[ATT-3344](https://atrainingtracker.atlassian.net/browse/ATT-3344)** | Migrate Advanced Settings to full-screen screen and optimize category structure | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Full-screen Scaffold eliminates accidental swipe dismissal; Navigation promoted to top. |
| 24 | **[ATT-3355](https://atrainingtracker.atlassian.net/browse/ATT-3355)** | Move workout route clustering parameters to Advanced Settings | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Follow-up **[ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)** logged to remove tuning from favorite routes and add recalculation with confirmation to Advanced Settings. |
| 25 | **[ATT-3343](https://atrainingtracker.atlassian.net/browse/ATT-3343)** | Ensure tracking map fills all remaining vertical space above elevation profile | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Map expands to fill 100% remaining space. Follow-up bug **[ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)** logged for cut climb marker badge. |
| 26 | **[ATT-3341](https://atrainingtracker.atlassian.net/browse/ATT-3341)** | Reopen dismissed LiveSegment and Climb bottom sheets on bottom swipe-up gesture | **Approved (i.O.)** | `V4.9.39` | Verified on-device. Peek tap-to-expand and bottom swipe-up gesture re-expands sheets. |

---

### 1.2 Backlog Follow-Up Tickets Created During Joint Review

In strict compliance with **Rules 8 & 9 ("No code changes during Sprint Review & Lean Defect Recording")**, immediate on-device evidence was captured via `adb exec-out screencap -p` and attached to 4 new structured backlog tickets ranked at the top of the backlog:

1. **[ATT-3450](https://atrainingtracker.atlassian.net/browse/ATT-3450)**: `Replace navigation cue dismiss duration chips with integer slider from 1s to 10s` (Rank 1).
   - Replaces the 6 `FilterChip` items with a continuous integer `TuningSliderItem` (1s–10s, default 4s) to eliminate the orphan 2nd row on mobile displays.
2. **[ATT-3451](https://atrainingtracker.atlassian.net/browse/ATT-3451)**: `Use warning colors instead of blue for sensor low battery snackbar alerts` (Rank 2).
   - Introduces two-level warning styling: Warning ($\le 20\%$, amber/yellow container) and Critical ($\le 10\%$, red/error container), eliminating primary blue for alerts.
3. **[ATT-3452](https://atrainingtracker.atlassian.net/browse/ATT-3452)**: `Remove cluster tuning from favorite routes and add recalculate action with confirmation to Advanced Settings` (Rank 3).
   - Excises the legacy tuning dialog from `WorkoutClustersScreen`, and adds the recalculate action directly to `ClusterTuningSection.kt` behind an explicit warning/confirmation dialog.
4. **[ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)**: `[Bug] Prevent clipping of climb category marker badges in elevation profile` (Rank 4).
   - Resolves top/boundary canvas clipping for climb category badges (e.g. UC, CAT 1–4) within `ElevationProfile.kt`.

---

## 2. Topic 1 Deep Dive: Document Triad Harmonization — "Reuse Before Create"

### 2.1 The Issue
During Sprint 2026-42.1 (ATT-3323), monolithic ASPICE governance rules were decoupled into the Document Triad:
1. Process Governance (`.agents/rules/aspice_governance.md`)
2. UI & Interaction Design Guidelines (`docs/design_guidelines.md`)
3. Technical Architecture & Coding Standards (`docs/coding_standards.md`)

When transferring legacy Rule 23 into `docs/design_guidelines.md`, Section 5.1 was titled *"Reuse Before Create"*.
The human user observed during the review:
> *"Why is the 'Reuse before create' rule in the UI part and not in the technical architecture & coding standards?"*

### 2.2 Root Cause & Architectural Insight
"Reuse Before Create" is fundamentally a foundational software engineering principle (DRY, Single Source of Truth, modular architecture) that applies across all code layers (repositories, utilities, formatters, domain engines). Housing that broad heading exclusively within UI guidelines inadvertently implied that code reuse was purely a visual or aesthetic styling concern.

### 2.3 Permanent Countermeasure (Codified in Document Triad)
1. **`docs/coding_standards.md` §1.1**:
   - Elevated and retitled to: **"1.1 Reuse Before Create & DRY (Shared Code, Utilities & Architecture Over Duplication)"**.
   - Anchors technical reuse across repositories, formatters, unit math conversion functions, and domain calculators as a non-negotiable coding invariant.
2. **`docs/design_guidelines.md` §5.1**:
   - Retitled to: **"5.1 Reuse Existing UI Components & Visual Patterns (Visual Baseline)"**.
   - Scopes this section explicitly to UI widgets, Material 3 theme tokens, shapes, spacing scales, and layout patterns.

---

## 3. Topic 2 Deep Dive: Numeric vs. String Sorting in Live Tracking (ATT-3324)

### 3.1 Forensic Analysis
When multiple Strava Live Segments overlap on the same road (e.g. a 2.3 km segment and a 3.7 km segment starting at the same junction), `TrackingViewModel` sorted candidates using `thenBy { it.liveData.remainingDistance }`.
`remainingDistance` was formatted as a display `String`.
At $\ge 1,000\text{ m}$, `DistanceFormatter` output `"2.40 km"`. At $< 1,000\text{ m}$, it output `"995 m"`.
Because Kotlin's `String.compareTo()` compares ASCII character values, `'2'` (ASCII 50) is smaller than `'9'` (ASCII 57). As a result, `"2.40 km" < "995 m"` evaluated to `true`, causing the active cockpit segment card to abruptly flip away to the longer climb the instant distance dropped below 1 km.

### 3.2 Countermeasure
Codified in `docs/coding_standards.md` §1.3:
* **Prohibition of Comparator Sorting on Formatted Strings**: Sorting, ranking, and priority comparisons across domain models and ViewModels **MUST** evaluate raw numeric primitives (`Double`, `Long`, `Int`), never human-readable formatted display strings.

---

## 4. Topic 3 Deep Dive: Google Drive API `drive.file` Scope vs Automated Scanning (ATT-3315)

### 4.1 Root Cause of Rejection
In ATT-3315, the automated Google Drive scan failed to discover workout files copied into Drive externally from desktop or web interfaces. The ticket implemented a fallback to Android's multi-document file picker (`ActivityResultContracts.OpenMultipleDocuments()`).
During review, the user rejected this approach (n.i.O.), clarifying that the **"Google Drive scannen"** action itself must be able to discover and import the files directly without delegating to a manual file picker.

### 4.2 Engineering Path Forward for ATT-3315 in Analysis
1. Re-evaluate OAuth scope permissions: Google Drive's least-privilege `drive.file` scope intentionally isolates files not created by the app itself. Direct automated scanning across externally placed files requires investigating Google Drive REST API read-only folder access, OAuth consent implications, or application-designated folder sync.
2. Returned ATT-3315 to `Analysis` stage for systematic requirement re-formulation.

---

## 5. Process & Rulebook Hardening Commitments

The following permanent updates are committed to the Document Triad on `sprint/2026-42.1`:
1. **`docs/coding_standards.md`**:
   - §1.1 elevated to *"Reuse Before Create & DRY (Shared Code, Utilities & Architecture Over Duplication)"*.
   - §1.3 added: *"Prohibition of Sorting / Comparing on Formatted Display Strings"*.
   - §1.4 added: *"Confirmation Dialog Invariant for Destructive or Cache-Invalidating Re-computations"*.
2. **`docs/design_guidelines.md`**:
   - §5.1 retitled to *"Reuse Existing UI Components & Visual Patterns (Visual Baseline)"*.
   - §1.4 added: *"Prefer Discrete Sliders Over Multi-Row Chip Lists for Large Scalar Value Sets"*.
   - §2.4 added: *"Canvas Boundary Clearance for Elevation Profile Marker Badges"*.
   - §5.4 updated: *"Dedicated Warning Semantic Tiers (Amber/Red vs Primary Blue)"*.

---

## 6. Sprint Sign-Off & Integration

With all 26 tickets inspected, 21 approved, 1 returned to Analysis, 4 postponed for later review, 4 follow-up tickets ranked at the top of the backlog, and the Retrospective complete, Sprint **2026-42.1** is ready for final merge into `develop`.
