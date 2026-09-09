# Implementation Plan: Cluster Algorithm Educational Info Modal (Lieblingsstrecken) (ATT-501)

* **Parent Ticket**: [ATT-501](https://rainerblind.atlassian.net/browse/ATT-501) ([Feature] Info about cluster algorithm)
* **Sub-Task**: [ATT-794](https://rainerblind.atlassian.net/browse/ATT-794) ([Impl-Plan] Info about cluster algorithm)
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-UI-137` (*Cluster Algorithm & Lieblingsstrecken Educational Info Modal*)
* **Test Specification**: `TST-UI-090` (*Cluster Algorithm Info Presentation & Action Trigger Verification*)
* **Branch**: `feature/ATT-501`

---

## 1. Executive Summary & Educational Motivation

### 1.1 Problem Statement & User Need
The workout clustering algorithm ("Lieblingsstrecken" / Favorite Tracks) automatically discovers recurring route patterns using a sophisticated 3D topological spatial signature (start, end, apex/max displacement, track distance, and min/max altitude anchors), matches sport types, and adapts dynamic centroids.
However, athletes previously had no in-app explanation of how this mechanism works, why certain workouts are grouped together, or how adjusting tuning sliders affects cluster sensitivity. This lack of transparency leads to confusion when tuning parameters or when routes do not group as expected.

### 1.2 Proposed Solution
Implement a dedicated, accessible, and comprehensive educational info dialog (`ClusterInfoDialog.kt`) that clearly explains:
1. **The Concept of Lieblingsstrecken**: Automatic route grouping, smart activity naming (e.g. "Morning Run #5"), gear assignment, and recurring performance comparison.
2. **The 3D Topological Fingerprint**: Detailed breakdown of the 6 spatial criteria:
   - Start point coordinate ($200\,\text{m}$ default tolerance)
   - End point coordinate ($200\,\text{m}$ default tolerance)
   - Apex / Maximum Line Distance coordinate ($400\,\text{m}$ default tolerance)
   - Total Track Length ($20\%$ default relative tolerance)
   - Minimum Altitude coordinate ($400\,\text{m}$ default tolerance, optional)
   - Maximum Altitude coordinate ($400\,\text{m}$ default tolerance, optional)
3. **Sport Type Awareness & Learning Centroid**: The sport firewall ensuring road bike rides do not mix with trail runs, and the dynamic weighted centroid adaptation as new recordings join the track family.
4. **Sensitivity Tuning**: Practical advice on using the Master Sensitivity Slider ("Strict" to "Relaxed") and fine-tuning individual parameters for local terrain.

Dual entry points ensure this information is reachable right where athletes think about it:
- From the **Favorite Tracks header** (`WorkoutClustersTabsScreen.kt`), alongside Tuning, Sort, and Filter.
- From the **Cluster Parameter screen** (`ClusterTuningScreen.kt`), via the top app bar action.

---

## 2. Architecture & UI/UX Design

### 2.1 Dialog Architecture (`ClusterInfoDialog.kt`)
* **Component Type**: Jetpack Compose `AlertDialog`.
* **Title**: Localized title `@string/cluster_info_title` ("Workout Clustering / Lieblingsstrecken").
* **Body Content**: Scrollable `Column` with `verticalScroll(rememberScrollState())` to ensure full accessibility on all device displays, landscape orientations, and large font scales without clipping.
* **Visual Structure**:
  - Four distinct sections, each with a bold headline (`titleMedium` / `FontWeight.Bold`) and informative descriptive body (`bodyMedium`).
  - Subtle vertical spacing (`spacedBy(16.dp)`).
* **Dismissal**:
  - Prominent "Close" button (`TextButton`) calling `onDismissRequest`.
  - Tapping outside or hardware back gesture also triggers `onDismissRequest`.

### 2.2 Entry Points
1. **Favorite Tracks Header (`WorkoutClustersTabsScreen.kt`)**:
   - Insert an `IconButton` with `Icons.Outlined.Info` (or `Icons.AutoMirrored.Filled.Help`) with content description `@string/cluster_info_title` into the action row.
   - Maintain clean visual spacing alongside the settings tuning icon, sort button, and filter action button.
   - Manage local dialog visibility state `var showInfoDialog by remember { mutableStateOf(false) }`.
2. **Cluster Parameters (`ClusterTuningScreen.kt`)**:
   - In `TopAppBar`, add `actions = { ... }` with an `IconButton` displaying `Icons.Outlined.Info` and content description `@string/cluster_info_title`.
   - Disabled during active recalculation (`enabled = !isRecalculating`) for consistent interaction safety.

---

## 3. Affected Files & Proposed Modifications

### 3.1 `[NEW]` [ClusterInfoDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt)
- Create reusable Compose dialog component in package `com.atrainingtracker.trainingtracker.ui.clusters`.
- Implements GPLv3 license header.
- Renders the 4 educational sections with localized strings and vertical scrolling.

### 3.2 `[MODIFY]` [WorkoutClustersTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt)
- Add `showInfoDialog` state in `WorkoutClustersTabsScreen`.
- Add info `IconButton` in the collapsing header action row.
- Render `ClusterInfoDialog(onDismissRequest = { showInfoDialog = false })` when active.

### 3.3 `[MODIFY]` [ClusterTuningScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt)
- Add `actions` slot to `TopAppBar` containing the info `IconButton`.
- Render `ClusterInfoDialog` when clicked.

### 3.4 `[MODIFY]` String Resources across all 9 Locales
Update `strings.xml` in:
- `app/src/main/res/values/strings.xml` (EN)
- `app/src/main/res/values-de/strings.xml` (DE)
- `app/src/main/res/values-es/strings.xml` (ES)
- `app/src/main/res/values-fr/strings.xml` (FR)
- `app/src/main/res/values-it/strings.xml` (IT)
- `app/src/main/res/values-ja/strings.xml` (JA)
- `app/src/main/res/values-nl/strings.xml` (NL)
- `app/src/main/res/values-pl/strings.xml` (PL)
- `app/src/main/res/values-pt/strings.xml` (PT)

New string keys:
- `cluster_info_title`
- `cluster_info_what_title`
- `cluster_info_what_desc`
- `cluster_info_fingerprint_title`
- `cluster_info_fingerprint_desc`
- `cluster_info_sports_title`
- `cluster_info_sports_desc`
- `cluster_info_tuning_title`
- `cluster_info_tuning_desc`
- `cluster_info_close`

### 3.5 `[NEW]` [ClusterInfoDialogTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialogTest.kt)
- Unit test verifying string resource IDs, section counts, and dialog logic contracts.

---

## 4. Verification Plan & Test Strategy (SWE.4 / SWE.5)

| Test ID | Level | Target | Description |
| :--- | :--- | :--- | :--- |
| **TST-UI-090-TC-01** | Unit / UI | `ClusterInfoDialogTest` | Verifies all 4 section headings and descriptions are populated and non-empty. |
| **TST-UI-090-TC-02** | Unit / UI | `ClusterInfoDialogTest` | Verifies dismiss button invokes the `onDismissRequest` callback. |
| **TST-UI-090-TC-03** | UI Integration | `WorkoutClustersTabsScreen` | Verifies info action button is present and opens the dialog. |
| **TST-UI-090-TC-04** | UI Integration | `ClusterTuningScreen` | Verifies TopAppBar action button is present and opens the dialog. |
| **TST-UI-090-TC-05** | Verification | Translation Parity Suite | Verifies 100% presence of all 10 string keys across all 9 language directories. |
| **TST-UI-090-TC-06** | Invariant | State Isolation | Verifies opening/closing info dialog causes 0 mutations to tolerances, db, or sort/filter state. |

---

## 5. System Invariants & Safety Verification

1. **State Isolation**: Opening, reading, or closing the educational info dialog MUST NOT alter cluster parameters, trigger recalculations, or modify database records.
2. **UI Non-Disruption**: The dialog MUST be modal, properly dismissing on backdrop touch or back button without leaving the parent screen in an unrecoverable state.
3. **Scroll Containment**: Dialog content MUST be scrollable inside the dialog container so that it never clips on low-DPI or high-accessibility font scaling devices.
4. **Localization Parity**: 100% translation coverage across all 9 supported application locales.
