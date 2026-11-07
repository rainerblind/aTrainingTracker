# Stage 1 Analysis: ATT-5809 - Uniform 'Share Workout Image' Button in WorkoutHeader

**Ticket**: [ATT-5809](https://atrainingtracker.atlassian.net/browse/ATT-5809)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5809`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
* The action to share a workout image (`WorkoutShareBottomSheet`) is currently fragmented and hard to discover:
  1. **Workout List (`WorkoutSummary.kt`)**: No share trigger exists in `WorkoutHeader`. The bottom sheet is only accessible if photos are already attached and clicked in `WorkoutPhotoPreviewBottomSheet`.
  2. **Workout Details (`WorkoutDetailTabbedContent.kt`)**: Athletes start on Tab 1 (*Übersicht*, `MODE_A_CARD_SCROLL`), where the map is hidden (`showMap = false`). The floating share button in `MapDetailLayout.kt` only exists on the map surface, making it completely invisible on Tabs 1, 3, and 4.
  3. **Missing Header in Snapshot**: `WorkoutDetailTabbedContent.kt` passes `header = {}` to `MapDetailLayout`, causing `headerLayer` to be empty and omitting `WorkoutHeader` from Option A snapshots.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: Sharing was historically coupled to `MapDetailLayout`'s map canvas rather than the top-level identity anchor (`WorkoutHeader`), while multi-tab navigation (`ATT-3920`) moved the map to a secondary tab.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `WorkoutHeader.kt:111-130`: Action buttons currently only include `actions()`, `onEditWorkout`, and the 3-dot export menu. Adding `onShareWorkout` cleanly unifies the header controls.
  * Inspected `WorkoutDetailTabbedContent.kt:248-375`: Confirmed `header = {}` is passed into `MapDetailLayout`, leaving `headerLayer` blank during snapshot composition.
  * Verified Option A and Option B behaviors: Option B (photo overlay) works anywhere with workout telemetry; Option A (map snapshot) requires map/elevation/analytics layers and can navigate or switch to the map tab.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Add `onShareWorkout: (() -> Unit)? = null` to `WorkoutHeader.kt` (32dp `IconButton`, `Icons.Default.Share`, `primary` tint, next to `onEditWorkout` in portrait and landscape).
  * Wire `onShareWorkout` in `WorkoutSummary.kt` to open `WorkoutShareBottomSheet` (Option B in-place, Option A routes to map details).
  * Wire `onShareWorkout` in `WorkoutDetailTabbedContent.kt` across all tabs, ensuring Option A captures `WorkoutHeader` and triggers map snapshot.
  * Retire redundant floating share button on map when `workoutData != null` in multi-tab details (retaining it for route/segment maps).
* **Out-of-Scope**: Changes to `PhotoTrackOverlayComposer` math, watermark formats, or external social sharing intents.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-447.1` (*Share Mode Selection Bottom Sheet*), extending `REQ-UI-255` (*Header & Primary Metrics Top Identity Anchor*) and `REQ-UI-408` (*Multi-Tab Workout Details*).
* **Historical Origin & Commit Trace**: `ATT-30` / `ATT-5479` introduced `WorkoutShareBottomSheet`; `ATT-3920` introduced tabbed layout without lifting the share action to the persistent header.
* **Root Reason for Existing Formulation**: The floating button was placed inside the map container when `MapDetailLayout` was the sole aftermath view.
* **Preservation of Core Invariants**: Standalone route/segment maps retain map-based sharing; 9-language localization parity preserved; all existing photo cascade tiers (`REQ-UI-447.6`) remain intact.

---

## 5. Architectural Strategy & High-Level Solution
* **WorkoutHeader**: Add `onShareWorkout` parameter; render `IconButton` (`Icons.Default.Share`, `size(32.dp)`, `tint = primary`) adjacent to edit button; adjust title spacer/constraints in portrait (~104dp) to avoid title truncation.
* **WorkoutSummary**: Manage `showShareSheet` state; open `WorkoutShareBottomSheet`; Option A forwards to `onMapClick()`.
* **WorkoutDetailTabbedContent**: Wire `onShareWorkout` on persistent header; record `WorkoutHeader` in `headerLayer`; Option A scrolls pager to map tab if needed and triggers snapshot.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in Strava/GPX export menu; touch targets $\ge 32\text{dp}$; non-workout map callers unaffected; 100% test pass rate.
* **Risk Rating**: **LOW** — Composable action callback plumbing and graphicsLayer recording with zero schema, database, or background service impacts.
