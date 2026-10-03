# Stage 5: Verification Walkthrough - ATT-2186: Add Origin Source Attribute (Tracked, TCX, GPX, FIT) to Workouts

**Ticket**: [ATT-2186](https://rainerblind.atlassian.net/browse/ATT-2186)  
**Sub-task**: [ATT-2193](https://rainerblind.atlassian.net/browse/ATT-2193) (`[Test]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2186`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary

This walkthrough verifies the completion of [ATT-2186](https://rainerblind.atlassian.net/browse/ATT-2186), fulfilling requirement `REQ-DAT-017` and test specification `TST-DAT-012`.
Workouts now persist and expose an explicit origin source attribute (`TRACKED`, `TCX`, `GPX`, `FIT`).
- The database schema is migrated safely from version 23 to 24 with an idempotent `ALTER TABLE` statement adding `source TEXT DEFAULT 'TRACKED'`.
- Real-time recordings via `TrackerService` explicitly tag `TRACKED`.
- Ingestion pipelines in `LegacyImportEngine` tag imported files as `TCX` or `GPX`.
- The Aftermath header (`WorkoutHeader.kt`) renders a subtle Material 3 surface badge in Row A next to the sport name badge only for non-tracked workouts (`data.source != WorkoutSource.TRACKED`), keeping live-recorded workouts clean and uncluttered.
- Localized labels are 100% complete across all 9 supported application locales.

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1: Domain Model & Enum Parsing** | [WorkoutSourceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSourceTest.kt) | **PASSED** | Validates all enum values and safe case-insensitive fallback to `TRACKED` for null or unknown strings. |
| **AC-2: Database Migration v23 -> v24** | [WorkoutSummariesDatabaseSourceMigrationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseSourceMigrationTest.kt) | **PASSED** | Validates column addition, default `'TRACKED'`, idempotent upgrade execution, and upgrade preservation. |
| **AC-3: Ingestion Source Tagging** | [WorkoutIngestionSourceTaggingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/WorkoutIngestionSourceTaggingTest.kt) | **PASSED** | Verifies TCX and GPX file imports tag summaries with `WorkoutSource.TCX` and `WorkoutSource.GPX`. |
| **AC-4: Data Mapping & State Integrity** | [WorkoutDataSourceMappingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataSourceMappingTest.kt) | **PASSED** | Verifies `WorkoutDataMapper` extracts `source` when present and gracefully defaults to `TRACKED` when absent. |
| **AC-5: Visual Badge Contract** | [WorkoutHeaderSourceBadgeVisualContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/header/WorkoutHeaderSourceBadgeVisualContractTest.kt) | **PASSED** | Verifies badge is suppressed for `TRACKED` and rendered for `TCX`, `GPX`, and `FIT` using appropriate string resource IDs. |
| **AC-6: 9-Language Localization Parity** | [TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt) | **PASSED** | 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT string resources. |
| **AC-7: Clean-Room Regression** | `./gradlew testDebugUnitTest` | **PASSED** | Full suite execution passed with zero errors. |

---

## 3. Key Implementation Diffs

### Database Schema Upgrade (v23 -> v24)
```diff
--- a/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java
+++ b/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java
@@ -48,7 +48,7 @@ public class WorkoutSummariesDatabaseManager extends SQLiteOpenHelper {
-    public static final int DB_VERSION = 23;
+    public static final int DB_VERSION = 24;
@@ -107,6 +107,7 @@ public class WorkoutSummariesDatabaseManager extends SQLiteOpenHelper {
+        public static final String SOURCE = "source";
@@ -432,6 +433,9 @@ public class WorkoutSummariesDatabaseManager extends SQLiteOpenHelper {
+        if (oldVersion < 24) {
+            addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE, "TEXT DEFAULT 'TRACKED'");
+        }
```

### UI Source Badge in Header Row A
```diff
--- a/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt
+++ b/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt
@@ -148,6 +148,22 @@ fun WorkoutHeader(
+            if (data.source != com.atrainingtracker.trainingtracker.database.WorkoutSource.TRACKED) {
+                val sourceLabel = when (data.source) {
+                    com.atrainingtracker.trainingtracker.database.WorkoutSource.TCX -> stringResource(R.string.workout_source_tcx)
+                    com.atrainingtracker.trainingtracker.database.WorkoutSource.GPX -> stringResource(R.string.workout_source_gpx)
+                    com.atrainingtracker.trainingtracker.database.WorkoutSource.FIT -> stringResource(R.string.workout_source_fit)
+                    else -> data.source.name
+                }
+                Surface(
+                    shape = RoundedCornerShape(4.dp),
+                    color = MaterialTheme.colorScheme.surfaceVariant,
+                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
+                ) {
+                    Text(
+                        text = sourceLabel,
+                        style = MaterialTheme.typography.labelSmall,
+                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
+                    )
+                }
+            }
```
