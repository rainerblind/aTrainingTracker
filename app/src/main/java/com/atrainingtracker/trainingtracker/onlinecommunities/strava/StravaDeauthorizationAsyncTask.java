package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class StravaDeauthorizationAsyncTask extends
        AsyncTask<String, String, String> {

    private static final String TAG = "StravaDeauthorizationAsyncTask";
    private static final boolean DEBUG = false;

    private ProgressDialog progressDialog;
    private Context mContext;

    public StravaDeauthorizationAsyncTask(Context context) {
        progressDialog = new ProgressDialog(context);
        mContext = context;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog.setMessage(mContext.getString(R.string.deauthorization));
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    @Override
    protected void onPostExecute(String accessToken) {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected String doInBackground(String... params) {

        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost("https://www.strava.com/oauth/deauthorize");
        httpPost.addHeader("Authorization", "Bearer " + TrainingApplication.getStravaToken());

        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpPost);

            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "response: " + response);
            // Uri uri = Uri.parse(response);
            JSONObject responseJson = new JSONObject(response);

            if (responseJson.has("access_token")) {
                // String tokenType   = responseJson.getString(TOKEN_TYPE);
                String access_token = responseJson.getString("access_token");
                if (access_token.equals(TrainingApplication.getStravaToken())) {
                    if (DEBUG) Log.d(TAG, "all right!");
                }
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        TrainingApplication.deleteStravaToken();
        return "foo";

    }

}
