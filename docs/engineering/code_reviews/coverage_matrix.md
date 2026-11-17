# Codebase Audit Coverage Matrix: Weekly Dual-Vendor Code & Architecture Reviews

**Standard**: ASPICE 4.0 `SWE.2`, `SWE.3.BP4`, `SWE.4.BP2`, `SUP.1.BP1/BP3`, `GP 2.1.6`  
**Governing Ticket**: [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213) | **Requirement**: `REQ-PRO-095`  
**Cadence**: Weekly ("in der Regel wöchentlich", $\le 7$ days)  
**Dual Vendors**: Current Top Model of Google Gemini + Current Top Model of Anthropic Claude (REQ-PRO-095.1 / ATT-8426)  
**Scope**: Full subsystem / package slice audits of the existing codebase alongside recent git diffs until 100% of packages are reviewed end-to-end (cycling continuously).

---

## Subsystem Package Coverage Matrix

| Subsystem Domain | Target Path(s) | Last Audited Date | Audited Commit SHA | Gemini Model | Claude Model | Status | Active / Next Target |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `tracker/` | `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/` | - | - | - | - | `UNREVIEWED` | `NEXT` |
| `banalservice/` | `app/src/main/java/com/atrainingtracker/banalservice/` | - | - | - | - | `UNREVIEWED` | - |
| `database/` | `app/src/main/java/com/atrainingtracker/trainingtracker/database/` | - | - | - | - | `UNREVIEWED` | - |
| `repositories/` | `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/` | - | - | - | - | `UNREVIEWED` | - |
| `exporter/` | `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/` | - | - | - | - | `UNREVIEWED` | - |
| `cloud/` | `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/` | - | - | - | - | `UNREVIEWED` | - |
| `onlinecommunities/` | `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/` | - | - | - | - | `UNREVIEWED` | - |
| `segments/` | `app/src/main/java/com/atrainingtracker/trainingtracker/segments/` | - | - | - | - | `UNREVIEWED` | - |
| `climbs/` | `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/` | - | - | - | - | `UNREVIEWED` | - |
| `ui/tracking/` | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/` (cockpit, telemetry) | - | - | - | - | `UNREVIEWED` | - |
| `ui/aftermath/` | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/` (detail, summary, charts) | - | - | - | - | `UNREVIEWED` | - |
| `ui/settings/` | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/` (settings, pairing) | - | - | - | - | `UNREVIEWED` | - |
| `tools/` | `tools/` | - | - | - | - | `UNREVIEWED` | - |
| `test/` | `app/src/test/`, `app/src/androidTest/` | - | - | - | - | `UNREVIEWED` | - |

---

## Review Lifecycle & Status Transition
1. **Target Selection**: Automated tooling (`jira_util.py ensure-weekly-reviews`) scans this matrix in order. The first `UNREVIEWED` subsystem (or, once 100% audited, the subsystem with the oldest `Last Audited Date`) is designated `NEXT` and pre-populated into the generated review ticket.
2. **Review Execution & Claude Non-Simulation Protocol**: Gemini and Claude conduct independent blind reviews covering:
   - Concurrency & Thread Safety (coroutine scopes, dispatchers, synchronized blocks)
   - Resource & Memory Leaks (SQLite cursors, broadcast receivers, SensorManager listeners)
   - Architectural Boundaries & Monolith Complexity (DRY, single responsibility, repository encapsulation)
   - Error Handling & Defensive Fallbacks (no silent exception swallowing)
   - Behavioral Test Coverage & Assertion Validity
   - **Claude Non-Simulation Mandate (ATT-8426)**: Antigravity agents running on Google models SHALL NEVER simulate or fabricate Claude reviews (Finding K2 evidence integrity). If external Claude execution is pending, mark Claude review as `PENDING / EXTERNAL HANDOFF REQUIRED`. Subsystems may be marked `AUDITED` only when both vendor evaluations are completed.
3. **Reconciliation & Ticket Filing**: Findings are reconciled, documented in `YYYY-MM-DD_dual_vendor_code_review.md`, and filed into Jira as Backlog Bug/Improvement tickets (`ATT-*`).
4. **Matrix Update**: The reviewed row is updated to `AUDITED`, updating `Last Audited Date`, `Audited Commit SHA`, and vendor model versions.
