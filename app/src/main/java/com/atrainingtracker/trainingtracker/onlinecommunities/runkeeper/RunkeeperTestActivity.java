/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

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
    protected void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
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
