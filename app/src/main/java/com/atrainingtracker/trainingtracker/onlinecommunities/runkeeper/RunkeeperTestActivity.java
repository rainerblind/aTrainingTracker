package com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;

public class RunkeeperTestActivity extends Activity {
    private final static String TAG = "RunkeeperTestActivity";
    private static final boolean DEBUG = false;

    private static final int GET_RUNKEEPER_ACCESS_TOKEN = 1;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        // just to demonstrate which activity is active
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.logo);
        setContentView(imageView);

        startActivityForResult(new Intent(this, RunkeeperGetAccessTokenActivity.class), GET_RUNKEEPER_ACCESS_TOKEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);

        switch (requestCode) {
            case (GET_RUNKEEPER_ACCESS_TOKEN):
                if (resultCode == Activity.RESULT_OK) {
                    String accessToken = data.getStringExtra(BaseGetAccessTokenActivity.ACCESS_TOKEN);
                    if (DEBUG) Log.d(TAG, "we got the access token: " + accessToken);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    if (DEBUG) Log.d(TAG, "WTF, something went wrong");
                }
                break;
        }
    }

}
