# Stage 3: Implementation Plan - ATT-7739: Morning HRV Instructions Desc Clean Phrasing

**Ticket**: [ATT-7739](https://rainerblind.atlassian.net/browse/ATT-7739)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-PER-032` / `REQ-PER-032.6`  
**Test Mapping**: `TST-PER-040` / `TST-PER-040.6`  
**Branch**: `feat/ATT-7739` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Sprint review feedback of `ATT-7533` in Sprint 2026-46.5 highlighted that `morning_hrv_instructions_desc` in `strings_hrv.xml` contained crude clinical phrasing referring to bladder voiding across all 9 supported locales. This plan specifies the exact step-by-step updates to replace this phrasing with rest-oriented morning waking instructions without altering any Kotlin logic or UI composable layout.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-032.6` (Localization, Waking Instruction Hygiene & Theme Token Centralization)
* **Test Mapping**: `TST-PER-040.6` (`TranslationParityTest.kt`, `processDebugResources`)

---

## 3. System Invariants & Preserved Behavior
1. **100% 9-Language Parity**: All 9 locale shards (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) retain identical key naming and zero format specifier mismatches.
2. **Resource Key Stability**: `morning_hrv_instructions_desc` ID is unchanged; callers in `MorningHrvCheckScreen.kt` remain intact.
3. **Zero Logic or Schema Changes**: No Kotlin bytecode changes in `MorningHrvCheckViewModel` or database schemas.

---

## 4. Proposed Architectural Changes

### Component 1: `app/src/main/res/values*/strings_hrv.xml`
Update `morning_hrv_instructions_desc` across all 9 locales:
* **DE (`values-de`)**: `Direkt nach dem Aufwachen in Ruhe messen. Ruhig bleiben, normal atmen und nicht sprechen. Haltung beibehalten für verlässliche SWC-Vergleiche.`
* **EN (`values`)**: `Measure at rest directly upon waking. Remain still and breathe normally without speaking. Maintain posture consistency for reliable SWC baseline comparisons.`
* **ES (`values-es`)**: `Medir en reposo directamente al despertar. Permanezca quieto y respire con normalidad sin hablar. Mantenga la misma postura para comparaciones basales fiables.`
* **FR (`values-fr`)**: `Mesurer au repos directement au réveil. Rester immobile et respirer normalement sans parler. Garder la même posture pour des comparaisons fiables.`
* **IT (`values-it`)**: `Misurare a riposo direttamente al risveglio. Rimanere fermi e respirare normalmente senza parlare. Mantenere la postura per confronti affidabili.`
* **JA (`values-ja`)**: `起床後、安静にして測定してください。会話を控え、静かに自然な呼吸を保ちます。正確なSWC比較のため常に同じ姿勢を維持してください。`
* **NL (`values-nl`)**: `Meet direct na het ontwaken in rust. Blijf stil liggen en adem rustig zonder te spreken. Behoud dezelfde houding voor betrouwbare SWC-vergelijkingen.`
* **PL (`values-pl`)**: `Wykonaj pomiar w spoczynku bezpośrednio po przebudzeniu. Pozostań w bezruchu i oddychaj normalnie bez mówienia. Zachowaj stałą postawę dla wiarygodnych porównań SWC.`
* **PT (`values-pt`)**: `Meça em repouso diretamente ao acordar. Permaneça imóvel e respire normalmente sem falar. Mantenha a mesma postura para comparações basais confiáveis.`

### Component 2: `TranslationParityTest.kt`
Add contract test assertion `testMorningHrvInstructionsDesc_noBladderPhrasingAcrossAllLocales_TST_PER_040_6` ensuring:
- Presence of string across base and all 8 non-default locales.
- Absence of forbidden bladder voiding keywords across all locales.
- Presence of positive resting keywords across all locales.

### UI Consistency (Rule 23)
* Standard M3 typography (`bodySmall`) inside `WakingProtocolChecklistCard()` with semantic `colorScheme.onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction Blueprint for Flash Agent)

### Step 1: Update XML Resource Strings in All 9 Locales
* Edit `morning_hrv_instructions_desc` in `values/strings_hrv.xml`, `values-de/strings_hrv.xml`, `values-es/strings_hrv.xml`, `values-fr/strings_hrv.xml`, `values-it/strings_hrv.xml`, `values-ja/strings_hrv.xml`, `values-nl/strings_hrv.xml`, `values-pl/strings_hrv.xml`, and `values-pt/strings_hrv.xml`.

### Step 2: Implement Unit Test in `TranslationParityTest.kt`
* Add `@Test fun testMorningHrvInstructionsDesc_noBladderPhrasingAcrossAllLocales_TST_PER_040_6()` verifying absence of bladder terms and presence of rest terms across all 9 locales.

### Step 3: Execute Tier 1 Unit Tests & Resource Compilation
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew processDebugResources`
* Run: `python3 tools/verify_requirement_governance.py`

### Step 4: Visual Verification & Stage 5 Walkthrough
* Deploy or render `MorningHrvCheckScreen` on `emulator-5554` (Pixel 8), capture screenshot in English and German, author `docs/engineering/walkthroughs/ATT-7739_walkthrough.md`, submit Stage 4 and Stage 5.

---

## 6. Verification & Rollback Plan
* **Verification**: `TranslationParityTest.kt` (100% green), `processDebugResources` (exit code 0), and `verify_requirement_governance.py`.
* **Rollback**: Clean revert via `git reset --hard feat/ATT-7739`.
