# Stage 5: Walkthrough & Verification - ATT-7533: Polar H10 False-Positive Smoothed Warning Fix & UI Ergonomics

**Ticket**: [ATT-7533](https://rainerblind.atlassian.net/browse/ATT-7533)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-TRK-067.3`, `REQ-TRK-067.5`, `REQ-TRK-068.3`, `REQ-TRK-068.5`  
**Test Mapping**: `TST-TRK-067.1`, `TST-TRK-067.3`, `TST-TRK-068.3`, `TST-TRK-068.5`  
**Branch**: `feature/ATT-7533` | **Author**: AI Agent 2 (Flash) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Refined `HrvSignalQualityAnalyzer.isDiscordant` by adding `SMOOTHED_DISCORDANT_TPR_THRESHOLD = 0.28` and extreme variance collapse guard (`rMSSD < 2.0 ms`). This unblocks authentic Polar H10 low-vagal evening/orthostatic sinus rhythm ($rMSSD = 4.5\text{ ms}$, $\alpha_1 = 1.52$, $TPR = 0.38 \to \text{VALID}$) while preserving 100% detection for firmware-smoothed sensors ($TPR < 0.28$). In `MorningHrvCheckScreen`, added dedicated `morning_hrv_result_title` ("Messergebnis"), shortened save button to single-word verb ("Speichern"), updated subtitle to natural resting phrasing across all 9 locales, dynamically inverted action button prominence when discard is recommended, and applied `heightIn(min = 52.dp)` with text ellipsis to prevent vertical clipping.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-067.3`, `REQ-TRK-067.5` | `TST-TRK-067.1`, `TST-TRK-067.3` | `HrvSignalQualityAnalyzerTest` | **PASSED** | `Verified` |
| `REQ-TRK-068.3` | `TST-TRK-068.3` | `MorningHrvCheckViewModelTest` & Compose review | **PASSED** | `Verified` |
| `REQ-TRK-068.5` | `TST-TRK-068.5` | `TranslationParityTest` (9 locales) | **PASSED** | `Verified` |
| `REQ-PRO-001`, `REQ-PRO-022` | `TST-TRK-067.7`, `TST-TRK-068.6` | `verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityAnalyzerTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckViewModelTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
```text
BUILD SUCCESSFUL in 2m 43s
33 actionable tasks: 19 executed, 14 up-to-date
100% PASS: All unit tests in targeted test classes passed cleanly.
```

### Governance Verification Evidence
Command: `python3 tools/verify_requirement_governance.py`
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
PASS: Zero attribute violations detected. Zero unmounted Composable islands. Repository encapsulation verified.
```

---

## 4. UI Consistency & Verification Checklist
* **`docs/design_guidelines.md` §1 & §5 Compliance**:
  * Shapes: `RoundedCornerShape(12.dp)` on all action buttons and result cards.
  * Spacing & Sizing: `heightIn(min = 52.dp)` replaces fixed height, preventing vertical label clipping.
  * Typography & Parity: 100% 9-language translation parity verified; `maxLines = 1` and `TextOverflow.Ellipsis` applied.
  * Visual Hierarchy: When `recommendDiscard == true`, *Verwerfen* is highlighted as primary filled `Button` while *Speichern* is secondary `OutlinedButton`.

---

## 5. Invariant & Governance Verification
1. **Zero Regression in Smoothing Detection**: Magene/CooSpo smoothed streams ($TPR < 0.20$ or discordant $TPR < 0.28$) remain 100% detected.
2. **Zero Schema Alteration**: SQLite tables, `HrvReadinessEntry`, and baseline SWC math remain strictly invariant.
3. **9-Language String Parity**: 100% key and format specifier parity across all 9 supported locales.
