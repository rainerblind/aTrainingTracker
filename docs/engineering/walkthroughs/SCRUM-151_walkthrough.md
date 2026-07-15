# Walkthrough: Strava Demo Mode for Google Review (SCRUM-151)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-EXT-006** | Developer/Reviewer Demo Mode for Strava features. | Verified |

## 2. Verification Evidence (TST-EXT-003)
* **Procedure**:
    1. Open **Settings > Strava**.
    2. Long-press the **Strava Logo**.
    3. Observe the connection status.
    4. Trigger a "Synchronization" of segments/routes.
    5. Perform an activity "Upload" in Aftermath.
* **Observation**:
    * Long-pressing the logo successfully injected mock credentials and activated the global `isStravaDemoMode` flag.
    * The UI displayed "Strava Demo Mode Active" and transitioned to the connected state.
    * Synchronization bypassed network calls and populated the database with high-fidelity mock data (e.g., Alpe d'Huez).
    * Uploading an activity short-circuited the API and reported a successful result.
* **Result**: **PASS**

## 3. Technical Changes
### Core Logic
* **`TrainingApplication.java`**: Added `SP_STRAVA_DEMO_MODE` key and `injectMockStravaAccount()` logic to simulate a valid session state without backend interaction.
* **`StravaUploader.kt`**: Implemented a mock success branch that returns a simulated "Successfully Uploaded" message in demo mode.

### Repositories
* **`SegmentsRepository.kt`**: Added `injectMockSegments()` which populates the local database with pre-defined technical segment data when in demo mode.
* **`RoutesRepository.kt`**: Added `injectMockRoutes()` to simulate route discovery with high-resolution polylines.

### UI Layer
* **`StravaConnectionHeader.kt`**: Added a `combinedClickable` modifier to the Strava logo to serve as the hidden entry point for the demo mode. Added a visual "Active" badge to confirm activation.
* **`StravaUploadFragment.kt`**: Wired the demo activation callback to refresh the UI and trigger mock synchronization.
