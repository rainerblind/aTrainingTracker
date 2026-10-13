# Stage 1 Analysis: ATT-3455 - Fix distorted vector icons for slight right and slight left turn navigation cues

**Ticket**: [ATT-3455](https://atrainingtracker.atlassian.net/browse/ATT-3455)  
**Sub-task**: [ATT-3485](https://atrainingtracker.atlassian.net/browse/ATT-3485) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Branch**: `feature/ATT-3455`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & Motivation

During turn-by-turn route navigation testing (REQ-MAP-028 / ATT-1450, ATT-2632), visual prompts for "Halb rechts" (`TurnDirection.SLIGHT_RIGHT`) and "Halb links" (`TurnDirection.SLIGHT_LEFT`) display distorted, visually inconsistent directional symbols:
1. **Floating S-Curve & Missing Baseline Anchor**: In `ic_turn_slight_right.xml` and `ic_turn_slight_left.xml`, the vector path represents an irregular floating S-curve ribbon that begins midway across the canvas (`x=2, y=14`) and never anchors to the bottom baseline stem (`y=19..20`), unlike all canonical directional icons in the application (`ic_turn_straight`, `ic_turn_left`, `ic_turn_right`, `ic_turn_sharp_right`).
2. **Asymmetrical / Malformed Arrowhead**: The arrowhead lacks standard geometric symmetry with the Google Material Design directions iconography, causing confusion for athletes glancing at the bike mount or running cockpit at speed.
3. **Lack of Instant Visual Inspection Tooling**: Developers and designers currently have no unified visual overview or `@Preview` gallery to inspect all 8 turn navigation symbols (`TurnDirection` enum variants) side-by-side across both Light and Dark themes without running a live GPX track on an active device.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Vector Path Discrepancy
Inspecting `app/src/main/res/drawable/ic_turn_slight_right.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF1976D2"
        android:pathData="M12.34,4l4.24,4.24l-2.83,2.83l-1.75,-1.75c-1.17,-1.17 -3.07,-1.17 -4.24,0l-4.24,4.24l-1.41,-1.41l4.24,-4.24c1.95,-1.95 5.12,-1.95 7.07,0l1.51,1.7l1.65,-1.66l-4.24,-4z" />
</vector>
```
* **Failure Analysis**:
  * The path `M12.34,4l4.24,4.24...` traces a hollow ribbon outlining an ambiguous curved fold. It reaches minimum y around 4 and maximum y around 14.
  * It fails to anchor to the standard bottom stem at `y=20`, leaving the lower 40% of the 24x24 canvas completely empty.
  * When rendered alongside `ic_turn_right` (which has a 2dp stem anchored at `y=19`), `ic_turn_slight_right` looks disconnected, ungrounded, and visually miniature.
  * `ic_turn_slight_left.xml` suffers from the identical defect, mirrored across the vertical axis.

### Canonical Material Navigation Standards
In Google Material Design / Material Symbols navigation set:
* **Canonical `turn_slight_right`**:
  * Path data: `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`
  * Rises vertically from bottom baseline `y=20` with standard 2dp stroke width (`x=9..11`).
  * Curves smoothly 45° to the upper right via standard Bezier control points (`c0,-0.53 0.21,-1.04 0.59,-1.41`).
  * Concludes with a balanced, symmetric 45° arrowhead with 2dp wing thickness (`H18v5.66h-2V7.41...`).
* **Canonical `turn_slight_left`**:
  * Path data: `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`
  * Exactly symmetric mirror across the vertical centerline ($x' = 24 - x$).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace `app/src/main/res/drawable/ic_turn_slight_right.xml` with canonical Material Design `turn_slight_right` vector path.
  2. Replace `app/src/main/res/drawable/ic_turn_slight_left.xml` with canonical Material Design `turn_slight_left` vector path.
  3. Maintain primary fill color token `#FF1976D2` and 24x24 viewport dimensions.
  4. Create an interactive Jetpack Compose `@Preview` composable gallery displaying all 8 `TurnDirection` enum variants side-by-side in both Light and Dark themes for instant visual verification.
  5. Author unit and contract tests verifying drawable existence, XML validity, non-empty paths, and bounding box properties.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter turn detection angle classification thresholds in `TurnCueDetector.kt` (already verified at $\pm 45^\circ$).
  * Do not alter navigation state models (`TurnNavigationState`) or chime alert generation (`NavigationAudioAlertManager.kt`).
  * Do not change string resources or localization keys for turn directions (`turn_cue_*`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-360`). No existing requirement logic modified. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450) and `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Top-Level Spatial Overlay Integration & Design Guideline Harmonization*, ATT-2941).
* **Historical Origin & Commit Trace**: Baseline turn cue drawables were originally added during initial turn prompt development in `ATT-1450` (Sprint 2026-40.16). While cardinal turns (`LEFT`, `RIGHT`, `STRAIGHT`) used clean paths, `SLIGHT_RIGHT` and `SLIGHT_LEFT` accidentally used preliminary SVG trace data.
* **Root Reason for Existing Formulation**: The original asset was imported from an unvetted vector snippet that lacked bottom stem grounding.
* **Preservation of Core Invariants**: Viewport 24x24, color `#FF1976D2`, and integration with `TurnDirection` enum and `TurnPromptBanner.kt` remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Vector Drawable Replacement**:
   * Update `app/src/main/res/drawable/ic_turn_slight_right.xml`:
     * Viewport: 24x24.
     * Fill color: `#FF1976D2`.
     * Path data: `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`.
   * Update `app/src/main/res/drawable/ic_turn_slight_left.xml`:
     * Viewport: 24x24.
     * Fill color: `#FF1976D2`.
     * Path data: `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`.
2. **Interactive Jetpack Compose `@Preview` Gallery**:
   * Implement `TurnCueIconsGallery` and multi-theme `@Preview` in `TurnPromptBanner.kt` (or a dedicated preview composable):
     * Iterates through `TurnDirection.values()`.
     * Renders each icon in a card tile with its localized string title.
     * Provides dual preview annotations: Light Theme and Dark Theme (`uiMode = Configuration.UI_MODE_NIGHT_YES`).
3. **Automated Verification**:
   * Author `TurnDirectionIconContractTest.kt`:
     * Validates that all 8 `TurnDirection` entries reference valid, existing XML drawables.
     * Parses the XML to assert standard 24x24 viewport, non-empty `pathData`, and baseline grounding (`V20` or `y=19..20`).
     * Confirms symmetrical path mirroring between slight right and slight left.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing turn cue detection and routing tests (`TurnCueDetectorTest`, `TurnByTurnNavigationEngineTest`, `TurnAlertTriggersTest`).
  2. File modularity (< 400 lines) across all modified files.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Vector drawable asset updates are purely presentational with zero impact on navigation math, database persistence, or threading.
