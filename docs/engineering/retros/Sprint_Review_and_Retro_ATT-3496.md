# Sprint Review & Retrospective: Sprint 2026-42.3

* **Ticket**: [ATT-3496](https://atrainingtracker.atlassian.net/browse/ATT-3496) (*Review & Retro - Sprint 2026-42.3*)
* **Sprint**: `2026-42.3`
* **Branch**: `sprint/2026-42.3` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-14

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-42.3** focused on navigation curvature intelligence, vector iconography, data sovereignty & background automation stabilization, least-privilege cloud hygiene, bulk storage framework import, and deep architectural refactorings enforcing **Rule 20 (Database & DTO Mapping Symmetry)** and **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)** across formatters, parsers, and repositories.

In accordance with **Rule 10 ("Install Before Review")**, the review ceremony commenced by compiling the latest integrated build from `sprint/2026-42.3` and deploying it directly to the attached physical Google Pixel 10 test hardware (`66020DLCR002FL`) via `./gradlew installDebug`. All sprint tickets in `Final Review (Human)` were evaluated strictly one-by-one in backlog rank order in accordance with **Rules 7 & 10 ("Single-Ticket Focus & Strict Rank Order")**.

---

### 1.1 Review Evaluation Decisions (13 Evaluated Sprint Tickets)

