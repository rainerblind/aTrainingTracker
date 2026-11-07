# Stage 1: Problem Domain & Root Cause Analysis - ATT-5679

**Ticket**: [ATT-5679](https://rainerblind.atlassian.net/browse/ATT-5679)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5679`  
**Requirement Mapping**: `REQ-UI-293` (refining `REQ-MIG-048`), `REQ-UI-458` (Card Title Action Parity)  
**Test Mapping**: `TST-UI-253`, `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
On the **„Import“** tab of *Import & Backup* (`ImportBackupTabsScreen.kt`), format import cards display actionable titles with explicit action verbs:
* Card 2 (`import_fit_title`): *„FIT-Dateien importieren“* (*„Import FIT Files“*)
* Card 3 (`import_tcx_title`): *„TCX-Dateien importieren“* (*„Import TCX Files“*)
* Card 4 (`import_gpx_title`): *„GPX-Dateien importieren“* (*„Import GPX Files“*)

**Usability Defect**: In contrast, the top card (`import_strava_archive_title`) is currently titled passively without an action verb:
* Current: *„Strava-Bulk-Export (ZIP)“* (*„Strava Bulk Export (ZIP)“*)

When skimming the screen, the word *„Export“* without the governing verb *„importieren“* (*„Import …“*) creates cognitive confusion, causing athletes to mistake the card for an export generator rather than an archive ingestion tool (verified in attachment `import_strava_bulk_screenshot.png`).

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: Refines `REQ-UI-293` (*Format-Partitioned Card Architecture in ImportBackupTabsScreen*) and `REQ-MIG-048` (*Strava Bulk Export Ingestion*). Targets `app/src/main/res/values*/strings.xml` across all 9 locales.
* **Historical Origin & Commit Trace**: `ATT-2623` (Sprint 2026-40.13, `0f40ce7d`: format cards layout) and `ATT-4957` (Sprint 2026-45.6, `d814238e`: Strava bulk archive card introduction).
* **Root Reason for Existing Formulation**: In `ATT-4957`, the card title was named directly after the artifact format (*„Strava Bulk Export (ZIP)“*), omitting the action verb that was consistently used on adjacent format cards.
* **Preservation of Core Invariants**:
  1. *Resource Key Stability*: String resource key `import_strava_archive_title` remains identical.
  2. *9-Language Parity*: Action verb added across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  3. *Emoji Prohibition (`ATT-5400`)*: Zero decorative inline Unicode emojis.
  4. *Zero Logic/UI Regressions*: `StravaArchiveImportCard` composable and ingestion engine unchanged.

---

## 3. Scope Bounding & Technical Architecture
* **In-Scope**:
  1. Update `import_strava_archive_title` in all 9 locale `strings.xml` files with explicit action verb phrasing (*„... importieren“* / *„Import ...“*).
  2. Extend contract test (`ImportFormatCardsContractTest.kt` or localization parity test) to verify the clarified title across all locales.
* **Out-of-Scope**: Modifications to `StravaBulkExportImporter.kt`, button labels, or dialog architecture.
* **Complexity Rating**: **1 SP (Trivial)**. Pure localization string clarification across 9 supported languages.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `values*/strings.xml` (9 locales) | Update `import_strava_archive_title` string resources. |
| `ImportBackupTabsScreen.kt` | Consumes updated resource without code changes. |
| Tests (`TranslationParityTest`, `ImportFormatCardsContractTest`) | Verify 100% 9-language translation parity and XML compilation. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Resource key unchanged; grammatical symmetry across all format cards; 100% 9-locale parity.
* **Risk Rating**: **LOW** (Zero risk to runtime logic, database schemas, or ingestion engines).
