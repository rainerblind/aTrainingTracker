# Schichtbetrieb nach Höchstmaß

**Document ID**: `DOC-MGT-SCHICHT`  
**Document Identifier**: `SCHICHT-OPS-ATT`  
**Standard**: ASPICE 4.0 `MAN.3` / `SUP.1` / `SUP.8` (Continuous Multi-Agent Shift Operations)  
**Target Release**: `V5.0.0` | **Version**: 1.0.2  
**Status**: Reviewed | **Approver**: Pending PO Sign-Off | **Date**: 2026-11-19  
**Governing Rules**: [`.agents/rules/aspice_governance.md`](../.agents/rules/aspice_governance.md) (`Rule 2`, `Rule 7`, `Rule 9`) & [`.agents/skills/sprint-planner/SKILL.md`](../.agents/skills/sprint-planner/SKILL.md)  

---

> **Leitmotiv:** *Höchstmaß* definiert den Qualitäts- und ASPICE-Anspruch – der *Schichtbetrieb* liefert den Takt und die Organisation für den kontinuierlichen Multi-Agenten-Betrieb.

Dieses Dokument beschreibt das verbindliche **Schicht-Modell**, die **3-Lane-Ausführung** und das **Schichtwechsel-Protokoll** für den **Schichtbetrieb nach Höchstmaß**. Ziel ist eine nahtlose, ASPICE-konforme Lieferung rund um die Uhr ohne Context Rot.

---

## 0. Einordnung: Die 5 Arbeitsstufen

Der **Schichtbetrieb nach Höchstmaß** bildet die fünfte und höchste Ausbaustufe der Zusammenarbeit mit KI-Agenten:

| Stufe | Steuerung | Qualitäts- & Prozessanspruch |
| :--- | :--- | :--- |
| **1. Vibe-Coding** | Mensch / einzelner Agent ad-hoc | Frei Schnauze – ein Agent baut drauflos, bis es irgendwie läuft. |
| **2. Augenmaß** | Mensch + mehrere Agenten | Ohne Prozess-Ballast, aber mit gezielter **Zweitmeinung** anderer Agenten, bis das Gefühl stimmt. |
| **3. Sorgfältig** | Mensch steuert Ticket-für-Ticket | Einzelne Tickets ordentlich, getestet und sauber abarbeiten – ohne das schwere ASPICE-Gerüst. |
| **4. Höchstmaß** | **Mensch steuert den Sprint** | Volle ASPICE-Strenge: Stages, Gates, Traceability und unabhängige Audits im menschlich geführten Sprint. |
| **5. Schichtbetrieb** *(nach Höchstmaß)* | **Schichtführer (Agent) steuert den Betrieb** | Volle Höchstmaß-Qualität im kontinuierlichen Multi-Agenten-Betrieb (`01_Früh`, `02_Spät`, `03_Nacht`, 3 parallele Lanes, direkter Schichtwechsel). |

---

## 1. Takt, Schichten & Parallelität (`05:00` / `15:00` / `22:00`)

Jede Schicht entspricht einem eigenen Jira-Sprint (`YYYY-MM-DD_0x_Schichtname`) und einem dedizierten Integrations-Branch `sprint/<SCHICHT_NAME>` ab `develop`:

| Schicht | Zeitfenster (Lokalzeit) | Dauer | Namenskonvention |
| :--- | :--- | :---: | :--- |
| **`01_Früh`** | `05:00 – 15:00` | 10 h | `YYYY-MM-DD_01_Früh` |
| **`02_Spät`** | `15:00 – 22:00` | 7 h | `YYYY-MM-DD_02_Spät` |
| **`03_Nacht`** | `22:00 – 05:00` | 7 h | `YYYY-MM-DD_03_Nacht` |

