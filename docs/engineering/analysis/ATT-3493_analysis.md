# Backlog Ticket Specification: ATT-3493

**Key:** ATT-3493  
**Summary:** Improve vector icons for sharp right and sharp left turn navigation cues  
**Type:** Improvement (`Verbesserung`)  
**Status:** Open / Backlog (`Zu erledigen`)  
**Epic:** ATT-2564 (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)  
**Related Tickets:** [ATT-3455](https://atrainingtracker.atlassian.net/browse/ATT-3455) (Slight Turn Vector Icons), [ATT-3494](https://atrainingtracker.atlassian.net/browse/ATT-3494) (Turn Cue Detection)  

---

### Problem Statement & Value Proposition
Following the visual refinement of slight turn cues in ATT-3455, the vector icons for sharp turns (`ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml`) also need improvement:
1. The current vector path in `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` uses a flat horizontal hook geometry (`M18,13.5l-5,-5v3.5c-3.31,0 -6,2.69 -6,6v3h2v-3c0,-2.21 1.79,-4 4,-4h5v3.5L18,13.5z`) that does not cleanly convey an acute/sharp turn maneuver (~135° direction change).
2. The stem and arrowhead proportions are visually inconsistent with the rest of the navigation direction icon family (`ic_turn_straight`, `ic_turn_right`, `ic_turn_slight_right`, `ic_turn_u_turn`), as rendered in the `TurnCueIconsGalleryPreview` gallery.
3. Athletes navigating complex or fast descents need sharp turn indicators to be immediately recognizable at a glance on the bike computer / cockpit.

---

### User Story
* **As an**: athlete following turn-by-turn route navigation
* **I want**: the sharp right and sharp left navigation icons to display clean, canonical, and balanced acute turn arrows with standard Material Design geometry
* **So that**: upcoming sharp bends and hairpin turns are immediately recognizable at high speeds or under bright sunlight, and the icon set exhibits 100% visual harmony in the navigation HUD and Compose preview gallery.

---

### Scope & Technical Architecture
1. **Vector Drawable Replacement**:
   * Replace `app/src/main/res/drawable/ic_turn_sharp_right.xml` with canonical Material `turn_sharp_right` vector path:
     * Vertical stem rising from baseline with 2dp stroke width.
     * Acute turn angle (~135°) veering sharply back-right.
     * Standard symmetric arrowhead matching the updated `TurnDirection` iconography.
   * Replace `app/src/main/res/drawable/ic_turn_sharp_left.xml` with symmetrical mirrored `turn_sharp_left` vector path.
2. **Visual Verification in `@Preview` Gallery**:
   * Inspect all 8 `TurnDirection` icons together in `TurnCueIconsGalleryPreview` (Light & Dark theme) in `TurnCue.kt` / `TurnByTurnBanner.kt` to ensure baseline alignment, line weight, and arrowhead uniformity.
3. **Contract & Color Consistency**:
   * Maintain `#FF1976D2` primary fill color token and 24x24 viewport.

---

### Acceptance Criteria (Given-When-Then)
* **Criterion 1: Sharp Right Icon Geometry**:
  * *Given* an upcoming navigation cue with `TurnDirection.SHARP_RIGHT`
  * *When* rendering `ic_turn_sharp_right.xml` in the navigation banner or cue sheet
  * *Then* the icon displays a clear, balanced acute turn arrow veering sharply to the right.
* **Criterion 2: Sharp Left Icon Geometry**:
  * *Given* an upcoming navigation cue with `TurnDirection.SHARP_LEFT`
  * *When* rendering `ic_turn_sharp_left.xml`
  * *Then* the icon displays a symmetrical mirrored acute turn arrow veering sharply to the left.
* **Criterion 3: Visual Harmony in Preview Gallery**:
  * *Given* the Compose `TurnCueIconsGalleryPreview`
  * *When* viewing all 8 turn icons side-by-side in Light and Dark themes
  * *Then* all icons exhibit consistent baseline, stroke weight, and arrowhead sizing.
