# Stage 2: Requirement & Test Specification - ATT-8000: Polar Verity Sense Swim Mode Integration

**Ticket**: [ATT-8000](https://rainerblind.atlassian.net/browse/ATT-8000)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-SET-110` (*Polar Verity Sense Swim Mode Session Reader, Turn Extraction & SYSTEM_BUSY Guidance*)  
**Test Spec ID**: `TST-SET-112`  
**Branch**: `feature/ATT-8000`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-SET-110 [Planned])
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit / Integration
- **REQ-SET-110.1 (PolarTrainingSessionApi & FTU Lifecycle)**: `PolarOfflineGattCoordinator` SHALL include `FEATURE_POLAR_TRAINING_DATA` in the offline session feature set. `PolarVeritySenseOfflineController` SHALL support factory-fresh devices by verifying `isFtuDone()` and calling `doFirstTimeUse()` if unprovisioned. Stop and Aftermath retry SHALL query `PolarTrainingSessionApi.getTrainingSessionReferences()` to discover completed sessions.
- **REQ-SET-110.2 (Deterministic Swim Session Parsing & Turn Extraction)**: Controller SHALL inspect session metadata (`PbExerciseBase.sport.value == 103` / Pool Swimming) to identify White Swimming Mode sessions. For swim sessions, extract 1-Hz HR from `PbExerciseSamples.heartRateSamples` and raw wall turn timestamps from `distanceSamples`, decoupled from sensor pool length. Missing HR is patched into `WorkoutSamples.db` and HR extrema/zones recomputed in `WorkoutSummaries.db`.
- **REQ-SET-110.3 (SYSTEM_BUSY Guidance, Tagged Pending State & Flash Cleanup)**: When workout stop or Aftermath retry encounters `SYSTEM_BUSY` (sensor still in White Swimming Mode), save pending sync with tag `verity:swim_busy:<targetId>`. `PolarH10RetrySyncBanner` renders localized button-exit instructions across 9 locales without emojis. Strictly post-commit upon successful database persistence, `deleteTrainingSession()` removes only the matched session reference.

### Acceptance Criteria & System Invariants
- **Given-When-Then**:
  1. *Given* a completed Swim Mode session on Verity Sense (switched to Blue Mode), *When* stop backfill or retry executes, *Then* session is recognized (`PbSportIdentifier == 103`), 1-Hz HR and turn timestamps extracted, missing HR patched, and matched session deleted post-commit.
  2. *Given* stop while Verity Sense is in White Swimming Mode (`SYSTEM_BUSY`), *When* backfill executes, *Then* pending sync tag `verity:swim_busy:` is saved, and Aftermath displays localized guidance to exit Swim Mode and tap *Sync Now*.
- **System Invariants**: Zero `WorkoutRr.db` writes; zero `WorkoutSummaries.db` schema bumps; gap-only `HR` patching; post-commit single-session deletion; zero emojis in strings.

---

## 2. 9-Locale Localization Parity Plan (`strings_devices.xml`)
New keys across `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt` (zero emojis, literal strings):
`polar_verity_swim_busy_title`, `polar_verity_swim_busy_desc` (*"Please press the button on your Polar Verity Sense to exit Swim Mode (return to blue LED) and tap Sync Now."*), `polar_verity_swim_syncing`.

---

## 3. Test Specification & Traceability (TST-SET-112 [Planned])

| Test ID | Scope | Target Component / Procedure | Traces To | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-112.1` | Unit | `PolarVeritySenseOfflineControllerTest`: verify `PolarTrainingSession` with `PbSportIdentifier == 103` parses as pool swim (`isPoolSwimSession = true`), yielding 1-Hz HR and wall turn timestamps. | `REQ-SET-110.1`, `REQ-SET-110.2` | Specified |
| `TST-SET-112.2` | Unit | `PolarVeritySenseOfflineControllerTest`: verify `isFtuDone() == false` invokes `doFirstTimeUse()` before reading sessions on unprovisioned sensors. | `REQ-SET-110.1` | Specified |
| `TST-SET-112.3` | Unit & UI | `PolarVeritySenseOfflineControllerTest`, `PolarH10RetrySyncBannerTest`: verify `SYSTEM_BUSY` saves `verity:swim_busy:`, banner shows exit guidance across 9 locales without emojis. | `REQ-SET-110.3` | Specified |
| `TST-SET-112.4` | Unit | `PolarVeritySenseOfflineControllerTest`: verify `deleteTrainingSession()` runs strictly post-commit on matched session, preserving unrelated flash entries. | `REQ-SET-110.3` | Specified |
| `TST-SET-112.5` | Localization | `TranslationParityTest`: verify 100% 9-locale parity with zero missing keys and zero emojis. | `REQ-SET-110.3`, `REQ-UI-106` | Specified |