* **3 parallele Lanes (`Rule 7.2`, `Rule 9.1`)**: Der Betrieb läuft standardmäßig auf **3 disjunkten Epic-Lanes** (z. B. `Lane A`: `ATT-8138` ASPICE/Tooling + `Lane B` & `Lane C`: disjunkte Produkt-Epics aus `Vom PO Freigegeben` ID `447` in strikter `ORDER BY rank ASC`-Reihenfolge).
* **Modell-Routing (`Rule 7.2`)**: Routine-Aufgaben in Stages 1–5 sowie unkritische Stage-6-Reviews nutzen **`Flash`** (`Model: "flash"`). **`Pro` / Gemini 4 Neon** (`Model: "inherit"` bei Neon-Koordinator, sonst `Model: "pro"`) wird **ausschließlich für komplexe oder grafische Aufgaben** eingesetzt (High-Risk / Multi-Component / Concurrency / DB / Sensor-Protokolle, Bounce-2–3-Eskalationen oder grafische UI/Compose/Canvas/Chart/Icon-Tickets mit `Rule 8.5`).
* **Eskalation ohne Blockade (`Rule 6.1`)**: Bei echten Unklarheiten oder Human-Gates wird das Subtask mit `Question for Human: ...` an `human` zugewiesen und die Lane arbeitet sofort am nächsten Ticket weiter.

---

## 2. Rolle & Grenzen des Schichtführers

1. **Ein Schichtführer zur gleichen Zeit**: Zwei Schichtführer arbeiten niemals parallel auf demselben `sprint/<SCHICHT_NAME>`-Branch. Subagenten werden in isolierten `Workspace: "share"`-Worktrees geführt und sauber abgeschlossen.
2. **Single Source of Truth statt Chat-Kontext**: Der übernehmende Schichtführer liest nicht den gesamten alten Chatverlauf, sondern verifiziert den Realstand direkt über Git (`develop`, `sprint/*`, `feature/*`), Jira (`jira_util.py list`), die letzte Schließ-Retro (`docs/engineering/retros/Sprint_Review_and_Retro_*.md`) und das Risikoregister (`docs/management/risk_register.md`).
3. **Verweise statt Kopien**: Übergabe-Einträge enthalten Ticket-IDs, Branch-Namen, Commit-Hashes, Test-Scopes und Dateipfade – keine duplizierten Code- oder Ticket-Kopien.

---

## 3. Der Schichtwechsel um `05:00`, `15:00` und `22:00`

### Phase 1: Abschluss der laufenden Schicht (Wrap-up & Lean Tier-2)
1. **Merge-Freeze**: Merges auf `sprint/<CLOSING_SCHICHT>` einfrieren. Offene `feature/<KEY>`-Branches bleiben für den nahtlosen Übertrag (`carryover-wip`) intakt.
2. **Lean Tier-2 Integration Gate (`Rule 7.5`)**: Auf `sprint/<CLOSING_SCHICHT>` ausführen (inkrementell mit `--rerun`, niemals `--rerun-tasks`; Stage 4/Gate 4/Stage 6 nutzen ebenfalls ausschließlich inkrementelles `--rerun --tests <TicketSuites>`, Stage 5/Gate 5 ohne redundanten Gradle-Lauf bei unverändertem Code):
   - `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --rerun --tests <SprintTouchedSuites> --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.integration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --max-workers=1`
   - `python3 tools/verify_requirement_governance.py`, Python-Tests (`tools/test_*.py`), Hoechstmass- & Safety-Linter.
   - *(Die vollständige `:app:testDebugUnitTest`-Suite sowie `:app:lintDebug`, `:app:detekt` und `:app:koverXmlReportDebug` laufen **1× wöchentlich** im Rahmen des Weekly Dual-Vendor Code Review / ASPICE Audit sowie bei Releases.)*
