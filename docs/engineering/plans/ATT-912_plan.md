# Implementation Plan - ATT-912: Strava Segments PB Update from Strava Feedback

## 1. Context & Objectives
When an athlete uploads a workout to Strava, Strava processes the GPS track and returns detailed activity information containing `segment_efforts`. Strava explicitly marks personal records with `pr_rank = 1` and course records with `kom_rank = 1`.
Currently, `aTrainingTracker` only updates the `pr_time` column in `Segments.db` (`StarredSegmentsTable`) when the user manually triggers a full segment sync from Strava in settings. As a result, local segment views and live segment tracking retain stale PR times after new personal records are set.

The goal of **ATT-912** is to:
1. Parse Strava segment effort feedback in `StravaUploader.kt` upon activity upload completion (`doUpdate`).
2. Atomically update `pr_time` in `Segments.db` (`StarredSegmentsTable`) for any starred segments where a new fastest time was achieved (`SegmentsDatabaseManager.java`).
3. Immediately propagate the updated PR time to `SegmentsRepository.kt` so reactive UI components (`SegmentListViewModel`, `LiveSegmentsRepository`) update without requiring an app restart.
4. Render a prominent celebration highlight banner in `StravaActivitySection.kt` within `WorkoutSummary.kt` whenever an activity achieves new PRs or KOMs.
5. Guarantee 100% localization parity across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. Verify via comprehensive unit tests and clean-room full regression suite.

---

## 2. Requirements & Verification Traceability
| Requirement ID | Component | Verification Test ID | Description |
|---|---|---|---|
| **REQ-EXP-010** | `SegmentsDatabaseManager.java` | **TST-EXP-007** | Atomic update of `pr_time` in `StarredSegmentsTable` if faster or uninitialized. |
| **REQ-EXP-010** | `SegmentsRepository.kt` | **TST-EXP-007** | In-memory reactive state flow update on `_allSegmentsWithPath`. |
| **REQ-EXP-010** | `StravaUploader.kt` | **TST-EXP-007** | Parse `segment_efforts` from Strava feedback and trigger DB/Repo updates. |
| **REQ-EXP-010** | `StravaActivitySection.kt` | **TST-EXP-007** | Celebratory highlight banner at top of Strava section for rank-1 achievements. |
| **REQ-EXP-010** | `strings.xml` (all 9 locales) | **TST-EXP-007** | 100% translation parity for celebration banner headers and descriptors. |

---

## 3. Proposed Changes & Component Architecture

### 3.1 Persistence Layer (`SegmentsDatabaseManager.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsDatabaseManager.java`
* **Changes**:
  - Implement public method:
    ```java
    public boolean updateSegmentPrTime(long stravaSegmentId, int newPrTimeSeconds)
    ```
  - Preconditions: Reject if `newPrTimeSeconds <= 0`.
  - Query: Check if `stravaSegmentId` exists in `StarredSegmentsTable`.
  - Condition: If `currentPrTime <= 0 || newPrTimeSeconds < currentPrTime`, update `Segments.PR_TIME = newPrTimeSeconds` via `db.update()` and return `true`.
  - Otherwise return `false`.

### 3.2 Reactive Repository Layer (`SegmentsRepository.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsRepository.kt`
* **Changes**:
  - Implement public method:
    ```kotlin
    fun updateSegmentPr(stravaSegmentId: Long, newPrTimeSeconds: Int)
    ```
  - Atomically update `_allSegmentsWithPath` flow: for matching segment, update `SegmentSummary.copy(prTime_raw = newPrTimeSeconds, prTime = TimeFormatter().format(newPrTimeSeconds))`.

### 3.3 Uploader Feedback Ingestion (`StravaUploader.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`
* **Changes**:
  - In `doUpdate()`, after receiving `activityJSON = getStravaActivity(activityId)`, invoke:
    ```kotlin
    processSegmentEffortsForPrs(activityJSON)
    ```
  - `processSegmentEffortsForPrs(activityJson: JSONObject?)`:
    - Safely iterate through `segment_efforts`.
    - Extract `segmentId` (from `segment.id` or `segment_id`) and `elapsed_time`.
    - Inspect `pr_rank == 1`, `kom_rank == 1`, and `achievements` array for rank 1 PR/KOM.
    - If rank 1 and `elapsed_time > 0`: invoke `SegmentsDatabaseManager.getInstance(mContext).updateSegmentPrTime(segmentId, elapsedTime)`.
    - If database update succeeded (`true`), invoke `SegmentsRepository.getInstance(mContext).updateSegmentPr(segmentId, elapsedTime)`.

### 3.4 Presentation Layer (`StravaActivitySection.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt`
* **Changes**:
  - Identify new PR/KOM efforts:
    ```kotlin
    val newPrEfforts = remember(activity.segmentEfforts) {
        activity.segmentEfforts.filter { it.prRank == 1 || it.komRank == 1 }
    }
    ```
  - When `newPrEfforts.isNotEmpty()`, render `SegmentPrCelebrationBanner(newPrEfforts)` directly beneath the header row.
  - Implement `SegmentPrCelebrationBanner(efforts: List<StravaSegmentEffort>)`:
    - Material 3 `Surface` with rounded corners (12.dp), subtle border, and gold styling (`TTColor.GoldContainer` / gold accents).
    - Trophy / medal icon with prominent celebratory title:
      - 1 PR: localized `strava_new_pr_banner_title_single`
      - >1 PRs: localized `strava_new_pr_banner_title_multiple` (with count)
    - List of achieved segment names and formatted times.

### 3.5 Localization Parity (9 Locales)
* **Files**:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)
* **Keys Added**:
  - `strava_new_pr_banner_title_single`
  - `strava_new_pr_banner_title_multiple`

---

## 4. Invariant Protection Checklist
* [x] **Strava Naming Strategy (`REQ-EXP-008`)**: Title preservation on duplicate workouts and smart naming on fresh uploads remain unchanged.
* [x] **Duplicate Equipment Reconciliation (`REQ-EXP-009`)**: Equipment and sport type enrichment from Strava cloud remain unaltered.
* [x] **Accordion Presentation (`REQ-UI-131`, `REQ-UI-144`)**: Highlight filtering and expand/collapse thresholds ($K=4$, best efforts unit filtering) remain intact.
* [x] **Live Segments Tracking (`REQ-LIV-001` - `REQ-LIV-004`)**: Gate crossing math, bearing alignment (45°), and real-time progress calculations remain unchanged.
* [x] **Database Schema**: `Segments.db` tables, column names, and SQLite versions remain unchanged.

---

## 5. Verification & Test Plan
1. **Database Unit Tests**:
   - `SegmentsDatabaseManagerTest.kt`: verify `updateSegmentPrTime` handles faster times, slower times, equal times, missing segments, and uninitialized records.
2. **Repository Unit Tests**:
   - `SegmentsRepositoryTest.kt`: verify `updateSegmentPr` updates in-memory `_allSegmentsWithPath` flow reactively.
3. **Uploader Unit Tests**:
   - `StravaUploaderNamingTest.kt`: verify `doUpdate` extracts PR segment efforts and triggers DB/repo updates.
4. **UI Presentation Tests**:
   - `StravaActivitySectionTest.kt`: verify `SegmentPrCelebrationBanner` renders for rank-1 efforts and is omitted when none exist.
5. **Localization Parity Test**:
   - `TranslationParityTest.kt`: verify new keys exist and pass format specifier checks across all 9 locales.
6. **Full Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` across the entire project.
