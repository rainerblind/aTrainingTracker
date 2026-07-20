# CI/CD Secrets Setup

This document explains how to configure the GitHub secrets used by the workflows in `.github/workflows/`.

## Where to add secrets

1. Open the repository on GitHub.
2. Go to **Settings** → **Secrets and variables** → **Actions**.
3. Click **New repository secret** and add each secret listed below.

## Required secrets

The CI workflows can run without any secrets by using placeholder values. To use real credentials, add the following secrets.

### Google Maps and Firebase

| Secret | Purpose | How to obtain |
|--------|---------|---------------|
| `MAPS_API_KEY` | Google Maps API key used in `AndroidManifest.xml` | [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials → Create an Android-restricted API key. |
| `GOOGLE_SERVICES_JSON` | Full contents of `app/google-services.json` | [Firebase Console](https://console.firebase.google.com/) → Project settings → Service accounts → Download `google-services.json`. |

> Note: The branch-build workflow currently generates a placeholder `google-services.json`. To use a real one, update the workflow to write `secrets.GOOGLE_SERVICES_JSON` to `app/google-services.json` instead of the placeholder.

### Third-party OAuth credentials (optional)

These are read from `secrets.properties` at build time. The CI workflows generate placeholder values so the project compiles. For a release with real credentials, add:

| Secret | Purpose |
|--------|---------|
| `STRAVA_CLIENT_ID` | Strava API client ID |
| `STRAVA_CLIENT_SECRET` | Strava API client secret |
| `TRAININGPEAKS_CLIENT_ID` | TrainingPeaks API client ID |
| `TRAININGPEAKS_CLIENT_SECRET` | TrainingPeaks API client secret |
| `RUNKEEPER_CLIENT_ID` | Runkeeper/HealthGraph API client ID |
| `RUNKEEPER_CLIENT_SECRET` | Runkeeper/HealthGraph API client secret |
| `DROPBOX_APP_KEY` | Dropbox app key |

## Signed release builds

The release workflow signs the APK and AAB when the following secrets are present. If they are missing, the workflow produces unsigned release artifacts.

| Secret | Purpose | Format |
|--------|---------|--------|
| `RELEASE_KEYSTORE_BASE64` | Android release keystore | Base64-encoded contents of your `.jks` or `.keystore` file. |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password | Plain text |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore | Plain text |
| `RELEASE_KEY_PASSWORD` | Password for the key alias | Plain text |

### Encoding the keystore

On Linux or macOS:

```bash
base64 -i release.keystore -o release.keystore.b64
```

Then paste the contents of `release.keystore.b64` into the `RELEASE_KEYSTORE_BASE64` GitHub secret.

On Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

## Verifying the setup

After adding secrets:

- Push any commit to a branch to trigger the **Android Branch Build** workflow.
- Create and push a tag matching `v*` or `release-*` to trigger the **Android Release** workflow.