3. **Best-Effort Phone Deploy (`Rule 7.6`)**: Falls ein physisches `Pixel 10` verbunden ist, `ANDROID_SERIAL=<serial> ./gradlew installDebug` ausführen (sonst ohne Blockade protokollieren und weiter).
4. **Evidenz, Retro, Wochen-Rollup & Merge nach `develop`**: `sprint-metrics --record --post-to-retro` (postet automatisch auch das kompakte Scorecard-Rollup auf das rollierende `Wochen Review & Retro`-Ticket), `export-gates`, `export-test-evidence --no-kover`, `--as agent2 qa-audit`, `record-review`, `docs/management/risk_register.md` aktualisieren, `Sprint_Review_and_Retro_<KEY>.md` (als persistentes Schichtbuch) schreiben und via `python3 tools/jira_util.py --as coordinator post-weekly-rollup --retro-file docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md` alle Retro-Erkenntnisse, Vorschläge und QA-Befunde automatisch ins `Wochen Review & Retro` spiegeln, committen, `sprint/<CLOSING_SCHICHT>` mit `--no-ff` in `develop` mergen, unveränderlich taggen (`baseline_manager.py tag` + `manifest`), und `Final Review (Human)`-Tickets via `archive-po-acceptance` nach `Bereit für PO Abnahme` (`453`) verschieben.

### Phase 2: Direkter Start der nächsten Schicht (`Direct Next-Schicht Start`)
Direkt im Anschluss an den Abschluss (**ohne Leerlauf**) startet die neue Schicht:
1. Neuen Sprint-Branch `sprint/<NEW_SCHICHT>` von aktuellem `develop` abzweigen (`git checkout -b sprint/<NEW_SCHICHT> develop`).
2. Laufende WIP-Tickets direkt übernehmen (`python3 tools/jira_util.py --as coordinator carryover-wip <OLD_SPRINT> <NEW_SPRINT>`).
3. `ensure-retro`, `ensure-weekly-retro`, `ensure-weekly-reviews` ausführen und freie Lane-Kapazität auf **3 Lanes** auffüllen (`python3 tools/jira_util.py --as coordinator pack-sprint <NEW_SPRINT_ID> --lanes 3 --max-sp-per-lane 8`).
4. Ceremony-1-Eintrag in `docs/management/risk_register.md` ergänzen und Ausführung auf den 3 Lanes aufnehmen (bzw. sauber pausieren, falls sowohl `447` als auch WIP leer sind).

### Phase 3: Antrittsroutine bei neuem Session-Kontext
Startet ein neuer Schichtführer in einem frischen Kontextfenster, prüft er zuerst:
1. `.agents/rules/aspice_governance.md` (`Rule 1.1`)
2. `git status` / `git branch` sowie `python3 tools/jira_util.py list`
3. Letzte `Sprint_Review_and_Retro_*.md` + `docs/management/risk_register.md` + `docs/engineering/qa_audits/*.md`

---

## 4. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | Sprint Coordinator (`ATT-8944`) | `Interactive-PO` | Aligned `schichtbetrieb.md` with live `05:00`/`15:00`/`22:00` `Schicht` sprints, 3 parallel lanes, `Pro` routing for complex/graphical tasks only, Lean Tier-2 at `Schichtwechsel` + weekly full suite, and direct next-`Schicht` start. |
| `1.0.1` | 2026-11-19 | Review Coordinator (`Interactive-PO`) | `ATT-8223` | Mandated automatic per-Schicht weekly rollup into the rolling `Wochen Review & Retro` ticket (`sprint-metrics --post-to-retro` scorecard rollup + `post-weekly-rollup --retro-file ...` qualitative Retro/QA rollup) during Schichtwechsel Phase 1. |
| `1.0.2` | 2026-11-19 | Sprint Coordinator (`Interactive-PO`) | `ATT-9055` | Aligned Option 1 Neon (`inherit`)/`flash` routing and incremental `--rerun` (never `--rerun-tasks`) Tier-1/Lean Tier-2 execution across `Rule 7.1/7.2/7.5`, Stage 4–6 skills, and `schichtbetrieb.md`. |
