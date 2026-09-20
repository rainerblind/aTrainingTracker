# Sprint Review & Retrospective: Sprint 2026-38.3 (Release V4.9.37)

* **Ticket**: [ATT-1126](https://rainerblind.atlassian.net/browse/ATT-1126) (*Review & Retro*)
* **Sprint**: `2026-38.3`
* **Target Release Version**: `V4.9.37`
* **Branch**: `feature/ATT-1126`

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-38.3**, 11 feature and improvement tickets were developed, verified through the unified 5-stage ASPICE process, backed by automated unit tests, and cleanly integrated into `develop` for release `V4.9.37`:

### 1.1 Summary of Shipped Tickets
| Ticket | Type | Summary | Key Impact & Solution | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **ATT-1118** | Feature | Properly handle retired equipment in UI and pickers | Preserved historical workout links while cleanly filtering out retired equipment from active selection dialogs. | `REQ-SET-085`, `TST-SET-070` |
| **ATT-1133** | Bug | TCX Import: Manual cluster selection when similarity < 1 | Corrected cluster similarity thresholding so manual user prompts are suppressed when similarity score is below 1.0. | `REQ-MIG-030`, `TST-MIG-028` |
| **ATT-1138** | Bug | Point of max line distance should remain average | Corrected workout clustering geometry so the point of maximum line distance remains the mathematical mean of all clustered workouts. | `REQ-SET-086`, `TST-SET-071` |
| **ATT-1107** | Bug | ANR / Crash when showing multi-year periods | Eliminated UI freeze/ANR on large historical periods via asynchronous chunked loading and non-blocking aggregation. | `REQ-UI-152`, `TST-UI-105` |
| **ATT-1116** | Feature | Import GPX files | Added comprehensive GPX route import parsing track points, elevation, and timestamps into the local route catalog. | `REQ-ROU-007`, `TST-ROU-007` |
| **ATT-912** | Feature | Strava Segments: Update PB in DB after upload | Ingested PR/KOM segment feedback after workout upload and celebrated achievements with a themed UI banner. | `REQ-EXP-010`, `TST-EXP-007` |
| **ATT-913** | Feature | Strava Segments: Periodically fetch from Strava | Integrated Android `WorkManager` for automated background synchronization of starred Strava segments. | `REQ-EXP-011`, `TST-EXP-008` |
| **ATT-914** | Feature | Strava Routes: Periodically fetch from Strava | Integrated Android `WorkManager` for automated background synchronization of Strava routes. | `REQ-EXP-012`, `TST-EXP-009` |
| **ATT-1078** | Improvement | Delete all Strava data when token is no longer valid | Implemented `StravaDataPurgeManager` to purge Strava tokens, databases, and routes on token invalidation/deauth (Section 7.4). | `REQ-EXT-009`, `TST-EXT-006` |
| **ATT-1177** | Improvement | 7-day TTL cache retention & orphan pruning | Added `synced_at` columns, 7-day cache eviction, and cascade orphan cleanup for Strava routes and segments (Section 6.2). | `REQ-EXP-011`, `REQ-EXP-012` |
| **ATT-1178** | Improvement | Sync Strava Segments and Routes every day | Enforced fixed 24-hour periodic sync and eliminated user-facing toggles ("no need to ask the user") to guarantee fresh data. | `REQ-EXP-011`, `REQ-EXP-012`, `TST-EXP-008`, `TST-EXP-009` |

---

## 2. Retrospective: Analysis of Observations & Countermeasures

### 2.1 Observation 1: Conflicting IDE Review Prompts (`RequestFeedback: true`)
* **Observation**:
  During Stage 3 implementation planning, setting `RequestFeedback: true` in the IDE artifact metadata triggered the IDE harness review hook, causing conflicting "Proceed" / approval prompts in the IDE UI. This confused the user, who had already reviewed and approved the plan in Jira.
* **Root Cause**:
  The agent set `RequestFeedback: true` instead of following the strict protocol rule that reserves human approval exclusively to Jira.
* **Countermeasure & Protocol Enforcement**:
  All IDE artifact calls (`write_to_file`, `replace_file_content`, `multi_replace_file_content`) must strictly set `RequestFeedback: false`. Jira is the sole authoritative Human Decision Gate.

### 2.2 Observation 2: Method Signature Mismatches in Unit Test Mocks
* **Observation**:
  During Stage 4 test updates, mock assertions were initially written calling `pruneStaleRoutes()` and `pruneStaleSegments()` instead of the actual repository methods `pruneExpiredRoutes()` and `pruneExpiredSegments()`, causing an unnecessary compile failure.
* **Root Cause**:
  Relying on memory rather than actively inspecting the target repository source code before formulating mock assertions.
* **Countermeasure & Protocol Enforcement**:
  Added Section *4. Active Interface Verification Before Mocking* to `docs/project_protocol.md`: Agents must inspect method signatures in active code before writing mock assertions.

### 2.3 Observation 3: Kotlin Daemon / Gradle Compilation Resilience
* **Observation**:
  During test execution, Kotlin daemon experienced file-lock/backup collisions in `/tmp`, causing fallback compilation and slowing execution down.
* **Root Cause**:
  Lingering daemon processes holding stale socket connections or backup locks.
* **Countermeasure & Protocol Enforcement**:
  Added Section *5. Gradle & Kotlin Daemon Recovery Protocol* to `docs/project_protocol.md`: Proactively run `./gradlew --stop` whenever compiler daemon fallback messages appear.

---

## 3. Protocol Hardening Summary
The following permanent additions were committed to `docs/project_protocol.md`:
1. **Rule 4 (Unit Testing)**: Active Interface Verification Before Mocking.
2. **Rule 5 (Unit Testing)**: Gradle & Kotlin Daemon Recovery Protocol (`./gradlew --stop`).
3. **Rule Reinforcement**: Strict `RequestFeedback: false` on all IDE markdown artifacts.
