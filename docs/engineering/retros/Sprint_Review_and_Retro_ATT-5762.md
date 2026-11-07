# Sprint Review & Retrospective — Sprint 2026-45.10 (ATT-5762)

**Ceremony Ticket**: [ATT-5762](https://rainerblind.atlassian.net/browse/ATT-5762)  
**Active Sprint**: `2026-45.10` (Jira Sprint ID `402`) | **Target Release**: `V5.0.0`  
**Sprint Integration Branch**: `sprint/2026-45.10` $\rightarrow$ `develop`  
**Date**: 2026-11-07 | **Facilitator**: Coordinator Agent & Rainer Blind

---

## 1. Sprint Velocity & Delivery Metrics (`REQ-PRO-029`)

| Metric | Ticket Count | Story Points (SP) | Share |
| :--- | :---: | :---: | :---: |
| **Planned at Sprint Start** | 10 | 16 SP | 100.0 % |
| **Completed & Accepted into `develop` (`V5.0.0`)** | 9 | 14 SP | 87.5 % |
| **Discarded in Review (Dropped Prior to Merge)** | 1 (`ATT-5665`) | 2 SP | 12.5 % |
| **Bounced to `Analysis` (`n.i.O.`)** | 0 | 0 SP | 0.0 % |

* **Tier 2 Sprint Integration Gate**: `4,597 / 4,597` unit tests passed (`0` failures, `0` ignored); `verify_requirement_governance.py` passed with `100 %` living documentation parity across all 9 domain shards.

---

## 2. Joint Sprint Review Summary (Strict Rank Order — `Rule 68`)

| Rank | Ticket | SP | Epic | Summary | Review Verdict | Fix Version / Follow-Up |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- |
| 1 | `ATT-5761` | 2 | `ATT-355` | Expert Settings (`WorkoutMasksAndCardsSection` & `WorkoutDetailTabsConfigComponents`): Reorder sections via `≡` drag handle instead of 22dp arrows | **Accepted (`i.O.`)** | `V5.0.0` |
| 2 | `ATT-5760` | 1 | `ATT-355` | `WorkoutPhotoStrip`: Dynamically adapt `decodeSampledBitmap maxDim` (`[256..1200]`) to thumbnail size tier (`SMALL` / `MEDIUM` / `LARGE`) | **Accepted (`i.O.`)** | `V5.0.0` |
| 3 | `ATT-5679` | 1 | `ATT-355` | Import & Backup: Clarify `import_strava_archive_title` with explicit import verb across all 9 languages | **Accepted (`i.O.`)** | `V5.0.0` |
| 4 | `ATT-5678` | 2 | `ATT-355` | Physiology & Localization: Replace Peaksware/TrainingPeaks trademarks (`NP`, `IF`, `TSS`, `PMC`, `CTL`/`ATL`/`TSB`) with `WP`, `RI`, `Load`, `Load / Form` across all 9 languages | **Accepted (`i.O.`)** | `V5.0.0` |
| 5 | `ATT-5677` | 1 | `ATT-355` | Localization & Brand Compliance: Neutralize `'Strava Live Segments'` feature title and hardware brands in `.fit` import text | **Accepted (`i.O.`)** | `V5.0.0` |
| 6 | `ATT-5675` | 1 | `ATT-2565` | Localization & Branding: Remove Garmin trademark `'(ClimbPro)'` from `tuning_show_live_climbs_title` across all 9 languages | **Accepted (`i.O.`)** | `V5.0.0` |
| 7 | `ATT-5674` | 1 | `ATT-176` | `ClusterNamingDialog`: Allow switching back to `'+ Neue Strecke erstellen...'` after selecting an existing favorite route | **Accepted (`i.O.`)** | `V5.0.0` + Follow-up `ATT-5818` |
| 8 | `ATT-5670` | 2 | `ATT-355` | Navigation Drawer & Expert Settings: Restructure Settings into `'Info & Unterstützung'` drawer group and 3 thematic subheaders in Expert Settings | **Accepted (`i.O.`)** | `V5.0.0` + Follow-ups `ATT-5819`, `ATT-5820`, `ATT-5821` |
| 9 | `ATT-5666` | 3 | `ATT-176` | Workout Clusters (*Lieblingsstrecken*): Merge cluster A into B via long-click context menu with similarity-ranked target selection and confirmation dialog | **Accepted (`i.O.`)** | `V5.0.0` + Follow-up `ATT-5824` |
| 10 | `ATT-5665` | 2 | `ATT-176` | Workout Clusters (*Lieblingsstrecken*): Automatic pairwise similarity detection and conditional header merge button | **Discarded in Review** | Dropped commit `769b80d1` from `sprint/2026-45.10` prior to `develop` merge |

---

## 3. Retrospective Root Cause Analysis & Permanent Countermeasures (`Rule 77`)

### Finding 1: Upfront Coordinator Batch Audit at Sprint-Start Instead of One-by-One Walkthrough (`ATT-5762`)
* **Observation**: At Sprint-Start (Ceremony 1), walking through every ticket one-by-one with the human user adds unnecessary ceremony because the human user has already pre-screened and approved tickets by placing them into the active sprint.
* **Root Cause**: `.agents/skills/sprint-planner/SKILL.md` prescribed an interactive ticket-by-ticket walkthrough for both Ceremony 1 (Sprint-Start) and Ceremony 2 (Sprint Review).
* **Permanent Countermeasure (`Rule 77` — `.agents/skills/sprint-planner/SKILL.md`)**:
  * In **Ceremony 1 (Sprint-Start)**, the Coordinator performs a thorough upfront audit of all regular sprint tickets (verifying Story Point estimates, acceptance criteria clarity, duplicate/overlap detection, and human prerequisites), presents a consolidated overview table plus any exception-based clarifying questions in a single pass, and batch-transitions the approved tickets to `Analysis`.
  * In **Ceremony 2 (Joint Sprint Review)**, strict one-by-one evaluation (`Rule 68`) remains mandatory.

### Finding 2: Outer `Modifier.clickable` Swallowed by `enabled = true` `OutlinedTextField(readOnly = true)` (`ATT-5674` $\rightarrow$ `ATT-5818`)
* **Observation**: In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), tapping the `trailingIcon` opened `WorkoutClusterSelectionDialog`, whereas tapping the body of the `readOnly = true` `OutlinedTextField` did not open the picker.
* **Root Cause**: In Jetpack Compose Material 3, an `enabled = true` `OutlinedTextField` (even with `readOnly = true`) attaches an internal pointer-input handler inside `BasicTextField` that consumes tap events for focus management before they bubble up to an outer `Modifier.clickable`.
* **Permanent Countermeasure (`Rule 77` — `docs/design_guidelines.md` Section 5.19)**:
  * Never rely on an outer `Modifier.clickable` on an `enabled = true` `OutlinedTextField(readOnly = true)`. Use `ExposedDropdownMenuBox` (`Modifier.menuAnchor()`) or observe `PressInteraction.Release` on a `MutableInteractionSource` passed to `OutlinedTextField(interactionSource = ...)`.
  * Tracked for fix in backlog ticket **`ATT-5818`**.

