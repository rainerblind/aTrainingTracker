# Stage 5 Walkthrough: ATT-6702 - Mount RadarSettingsSection as TuningAccordionSection in AdvancedTuningScreen and AdvancedTuningDialog

**Ticket**: [ATT-6702](https://rainerblind.atlassian.net/browse/ATT-6702)  
**Sub-task**: [ATT-6912](https://rainerblind.atlassian.net/browse/ATT-6912) (`[Test]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-512` (*Mounting RadarSettingsSection as TuningAccordionSection*)  
**Test Spec Mapping**: `TST-UI-512` (*Radar Accordion Section and Modularity Verification*)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem & Scope Overview
`RadarSettingsSection.kt` (907 LOC) provides cycling radar overlay customization, audio/haptic alerts, tail light control, and an indoor traffic simulator, but previously had zero production callers. `ATT-6702` mounts `RadarSettingsSection` as a collapsible `TuningAccordionSection` under Block 2 ("Sensoren & GPS") in both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, with dynamic subtitle formatting, 9-language localization parity, and outer card padding alignment.

---

## 2. Key Changes Implemented

1. **9-Language Localization (`REQ-UI-512.6` / `TST-UI-512.3`)**:
   - Added `tuning_cat_cycling_radar` across all 9 `strings_radar.xml` locales:
     - EN: `"Cycling Radar"`, DE: `"Fahrrad-Radar"`, ES: `"Radar de ciclismo"`
     - FR: `"Radar cycliste"`, IT: `"Radar per bici"`, JA: `"自転車レーダー"`
     - NL: `"Fietsradar"`, PL: `"Radar rowerowy"`, PT: `"Radar de bicicleta"`
   - Verified via `TranslationParityTest` and `processDebugResources`.

2. **Accordion Category & Subtitle Formatting (`REQ-UI-512.1`, `REQ-UI-512.2` / `TST-UI-512.1`)**:
   - Added `TuningSection.CYCLING_RADAR` to `AdvancedTuningAccordion.kt`.
   - Implemented `TuningSubtitleFormatter.formatCyclingRadarSubtitle(...)` reflecting:
     - Idle state: active edge side (`Left Edge`/`Right Edge`), alert mode (`All Alerts`/`Muted`/...), and tail light default mode.
     - Simulating state: `"Simulation active • <ScenarioName>"`.

3. **Padding Hygiene & Visual Alignment (`REQ-UI-512.4`)**:
   - Stripped redundant `horizontal = 16.dp` padding from the 4 `ElevatedCard` containers in `RadarSettingsSection.kt` (`.padding(vertical = 4.dp)`), ensuring flush alignment with accordion card boundaries.

4. **Screen & Dialog Mounting (`REQ-UI-512.3`)**:
   - Mounted `RadarSettingsSection` in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` under Block 2, defaulting to collapsed (`expandedSections = emptySet()`) per Design Guidelines §3.

5. **Modularity Enforcement (`REQ-UI-512.5` / `TST-UI-512.2`)**:
   - Compacted `AdvancedTuningAccordion.kt` from 408 to 360 LOC ($< 400$).
   - Maintained `AdvancedTuningScreen.kt` at 391 LOC and `AdvancedTuningDialog.kt` at 377 LOC.
   - All files under `ui/settings/tuning/` strictly satisfy `AdvancedTuningModularityTest`.

---

## 3. Verification & Test Evidence

| Test Suite | Scope | Target | Result |
| :--- | :--- | :--- | :--- |
| `RadarAccordionSectionTest` | JVM Unit | Enum order, subtitle states, string resource | **PASS** (6/6 tests) |
| `AdvancedTuningScreenTest` | Structural | 3-block sequence, scaffold, collapsed state | **PASS** (4/4 tests) |
| `AdvancedTuningModularityTest`| Modularity | All tuning files $< 400$ LOC | **PASS** (2/2 tests) |
| `TranslationParityTest` | Localization | 9-language translation parity | **PASS** (100% parity) |
| `verify_requirement_governance.py` | Governance | Living doc tables, 0 islands | **PASS** (100% parity) |

---

## 4. Conclusion & Next Steps
`RadarSettingsSection` is fully mounted and production-reachable in Advanced Settings. Ready for Gate 5 Review by agent2 and squash merge to `sprint/2026-46.2`.
