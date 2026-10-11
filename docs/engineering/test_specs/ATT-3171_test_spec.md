# Stage 2: Requirement & Test Specification - ATT-3171

## 1. Traceability & Context
- **Issue**: [ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171): `[Bug] Modifying Vorausschau-Kameraabstand setting has barely noticeable effect on map follow-me camera offset`
- **Sub-task**: [ATT-3241](https://atrainingtracker.atlassian.net/browse/ATT-3241) (`[Req & Test Spec]`)
- **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
- **Active Sprint**: `Sprint 2026-41.9`
- **Author**: AI Agent 1 (Implementer)
- **Reviewer**: AI Agent 2 (Auditor)

---

## 2. Requirement Specification (`REQ-MAP-048`)

### Requirement Statement
The system SHALL scale and project the forward lookahead camera target coordinate in `calculateFollowMeCameraTarget` (`MapBehaviors.kt`) such that adjusting the "Vorausschau-Kameraabstand" (*Forward Lookahead Padding*) slider produces a proportional, visually distinct shift of the athlete marker between the screen center and lower viewport across all camera tilt angles, compensating for 3D perspective tilt foreshortening and providing explanatory scale direction in user settings (ATT-3171, amending `REQ-MAP-046` and aligning with Design Guidelines §3.1 & §6.1):

1. **Analytical Forward Lookahead Projection Calibration (`MapBehaviors.kt`)**:
   - `calculateFollowMeCameraTarget` SHALL accept `tiltAngle: Float = 0f`.
   - The offset fraction calculation SHALL scale as:
     $$\text{offsetFraction} = \left(\frac{\text{lookaheadPaddingPercent}}{100\text{f}}\right) \times 0.6\text{f}$$
     mapping slider values from $10\% \dots 50\%$ to $6\% \dots 30\%$ of viewport height (anchoring the marker from $56\%$ Y just below center to $80\%$ Y in the lower cockpit, with the $30\%$ default anchoring at $68\%$ Y in the canonical lower third).
2. **Perspective Tilt Foreshortening Compensation (`MapBehaviors.kt`)**:
   - The ground offset projection SHALL compensate for 3D perspective pitch tilt foreshortening:
     $$\text{tiltFactor} = \cos(\text{Math.toRadians(tiltAngle.toDouble().coerceIn(0.0, 70.0))).toFloat().coerceAtLeast(0.34f)}$$
     $$\text{offsetMeters} = \frac{\text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}}{\text{tiltFactor}}$$
   - At $\text{tiltAngle} = 70^\circ$, $\text{tiltFactor} \approx 0.342$, scaling the ground offset by $\approx 2.92\times$ so that when Google Maps renders the 3D perspective camera, the on-screen displacement matches the intended fraction of viewport height.
3. **Follow-Me Controller Delegation (`MapBehaviors.kt`)**:
   - `followMeController` SHALL pass `tiltAngle = effectiveTilt` into `calculateFollowMeCameraTarget`.
4. **Settings Scale Direction Transparency (Design Guidelines §3.1)**:
   - In accordance with Design Guidelines §3.1, `tuning_map_lookahead_padding_desc` SHALL explicitly state the physical scale direction across all 9 supported locales:
     - **EN**: `"Viewport padding percent to position the rider in the lower portion and maximize visible road ahead (higher value = rider further down / more road ahead)"`
     - **DE**: `"Anzeigebereich-Puffer in Prozent, um die Fahrerposition nach unten zu versetzen und den Blick auf die Strecke nach vorn zu maximieren (höherer Wert = Fahrer weiter unten / mehr Vorausschau)"`
     - **ES**: `"Porcentaje de margen para situar al ciclista en la parte inferior y maximizar la carretera visible por delante (valor más alto = ciclista más abajo / más vista adelante)"`
     - **FR**: `"Pourcentage de marge pour positionner le cycliste en bas et maximiser la route visible devant (valeur plus élevée = cycliste plus bas / meilleure visibilité)"`
     - **IT**: `"Percentuale di spaziatura per posizionare il ciclista nella parte bassa e massimizzare la strada visibile in avanti (valore più alto = ciclista più in basso / maggiore visuale)"`
     - **JA**: `"ライダーの位置を下部に配置し、前方の道路視野を最大化するためのビューポート余白比率（%）（高い値 = ライダーがより下に配置 / 前方視野拡大）"`
     - **NL**: `"Percentage marge om de fietser in het onderste deel te plaatsen en maximaal zicht op de weg vooruit te bieden (hogere waarde = fietser verder onderin / meer zicht vooruit)"`
     - **PL**: `"Procentowy margines widoku, aby umieścić rowerzystę w dolnej części i zmaksymalizować widoczność drogi z przodu (wyższa wartość = kolarz niżej / większe pole widzenia)"`
     - **PT**: `"Percentagem de margem para posicionar o ciclista na parte inferior e maximizar a estrada visível à frente (valor mais alto = ciclista mais abaixo / melhor visão à frente)"`
