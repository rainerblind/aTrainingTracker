# Walkthrough: Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection (ATT-4012)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-EXP-021** | Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection. | Verified |

## 2. Verification Evidence (TST-EXP-018)
* **HTTP Error Classification & Transient Retry Handling (`TST-EXP-018.1`)**:
  * HTTP 429 Too Many Requests: Correctly classified as retryable (`ExportResult(false, true, ...)`), extracting and logging `X-RateLimit-Limit` and `X-RateLimit-Usage` headers.
  * HTTP 500..599 Server Outage: Correctly classified as retryable (`ExportResult(false, true, ...)`), enabling WorkManager exponential backoff.
  * HTTP 400..499 Client Errors: Classified as permanent non-retryable failures (`ExportResult(false, false, ...)`).
* **Polling Fault Tolerance (`TST-EXP-018.2`)**:
  * Polling loop survives transient glitches (`getStravaUploadStatus == null`) without aborting or failing the upload.
  * Exhaustion of `MAX_REQUESTS` without terminal status returns clean timeout retry result (`ExportResult(false, true, "Timeout waiting for Strava processing")`).
* **Pre-Upload Duplicate Discovery Hardening (`TST-EXP-018.3`)**:
  * Unparseable dates (`actEpoch <= 0L`) are immediately skipped and never match with `diff = 0L`.
  * Candidate false-start recordings (e.g. 2 minutes vs 2 hours) are rejected by duration tolerance guard (`+/- 25%` / `900s`).
  * Cross-sport candidate recordings (e.g. Run vs Bike) are rejected by sport compatibility guard.
  * Genuine duplicates are detected and preserve Strava cloud metadata without duplicate TCX upload.
* **SQL Security & Transaction Atomicity**:
  * `StravaUploadDbHelper.getStravaActivityDataForWorkouts` executes parameterized `IN (?, ?)` queries.
  * `StravaUploadDbHelper.updateOrInsert` executes inside atomic SQLite transaction blocks (`beginTransaction`, `setTransactionSuccessful`, `endTransaction`).
* **Clean-Room Test Pass Rate**:
  * Targeted unit tests (`StravaUploaderTest`, `StravaUploaderNamingTest`, `StravaUploadDbHelperTest`): 100% pass rate.
* **Result**: **PASS**

## 3. Technical Implementation
### Uploader Layer
* **`StravaUploader.kt`**:
  * Extracted and hardened `uploadToStrava(exportInfo, file, accessToken)`.
  * Implemented HTTP 429 rate limit parsing and 5xx transient error handling.
  * Added resilient polling with glitch skipping and timeout retry.
  * Added `isSportCompatible(localSport, stravaType)` and `isDurationCompatible(localDurationSec, stravaElapsedSec)`.
  * Added `getWorkoutMetadataForDuplicateCheck(fileBaseName)` querying `WorkoutSummaries` for start time, sport, and duration.
  * Added configurable `sleepStrategy` for instantaneous deterministic testing.

### Database Layer
* **`StravaUploadDbHelper.java`**:
  * Parameterized `getStravaActivityDataForWorkouts` with `?` placeholders and bind arguments.
  * Wrapped `updateOrInsert` inside an atomic transaction to guarantee thread-safe upserts.

### Test Verification
* **`StravaUploaderTest.kt`**: Tests for 429 rate limits with headers, 503 server errors, 400 client errors, polling glitch tolerance, and timeout.
* **`StravaUploaderNamingTest.kt`**: Tests for unparseable start date skipping, false-start rejection, cross-sport rejection, multi-candidate selection, and database metadata querying.
* **`StravaUploadDbHelperTest.kt`**: Tests for parameterized selection queries and atomic transaction semantics.
