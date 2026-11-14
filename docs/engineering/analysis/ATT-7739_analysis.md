# Stage 1 Analysis: ATT-7739 - Morning HRV: Remove 'Entleeren der Blase' / Bladder Voiding Phrasing

**Ticket**: [ATT-7739](https://rainerblind.atlassian.net/browse/ATT-7739)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Branch**: `feat/ATT-7739` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.5 review of `ATT-7533`, the instruction string `morning_hrv_instructions_desc` was flagged for indelicate phrasing (*"nach dem Entleeren der Blase"* in DE, *"after bladder voiding"* in EN, and literal translations in the other 7 locales). While clinical resting HRV protocols standardize bladder state to avoid sympathetic distention artifacts, displaying explicit bladder emptying instructions in the primary user-facing waking card degrades UI elegance and UX. The string should be simplified across all 9 supported locales to a clean, rest-focused phrasing (e.g. *"Direkt nach dem Aufwachen in Ruhe messen..."*).

---

## 2. Root Cause Analysis & Independent Verification
* **RCA (String Resource Phrasing)**: `morning_hrv_instructions_desc` was introduced in `ATT-3556` (`dc034c08`) in `strings_hrv.xml` across all 9 locale directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **UI Consumer Verification**:
  * Consumed in `MorningHrvCheckScreen.kt` line 334 inside `WakingProtocolChecklistCard()`:
    `Text(text = stringResource(R.string.morning_hrv_instructions_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)`
* **Codebase Scan**:
  * An exhaustive grep confirmed that `morning_hrv_instructions_desc` is the single place where bladder voiding terminology is present in the app resources.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (1 SP - Trivial)**:
  1. Update `morning_hrv_instructions_desc` in `strings_hrv.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) replacing bladder voiding phrasing with rest-oriented morning guidance.
  2. Add contract test assertions in `TranslationParityTest.kt` verifying all 9 locales contain localized non-empty translations free of bladder terms.
  3. Update living documentation in `docs/requirements/req_aftermath_periods.md` (`REQ-PER-032.6`) and `docs/tests/tst_aftermath_periods.md` (`TST-PER-040.6`).
* **Out-of-Scope Non-Goals**:
  * No layout or styling modifications to `MorningHrvCheckScreen.kt` or `WakingProtocolChecklistCard`.
  * No renaming of the resource key `morning_hrv_instructions_desc`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-PER-032.6` (Localization Parity & Waking Protocol Strings).
* **Historical Origin & Commit Trace**: Introduced in `ATT-3556` (commit `dc034c08`); recorded in Sprint 2026-46.5 retrospective `ATT-7525`.
* **Root Reason for Existing Formulation**: Literal translation of Esco & Flatt (2014) clinical protocol specifications.
* **Preservation of Core Invariants**: Waking rest, motionless posture, normal breathing, and posture consistency for reliable SWC baseline comparisons are 100% preserved. 9-language parity is strictly maintained.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-PER-032.6`.
* **Affected Architecture Elements**: `app/src/main/res/values*/strings_hrv.xml` (all 9 locale shards).
* **Verification Impact & Test Surface**: `TranslationParityTest.kt`, `processDebugResources`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low Risk (1 SP). Resource string wording refinement only. Zero Kotlin bytecode or database schema changes.

---

## 6. Architectural Strategy & High-Level Solution
Update `morning_hrv_instructions_desc` across all 9 locales:
* **DE**: `Direkt nach dem Aufwachen in Ruhe messen. Ruhig bleiben, normal atmen und nicht sprechen. Haltung beibehalten für verlässliche SWC-Vergleiche.`
* **EN**: `Measure at rest directly upon waking. Remain still and breathe normally without speaking. Maintain posture consistency for reliable SWC baseline comparisons.`
* **ES**: `Medir en reposo directamente al despertar. Permanezca quieto y respire con normalidad sin hablar. Mantenga la misma postura para comparaciones basales fiables.`
* **FR**: `Mesurer au repos directement au réveil. Rester immobile et respirer normalement sans parler. Garder la même posture pour des comparaisons fiables.`
* **IT**: `Misurare a riposo direttamente al risveglio. Rimanere fermi e respirare normalmente senza parlare. Mantenere la postura per confronti affidabili.`
* **JA**: `起床後、安静にして測定してください。会話を控え、静かに自然な呼吸を保ちます。正確なSWC比較のため常に同じ姿勢を維持してください。`
* **NL**: `Meet direct na het ontwaken in rust. Blijf stil liggen en adem rustig zonder te spreken. Behoud dezelfde houding voor betrouwbare SWC-vergelijkingen.`
* **PL**: `Wykonaj pomiar w spoczynku bezpośrednio po przebudzeniu. Pozostań w bezruchu i oddychaj normalnie bez mówienia. Zachowaj stałą postawę dla wiarygodnych porównań SWC.`
* **PT**: `Meça em repouso diretamente ao acordar. Permaneça imóvel e respire normalmente sem falar. Mantenha a mesma postura para comparações basais confiáveis.`

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: 100% 9-language key & specifier parity; zero regression in screen layout or translation tests.
* **Risk Rating**: **LOW** (pure XML localization refinement).