5. **Preservation of System Invariants**:
   - Single spatial authority (Rule 33 / Design Guidelines §6.1) without competing Google Maps content padding MUST be strictly preserved.
   - Bearing low-pass filter smoothing ($\alpha = 0.15$), speed-dependent zoom curve, DataStore preference schema and defaults ($30\%$), and 100% full clean-room unit test pass rate MUST be strictly preserved.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Amends `REQ-MAP-046` (*Unified Viewport Anchor Authority for Follow-Me Map Tracking*, Sprint 2026-41.8, [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)).
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.8 (`ATT-3102`, commit `611d3781`).
3. **Root Reason for Existing Formulation**: ATT-3102 unified spatial authority to resolve the 1 Hz viewport oscillation by calculating lookahead analytically instead of using GoogleMap content padding. To approximate the old 15% midpoint shift, the developer used `* 0.5f`. However, `tiltAngle` was omitted from the calculation, and because `DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f`, perspective foreshortening ($\cos(70^\circ) \approx 0.342$) squashed the displacement to ~6.8% of the screen across the entire 10%–50% slider range.
4. **Preservation of Core Invariants**: Retaining single spatial authority, smooth bearing filtering, speed-dependent zoom, and 100% clean-room test pass rate.

---

## 4. Acceptance Criteria (Given-When-Then)
- **AC-1 (Proportional Scaling Across Slider Range)**:
  - *Given* an active workout tracking session in `FOLLOW_ME` mode with a 600dp viewport at zoom 18f,
  - *When* `lookaheadPaddingPercent` is increased from 10% to 30% and to 50%,
  - *Then* the calculated ground offset distance in meters SHALL scale strictly proportionally (5x from 10% to 50%, and 3x from 10% to 30%).
- **AC-2 (Tilt Foreshortening Compensation)**:
  - *Given* a fixed `lookaheadPaddingPercent = 30f` and viewport height of 600dp,
  - *When* `tiltAngle` is 70°,
  - *Then* the calculated ground offset distance in meters SHALL be scaled up by approximately $2.92\times$ relative to `tiltAngle = 0°`, compensating for 3D perspective foreshortening.
- **AC-3 (Safe Tilt Bounds Handling)**:
  - *Given* `calculateFollowMeCameraTarget` invoked with extreme tilt angles (e.g. 90° or negative angles),
  - *When* evaluating the tilt compensation factor,
  - *Then* `tiltAngle` SHALL be safely coerced to $[0^\circ, 70^\circ]$ and `tiltFactor` SHALL be clamped to $\ge 0.34\text{f}$, completely preventing division by zero or negative offsets.
- **AC-4 (FollowMeController Contract Integrity)**:
  - *Given* `followMeController` executing during workout tracking,
  - *When* invoking `calculateFollowMeCameraTarget`,
  - *Then* `followMeController` SHALL pass `tiltAngle = effectiveTilt`.
- **AC-5 (Localization Parity & Scale Direction)**:
  - *Given* all 9 supported application locales,
  - *When* inspecting `tuning_map_lookahead_padding_desc`,
  - *Then* each locale SHALL include the explicit scale direction indicator with zero missing entries.

---

## 5. Test Specification (`TST-MAP-050`)

### Verification Procedures
1. **Mathematical & Scaling Unit Tests (`FollowMeCameraTargetTest.kt`)**:
   - `testLookaheadPaddingScaling_increasesProportionally`: Asserts that ground offset at 50% padding is strictly $5\times$ the offset at 10% padding, and at 30% padding is $3\times$ the offset at 10% padding.
   - `testTiltForeshorteningCompensation_scalesGroundOffsetAtSteepTilt`: Asserts that at `tiltAngle = 70f`, the ground offset is $\approx 2.92\times$ (tolerance $[2.8, 3.0]$) larger than at `tiltAngle = 0f`.
   - `testTiltForeshorteningCompensation_clampsExtremeTiltSafely`: Asserts that `tiltAngle = 90f` and `tiltAngle = -10f` do not crash and clamp safely within valid bounds.
   - `testZeroPaddingOrZeroHeight_returnsCurrentLocationUnchanged`: Preserves zero-padding and zero-height edge cases.
2. **Contract Tests (`FollowMeUnifiedAnchorContractTest.kt`)**:
   - Asserts `followMeController` passes `tiltAngle = effectiveTilt` into `calculateFollowMeCameraTarget`.
   - Asserts `calculateFollowMeCameraTarget` signature declares default `tiltAngle: Float = 0f`.
3. **Localization Parity Audit (`TranslationParityTest.kt`)**:
   - Asserts 100% parity across all 9 supported locales for `tuning_map_lookahead_padding_desc`.
4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 6. Traceability Matrix

| Requirement ID | Test Specification ID | Test Class / Verification Target | Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-048` §1 | `TST-MAP-050` §1 | `FollowMeCameraTargetTest.testLookaheadPaddingScaling_increasesProportionally` | Proportional lookahead scaling |
| `REQ-MAP-048` §2 | `TST-MAP-050` §1 | `FollowMeCameraTargetTest.testTiltForeshorteningCompensation_scalesGroundOffsetAtSteepTilt` | 3D perspective tilt compensation |
| `REQ-MAP-048` §2 | `TST-MAP-050` §1 | `FollowMeCameraTargetTest.testTiltForeshorteningCompensation_clampsExtremeTiltSafely` | Tilt angle clamping & bounds |
| `REQ-MAP-048` §3 | `TST-MAP-050` §2 | `FollowMeUnifiedAnchorContractTest` | Controller delegation contract |
| `REQ-MAP-048` §4 | `TST-MAP-050` §3 | `TranslationParityTest` | 9-language scale direction parity |
| `REQ-MAP-048` §5 | `TST-MAP-050` §4 | `./gradlew testDebugUnitTest` | Clean-room full-suite regression |
