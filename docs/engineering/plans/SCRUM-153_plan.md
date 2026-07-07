# Implementation Plan: Improved Strava Connection (SCRUM-153)

## 1. Goal
Modernize the Strava OAuth connection flow by using Chrome Custom Tabs for authorization and refactoring the token exchange to use Kotlin, ViewModels, and StateFlow.

## 2. Requirement Mapping
*   **REQ-EXT-004**: Integrated Strava Authorization via Custom Tabs. (Test: TST-EXT-001)
*   **REQ-EXT-005**: Reactive OAuth Feedback and modernized logic. (Test: TST-EXT-002)

## 3. Impact Analysis (SWE.1.BP.5)
*   **Android System**: Improved app-switching experience. Custom Tabs prevent the browser from polluting the user's recent tasks list.
*   **Component Interfaces**: Replaces `LocalBroadcastManager` with a more modern communication mechanism (ViewModel or Repository Flow).
*   **Data Integrity**: Zero risk. Token storage remains the same.
*   **Legacy Compatibility**: Converting `StravaHelper` to Kotlin might require updating some Java callers.

## 4. Proposed Changes

### Library Layer
*   Add `androidx.browser:browser:1.10.0` to `app/build.gradle`.

### Logic & Data Layer
*   **StravaHelper.kt**:
    - Convert `StravaHelper.java` to Kotlin.
    - Implement `requestAccessToken` using `CustomTabsIntent`.
    - Modernize URL generation logic.
*   **StravaAuthRepository.kt** (New):
    - A singleton/repository to hold the OAuth state (`StateFlow`).
    - Handles the token exchange process using Kotlin Coroutines and OkHttp.

### ViewModel (New)
*   **StravaAuthViewModel.kt**:
    - Manages the connection flow state (Idle, Connecting, Success, Error).
    - Exposed to the UI for observing the connection status.

### Callback Handling
*   **StravaOAuthCallbackActivity.kt**:
    - Convert to Kotlin.
    - Remove `ProgressDialog` and `HttpURLConnection` logic.
    - Instead of manual exchange, it calls `StravaAuthRepository.exchangeCode(code)`.
    - Acts as a transparent proxy that finishes immediately after triggering the exchange.

### UI Layer
*   **StravaUploadFragment.kt**:
    - Remove `LocalBroadcastManager` and `BroadcastReceiver`.
    - Use `StravaAuthViewModel` to observe the connection state.
    - Update `StravaConnectionHeader` to reflect the "Connecting" state if necessary.

## 5. Verification Criteria (TST-EXT-001, TST-EXT-002)
*   Manual test: clicking connect opens a Custom Tab.
*   Manual test: successful login returns to the app and updates the UI automatically without manual refresh.
*   Check that no deprecated `ProgressDialog` is shown.
