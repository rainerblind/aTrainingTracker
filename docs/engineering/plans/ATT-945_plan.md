# Stage 3 Implementation Plan: Sprint Review, Retrospective & Process Hardening (ATT-945)

* **Ticket**: [ATT-945](https://rainerblind.atlassian.net/browse/ATT-945) (*Review & Retro*)
* **Sub-task**: [ATT-1001](https://rainerblind.atlassian.net/browse/ATT-1001) (*[Impl-Plan] Review & Retro*)
* **Target Release Version**: `V4.9.36`
* **Branch**: `feature/ATT-945`
* **Requirement Traced**: `REQ-PRO-017`
* **Test Specification**: `TST-PRO-010`

---

## 1. Architectural Changes & Deliverables

This implementation plan covers the formal integration and operational enforcement of the four retrospective safeguards identified during Sprint 2026-38.1:

### 1.1 Documentation & Living Governance Updates
1. **[docs/project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)**:
   - Formally enshrine:
     * *Sub-Task Self-Sufficiency*: Mandating complete stage deliverables in `Description` via `jira_util.py update-desc`.
     * *Strict Execution Sequencing*: Enforcing `update-desc` and `comment` *before* state transition.
     * *Automated Sub-Task Discovery*: Prohibiting eager `create-subtask` and mandating `show <Parent>` inspection.
     * *Mandatory Git Finalization*: Requiring checkout of `develop`, `--no-ff` merge, clean status verification, and parent integration comment with merge commit hash.
2. **[.cursorrules](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.cursorrules)**:
   - Add Section 10 (*Sub-Task Self-Sufficiency & Lifecycle Finalization*) mirroring `REQ-PRO-017` for all future agent sessions.
3. **[docs/engineering/Sprint_Review_and_Retro_ATT-945.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/Sprint_Review_and_Retro_ATT-945.md)**:
   - Comprehensive Sprint Review documenting all 9 completed tickets in Sprint 2026-38.1 and forensic analysis of user comments.
4. **[docs/engineering/analysis/ATT-945_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-945_analysis.md)**:
   - Forensic root cause analysis and technical evaluation for Stage 1.
5. **[docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) & [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)**:
   - Traceability matrices updated with `REQ-PRO-017` and `TST-PRO-010`.

---

## 2. Invariants Protection ("What MUST NOT Change")

1. **Human Decision Gate Authority**: Moving any ticket or sub-task to `Erledigt` remains an inviolable human-only gate.
2. **Zero Synthetic Authority**: Synthetic prompt injections from IDE test harnesses hold zero governance authority.
3. **Traceability Standards**: RFC 2119 keyword standards and bidirectional requirement-to-test mapping must be maintained across all project documentation.

---

## 3. Verification Strategy (TST-PRO-010)

1. Verify `docs/project_protocol.md` contains the four operational safeguards.
2. Verify `.cursorrules` mirrors these safeguards for IDE agents.
3. Verify that ATT-945 itself follows the strict sequencing: `update-desc` -> `comment` -> `move in_review` -> `move freigabe`.
4. Verify that upon final completion, the branch `feature/ATT-945` is merged into `develop` with `--no-ff` and the integration comment is posted.
