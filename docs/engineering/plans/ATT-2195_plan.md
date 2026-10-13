# Stage 3 Implementation Plan: ATT-2195 - Fix Missing Bullet Point Line Breaks in Cluster Info Explanation Dialog

**Ticket**: [ATT-2195](https://rainerblind.atlassian.net/browse/ATT-2195)  
**Sub-task**: [ATT-2368](https://rainerblind.atlassian.net/browse/ATT-2368) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-269`  
**Test Mapping**: `TST-UI-228`  
**Branch**: `feature/ATT-2195`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design & System Decomposition (SWE.2)

### 1.1 Architectural Boundary
* **Layer**: Android Resource Packaging & Presentation Layer.
* **Component Affected**:
  - `cluster_info_fingerprint_desc` across `app/src/main/res/values*/strings.xml`.
  - Rendered by `com.atrainingtracker.trainingtracker.ui.clusters.ClusterInfoDialog.kt` (`AppModalBottomSheet`).
* **Concurrency & Persistence**:
  - Zero database mutations, background worker interactions, or threading concerns.
  - Strictly confined to Android resource compilation and declarative string rendering.

---

## 2. Atomic Step Sequencing

### Step 1: English (`res/values/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 2: German (`res/values-de/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 3: Spanish (`res/values-es/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 4: French (`res/values-fr/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 5: Italian (`res/values-it/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 6: Japanese (`res/values-ja/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 7: Dutch (`res/values-nl/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 8: Polish (`res/values-pl/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 9: Portuguese (`res/values-pt/strings.xml`)
* Replace `&#10;` with literal `\n` in `cluster_info_fingerprint_desc`.

### Step 10: Implement Automated Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialogContractTest.kt`
* **Test Cases**:
  - `testClusterInfoFingerprintDesc_existsAcrossAll9Locales`: Confirms presence in all 9 directories.
  - `testClusterInfoFingerprintDesc_containsNoXmlNumericEntities`: Asserts `!content.contains("&#10;")`.
  - `testClusterInfoFingerprintDesc_containsLiteralNewlineEscapes`: Asserts count of `\n` $\ge 4$ in each locale.

### Step 11: Execute Targeted Unit Tests
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterInfoDialogContractTest"
  ```

---

## 3. Invariant Protection & Rollback Safety

1. **Exact Translation Preservation**: Wording, terminology, and existing XML entity escapes (e.g. `&amp;`) are preserved intact.
2. **Tolerance Value Invariance**: Standard tolerances (200 m, 400 m, 20%) are preserved across all language packs.
3. **Mandatory Programmatic Pre-Check**: Before calling code edit tools in Stage 4, confirm `python3 tools/jira_util.py check-gate ATT-2368` exits with code 0 (`GATE_PASSED: ATT-2368 is Erledigt`).
