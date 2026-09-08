# Engineering Implementation Plan: ATT-716

## 1. Overview
* **Ticket**: [ATT-716](https://rainerblind.atlassian.net/browse/ATT-716) (`[Feature] Workout Summary: Optionally show less Strava Info`)
* **Requirement**: [`REQ-UI-131`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L255)
* **Test Specification**: [`TST-UI-084`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L285)
* **FixVersion**: `V4.9.36`
* **Goal**: Provide a smart, in-place expandable accordion for Strava segment efforts in the workout summary card to prevent card bloat on multi-segment activities while highlighting personal records (PRs, KOMs) and starred segments by default.

---

## 2. Technical Architecture & Component Changes

### Component 1: Data Model & Parsing (`StravaActivityData.kt`)
* **File**: [`StravaActivityData.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/StravaActivityData.kt)
* **Changes**:
  1. Update `StravaSegmentEffort` data class to include:
     - `val isStarred: Boolean = false`
     - `val segmentId: Long? = null`
  2. Add extension property `val StravaSegmentEffort.isHighlight: Boolean get() = isStarred || prRank != null || komRank != null`.
  3. In `StravaActivityParser.parse()`, extract:
     - `segmentId`: from nested `segment.id` or root `segment_id`.
     - `isStarred`: from nested `segment.starred` or root `starred` / `is_starred`.

### Component 2: Presentation Layer & Smart Accordion (`StravaActivitySection.kt`)
* **File**: [`StravaActivitySection.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt)
* **Changes**:
  1. Retrieve local starred segment IDs from `SegmentsDatabaseManager.getInstance(context).allSegmentSummaries` to supplement Strava activity JSON payloads.
  2. Implement collapse eligibility logic:
     - Threshold $K = 4$.
     - `isCollapsible = totalSegments > 4 && totalSegments > highlightCount`.
     - If $N \le 4$ or $N == N_{\text{highlight}}$, display all segments directly with zero accordion controls.
  3. Hoist accordion state using `rememberSaveable { mutableStateOf(false) }`.
  4. Compute `displayedSegments`:
     - If `!isCollapsible || isExpanded`: display all segment efforts.
     - If `!isExpanded && highlightCount > 0`: display only highlighted segment efforts.
     - If `!isExpanded && highlightCount == 0`: display first 4 segment efforts.
  5. Render subtle expand/collapse button below displayed segment efforts:
     - When collapsed: `"▼ Show all %1$d segments (+%2$d more)"`
     - When expanded: `"▲ Show only starred & PRs (%1$d)"`
     - Clicks on the toggle button consume the click event, preventing unintended triggering of the parent container's Strava URL launch.
  6. Add starred badge / icon next to segment name if `effort.isStarred` or matched in local starred database.

### Component 3: Localization (9 Locales)
* **Files**: `app/src/main/res/values*/strings.xml` across all 9 application locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
* Add strings:
  - `strava_show_all_segments_format`: `"Show all %1$d segments (+%2$d more)"`
  - `strava_show_less_segments_format`: `"Show only starred & PRs (%1$d)"`

---

## 3. Verification & Testing Strategy

### Automated Unit Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySectionTest.kt`
* **Test Cases**:
  1. `testParserExtractsStarredAndSegmentIdFromNestedAndFlatJson`: Verifies parsing of nested and flat `starred` and `segment_id`.
  2. `testHighlightClassification`: Verifies `isHighlight` logic for PR (1, 2, 3), KOM, and starred.
  3. `testSmallSegmentCountDoesNotTriggerAccordion`: Verifies $\le 4$ segments show all segments and no toggle button.
  4. `testAllHighlightsDoesNotTriggerAccordion`: Verifies $> 4$ segments where all are highlights show all segments and no toggle button.
  5. `testCollapsibleDefaultsToHighlights`: Verifies $> 4$ segments with standard segments defaults to showing only highlights and renders expand button.
  6. `testAccordionExpansionAndCollapse`: Verifies in-place expansion toggles to all segments and collapse button restores highlights.
  7. `testZeroHighlightsFallback`: Verifies fallback when $> 4$ segments have 0 highlights.

### Full Regression Test
* Execute `./gradlew testDebugUnitTest` verifying 100% clean pass across the full test suite.