| # | Ticket | Summary | Review Result | Status & Follow-up Actions |
| :---: | :--- | :--- | :---: | :--- |
| 1 | **[ATT-3494](https://atrainingtracker.atlassian.net/browse/ATT-3494)** | Improve turn cue detection algorithm and curvature classification along route polylines | **Retained in Review** | Kept in `Final Review (Human)` for field ride verification. |
| 2 | **[ATT-3493](https://atrainingtracker.atlassian.net/browse/ATT-3493)** | Improve vector icons for sharp right and sharp left turn navigation cues | **Approved with Follow-Up** | Human observation recorded: sharp turns visually resemble U-turns due to 180° loop arch. Follow-up ticket **ATT-3573** created in backlog. Ticket marked `Erledigt`. |
| 3 | **[ATT-3492](https://atrainingtracker.atlassian.net/browse/ATT-3492)** | Style cluster recalculate button in red and add direct Advanced Settings deep-link to Favorite Routes info | **Approved (i.O.)** | Transitioned to `Erledigt`. Verified error color tokens on recalculate button and direct deep-link from Favorite Routes info dialog to Advanced Settings. |
| 4 | **[ATT-3472](https://atrainingtracker.atlassian.net/browse/ATT-3472)** | Fix automated background backup execution and WorkManager scheduling | **Retained in Review** | Kept in `Final Review (Human)` for long-term timer and local backup verification. |
| 5 | **[ATT-3478](https://atrainingtracker.atlassian.net/browse/ATT-3478)** | Fix automated background synchronization for Strava segments, routes and equipment | **Retained in Review** | Kept in `Final Review (Human)` for scheduled Strava sync observation. |
| 6 | **[ATT-3489](https://atrainingtracker.atlassian.net/browse/ATT-3489)** | Remove Google Drive scan option across all workout format import cards | **Approved (i.O.)** | Transitioned to `Erledigt`. Verified clean removal of Google Drive scan buttons across FIT, TCX, and GPX cards, removing sensitive scope dependencies. |
| 7 | **[ATT-3491](https://atrainingtracker.atlassian.net/browse/ATT-3491)** | Recursive directory workout file discovery and bulk import via Storage Access Framework | **Approved (i.O.)** | Transitioned to `Erledigt`. Verified platform-native SAF recursive tree traversal (`OpenDocumentTree`) with duplicate skipping and period reconciliation. |
| 8 | **[ATT-3361](https://atrainingtracker.atlassian.net/browse/ATT-3361)** | Replace hardcoded distance and altitude strings with canonical formatters | **Approved (i.O.)** | Transitioned to `Erledigt`. Centralized all distance and elevation formatting under `DistanceFormatter` and `AltitudeFormatter`, restoring Imperial unit support across navigation cards. |
| 9 | **[ATT-3362](https://atrainingtracker.atlassian.net/browse/ATT-3362)** | Consolidate duplicate TCX and GPX trackpoint parsing into shared XML utility | **Approved (i.O.)** | Transitioned to `Erledigt`. Consolidated 4 disparate XML parsers into canonical streaming engines in `routes.parsers`, excising external library `androidgpxparser`. |
| 10 | **[ATT-3363](https://atrainingtracker.atlassian.net/browse/ATT-3363)** | Centralize inline speed and unit conversions to use MyHelper | **Approved (i.O.)** | Transitioned to `Erledigt`. Centralized speed conversion constants and math in `MyHelper.java`, adding context-independent overloads and comma/dot locale resilience in the sport type editor. |
| 11 | **[ATT-3364](https://atrainingtracker.atlassian.net/browse/ATT-3364)** | Harmonize live segment and live climb bottom sheets under unified sheet layout | **Approved (i.O.)** | Transitioned to `Erledigt`. Normalized `LiveSegmentSheet.kt` PascalCase filename and consolidated duplicate scaffold wrappers into canonical `LiveTrackingSheetContainer`. |
| 12 | **[ATT-3365](https://atrainingtracker.atlassian.net/browse/ATT-3365)** | Standardize cluster tuning form controls using TuningFormControls | **Approved (i.O.)** | Transitioned to `Erledigt`. Parameterized `TuningFormControls.kt` with disabled states and standard 48dp touch targets, standardizing `ClusterTuningContent.kt`. |
| 13 | **[ATT-3366](https://atrainingtracker.atlassian.net/browse/ATT-3366)** | Eliminate manual cursor column extractions in repositories using fromCursor mappers | **Approved (i.O.)** | Transitioned to `Erledigt`. Introduced pre-resolved column index caching (`WaypointIndices`, `PathPointIndices`, `WorkoutBatchKeys`) and authoritative `fromCursor`/`fromCursorBatch` mappers. |

---

### 1.2 Backlog Follow-Up Tickets Created During Joint Review

In strict compliance with **Rules 8 & 9 ("No code changes during Sprint Review & Lean Defect Recording")**, user observations were captured immediately into a dedicated backlog ticket without modifying code during the review ceremony:

1. **[ATT-3573](https://atrainingtracker.atlassian.net/browse/ATT-3573)**: `[Verbesserung] Refine sharp turn vector icons to reduce visual resemblance to U-turn`
   - **Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)
   - **Observation**: "Die Sharp left/right sehen jetzt fast so aus wie ein U-Turn."
   - **Scope**: Re-engineer the vector geometry for sharp left and sharp right cues (e.g. sharp diagonal bend / chevron / reduced loop angle) to clearly distinguish acute turns from U-turn hooks at high athletic speeds.

---

## 2. Topic 1 Deep Dive: Single Source of Truth & DRY Consolidation (Rule 36)

### 2.1 The Architectural Invariant
Throughout Sprint 2026-42.3, a concerted refactoring campaign eliminated widespread code duplication across the persistence, parsing, and presentation layers:
1. **XML Parsers (ATT-3362)**: Unified 4 separate parser implementations spanning ~600 redundant lines of pull-parser loops into streaming `TcxTrackpointParser` and `GpxTrackpointParser`.
2. **Speed & Metric Conversions (ATT-3363)**: Consolidated scattered `* 3.6` and `/ 3.6` operations into parameterized `MyHelper.UserUnit2mps` and `mps2userUnit`, eliminating magic constants.
3. **Distance & Elevation Formatters (ATT-3361)**: Replaced raw `"km"` and `"m"` templates with canonical formatters, fixing broken Imperial unit display in HUD and navigation cards.
4. **Bottom Sheet Scaffold (ATT-3364)**: Extracted `LiveTrackingSheetContainer` for both Live Segments and Live Climbs.
5. **Settings Tuning Controls (ATT-3365)**: Replaced bespoke sliders and switches with `TuningFormControls.kt`.
6. **Cursor-to-Entity Mappers (ATT-3366)**: Centralized column lookups with single-pass index caching, reducing $O(N \times K)$ JNI lookups to $O(K)$.

### 2.2 Permanent Process Insight
Whenever a feature requires parsing, formatting, or UI controls similar to an existing capability, engineers must extend or parameterize the canonical component rather than copying and pasting private implementations.

---

## 3. Topic 2 Deep Dive: Safe Background Automation & Concurrency Governance

### 3.1 WorkManager Policy Hygiene (ATT-3472 & ATT-3478)
In automated background tasks (`BackupWorker`, `StravaEquipmentSyncWorker`, `StravaSegmentsSyncWorker`, `StravaRoutesSyncWorker`):
- Using `ExistingPeriodicWorkPolicy.REPLACE` caused timers to reset on every app cold start.
- Transitioning to `ExistingPeriodicWorkPolicy.UPDATE` preserved elapsed countdowns.
- Proactive token refreshing (`StravaHelper.getRefreshedAccessToken()`) prevented background 401 Unauthorized crashes.
- Dual-locking (`ReentrantLock` + `FileLock` on `backup.lock`) ensured safe coordination between background workers and manual athlete triggers.

---

## 4. Topic 3 Deep Dive: Platform-Native Ingestion vs. Sensitive Cloud Scopes

### 4.1 Storage Access Framework (SAF) Supremacy (ATT-3489 & ATT-3491)
Attempting to scan broad cloud storage (like Google Drive) via direct REST APIs incurs severe security review burdens (sensitive OAuth scopes `drive.readonly`).
By contrast, delegating file and folder discovery to the native Android Storage Access Framework (`OpenDocumentTree()` and `OpenMultipleDocuments()`):
- Operates entirely under least-privilege security models.
- Transparently supports local storage, SD cards, Google Drive, Nextcloud, and Dropbox through system document providers.
- Provides superior user control and zero CASA audit friction.

---

## 5. Process & Rulebook Hardening Commitments

The following permanent updates are codified in the Document Triad:
1. **Rule 20 & Rule 36 in `.agents/rules/aspice_governance.md`**: Cursor extraction routines must use pre-resolved index caching (`*Indices`) and authoritative `fromCursor` / `fromCursorBatch` mappers.
2. **`docs/design_guidelines.md` §5.5**: Directional navigation icons must clearly differentiate sharp turns from U-turn loops while maintaining baseline grounding ($y=20$) and 2dp stem consistency.
3. **`docs/coding_standards.md` §2.1**: WorkManager periodic jobs must always use `ExistingPeriodicWorkPolicy.UPDATE` unless an intentional cancellation and timer reset is explicitly specified.

---

## 6. Sprint Sign-Off & Integration

With all 13 sprint tickets reviewed (10 approved, 3 retained in `Final Review (Human)` for field/manual verification), follow-up ticket **ATT-3573** logged, and the Retrospective complete, Sprint **2026-42.3** is finalized.

Branch `sprint/2026-42.3` is ready for final merge into `develop`.