### Finding 3: Two-Step Dialog State Reset Race Between `onSelect` and `onDismiss` (`ATT-5666` $\rightarrow$ `ATT-5824`)
* **Observation**: When selecting a target cluster inside `WorkoutClusterSelectionDialog` (opened from the long-click context menu in `WorkoutClustersScreen.kt`), the selection sheet closed without showing the confirmation `AlertDialog` on the first attempt; the confirmation dialog only appeared on the second attempt.
* **Root Cause**: `WorkoutClusterSelectionDialog` invokes `onSelect(cluster)` followed immediately by `onDismiss()` inside the candidate click handler. In `WorkoutClustersScreen.kt`, `onSelect` set `mergeTargetCluster = target`, and `onDismiss` immediately set `clusterToMerge = null`, invalidating the confirmation dialog guard `if (clusterToMerge != null && mergeTargetCluster != null)`.
* **Permanent Countermeasure (`Rule 77` — `docs/design_guidelines.md` Section 5.19)**:
  * When chaining a selection BottomSheet into a confirmation `AlertDialog`, never gate the confirmation dialog on a state variable cleared by the picker's `onDismiss()` callback. Capture the confirmation state as a self-contained pair (`pendingMergePair = source to target`) inside `onSelect`.
  * Tracked for fix in backlog ticket **`ATT-5824`**.
