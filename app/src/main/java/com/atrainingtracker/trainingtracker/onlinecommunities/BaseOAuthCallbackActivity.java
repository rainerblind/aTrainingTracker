package com.atrainingtracker.trainingtracker.onlinecommunities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseOAuthCallbackActivity extends Activity {
    private static final String TAG = "BaseOAuthCallback";
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    public static final String ACCESS_TOKEN = "access_token";
    protected static final String CODE = "code";

    // Abstract methods to get the specific URL logic for the concrete service (Strava, etc)
    protected abstract String getAccessUrl(String code);
    protected abstract String getRedirectUri();

    private ProgressDialog dialog;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Flag to ensure we don't process multiple redirects
    private boolean isProcessing = false;

    protected abstract String getOAuthSuccessID();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This activity starts blank and waits for onNewIntent.
        // Optionally show a "Waiting for browser..." text or spinner here.
        if (DEBUG) Log.d(TAG, "onCreate: Waiting for redirect...");

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (DEBUG) Log.d(TAG, "onNewIntent");
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (isProcessing) return;

        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(getRedirectUri())) {
            if (DEBUG) Log.d(TAG, "Redirect received: " + data.toString());

            String error = data.getQueryParameter("error");
            if (error != null) {
                Log.e(TAG, "Auth error: " + error);
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            }

            String code = data.getQueryParameter(CODE);
            if (code != null) {
                isProcessing = true;
                showProgress();
                performTokenExchange(code);
            }
        }
    }

    private void showProgress() {
        if (dialog == null) {
            dialog = new ProgressDialog(this);
            dialog.setMessage(getString(R.string.please_wait)); // Ensure you have this string
            dialog.setCancelable(false);
        }
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    private void performTokenExchange(String code) {
        executor.execute(() -> {
            String resultToken = null;
            HttpURLConnection urlConnection = null;

            try {
                URL url = new URL(getAccessUrl(code));
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(false);
                urlConnection.setFixedLengthStreamingMode(0);
                urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);

                int responseCode = urlConnection.getResponseCode();

                // Read Stream Logic ...
                java.io.InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                        ? urlConnection.getInputStream()
                        : urlConnection.getErrorStream();

                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) responseBuilder.append(line);
                    }
                }

                String responseStr = responseBuilder.toString();
                if (DEBUG) Log.d(TAG, "Response: " + responseStr);

                if (!responseStr.isEmpty()) {
                    JSONObject json = new JSONObject(responseStr);
                    // Hook for subclasses if needed
                    onJsonResponse(json);
                    if (json.has(ACCESS_TOKEN)) {
                        resultToken = json.getString(ACCESS_TOKEN);
                    }
                }

            } catch (Throwable t) {
                Log.e(TAG, "Error", t);
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }

            // Post the result back to the Main Thread
            final String finalToken = resultToken;
            mainHandler.post(() -> {
                if (isFinishing()) return;

                Intent resultIntent = new Intent();
                if (finalToken != null) {
                    // send Broadcast
                    Intent broadcast = new Intent(getOAuthSuccessID());
                    broadcast.putExtra("access_token", finalToken);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

                    // return result
                    if (DEBUG) Log.d(TAG, "Token acquired. Finishing with RESULT_OK.");
                    resultIntent.putExtra(ACCESS_TOKEN, finalToken);
                    setResult(Activity.RESULT_OK, resultIntent);
                } else {
                    if (DEBUG) Log.e(TAG, "Token is null. Finishing with RESULT_CANCELED.");
                    setResult(Activity.RESULT_CANCELED);
                }

                if (dialog != null && dialog.isShowing()) {
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {}
                }
                finish(); // Finishes THIS Callback activity, returning to BaseGetAccessTokenActivity
            });
        });
    }

    protected void onJsonResponse(JSONObject json) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }
}
