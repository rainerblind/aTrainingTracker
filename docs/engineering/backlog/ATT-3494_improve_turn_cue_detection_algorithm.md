# Backlog Ticket Specification: ATT-3494

**Key:** ATT-3494  
**Summary:** Improve turn cue detection algorithm and curvature classification along route polylines  
**Type:** Improvement (`Verbesserung`)  
**Status:** Open / Backlog (`Zu erledigen`)  
**Epic:** ATT-2564 (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)  
**Related Tickets:** [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450) (Initial Turn-by-Turn Cues), [ATT-3455](https://rainerblind.atlassian.net/browse/ATT-3455) (Slight Turn Vector Icons), [ATT-3493](https://rainerblind.atlassian.net/browse/ATT-3493) (Sharp Turn Vector Icons)

---

### Problem Statement & Value Proposition
During active route navigation testing, the automated geometric turn detection engine (`TurnCueDetector.kt`) exhibits accuracy limitations when identifying and classifying turns from route polylines:
1. **Fixed-Window Blind Spots**: The current algorithm analyzes heading change across a static 15-meter window (`WINDOW_LOOKAROUND_METERS = 15.0m`). Long, sweeping curves or multi-segment turns spanning 30m–80m often exhibit low chord-to-chord deltas ($< 30^\circ$) at any single 15m step, causing real $90^\circ$ turns to be completely missed.
2. **Spurious Cues on Winding Roads**: On curvy country roads or trails without junctions, natural road bends exceeding $30^\circ$ frequently trigger nuisance "Halb rechts" / "Halb links" turn prompts when the athlete should simply follow the main path.
3. **Apex Placement & Misclassification**: In gradual turns, cues are occasionally placed at an arbitrary interior trackpoint rather than the true start or apex of the turn, leading to confusing countdown distances. Furthermore, boundary cutoffs ($30^\circ$, $60^\circ$, $120^\circ$, $155^\circ$) need refinement to distinguish genuine intersection turns from road curvature.

Athletes need an intelligent, robust turn detection algorithm that accurately identifies intersections and significant turns, suppresses road curvature noise, places turn cues at the natural turn apex, and properly classifies turn severity.

---

### User Story
* **As an**: athlete following a navigated route on my bike or while running
* **I want**: turn-by-turn prompts to accurately identify genuine turns and intersections without phantom cues on winding paths
* **So that**: I receive clear, timely, and trustworthy navigational guidance at every critical junction.

---

### Scope & Technical Architecture

1. **Curvature Aggregation & Multi-Scale Detection (`TurnCueDetector.kt`)**:
   - Evaluate cumulative heading deflection over adaptive distance windows rather than relying solely on a fixed 15-meter chord.
   - Detect sweeping turns where cumulative angle $\sum \Delta \theta \ge 60^\circ$ across 30–60 meters even if point-to-point chords are gradual.

2. **Apex Localization & Cluster Deduplication**:
   - Identify the true apex (point of maximum curvature) for each detected turn maneuver to anchor the cue and countdown distance accurately.
   - Refine duplicate suppression (`MIN_SEPARATION_METERS`) to prevent clustered cues during roundabouts, chicane maneuvers, or switchbacks.

3. **Classification Threshold Calibration**:
   - Audit angular thresholds for `TurnDirection` classification (`SLIGHT_RIGHT`, `RIGHT`, `SHARP_RIGHT`, `U_TURN`, `SLIGHT_LEFT`, `LEFT`, `SHARP_LEFT`).
   - Distinguish between gentle road bends (to be ignored or classified as straight) vs. distinct turn maneuvers.

4. **Smoothing & Pre-Processing**:
   - Apply polyline pre-smoothing (e.g. Ramer-Douglas-Peucker or moving average filter) to filter out high-frequency GPS jitter before angle analysis.

5. **Comprehensive Unit & Synthetic Route Tests**:
   - Expand `TurnCueDetectorTest.kt` with realistic test polylines: 90-degree street grid corners, sweeping roundabouts, mountain hairpins, S-curve winding roads, and noisy GPS tracks.

---

### Acceptance Criteria (Given-When-Then)

* **Criterion 1: Sweeping 90-Degree Turn Detection**:
  - *Given* a route polyline with a gradual 90-degree corner distributed across a 40-meter curve
  - *When* running `TurnCueDetector.detectGeometricCues`
  - *Then* a single turn cue of type `RIGHT` or `LEFT` is detected at the apex, rather than being missed or split into multiple redundant cues.
* **Criterion 2: Winding Road Noise Suppression**:
  - *Given* a winding road with continuous gentle bends ($< 25^\circ$)
  - *When* evaluated
  - *Then* zero false turn cues are emitted, allowing the athlete to follow the road uninterrupted.
* **Criterion 3: Accurate Sharp Corner Classification**:
  - *Given* an abrupt intersection turn of $90^\circ \pm 15^\circ$
  - *When* evaluated
  * *Then* the cue is classified as standard `RIGHT` or `LEFT` and anchored within 5 meters of the intersection corner.
* **Criterion 4: Regression & Performance**:
  - *Given* long routes with over 5,000 path points
  - *When* calculating turn cues
  - *Then* computation completes in $< 100\text{ms}$ on a mobile processor without UI thread stalling.

---

### Parent Epic & Placement
* **Target Epic**: `ATT-2564` (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
