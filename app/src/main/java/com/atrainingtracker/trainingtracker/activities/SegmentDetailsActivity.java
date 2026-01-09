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

package com.atrainingtracker.trainingtracker.activities;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.atrainingtracker.trainingtracker.segments.SimpleSegmentOnMapFragment;


public class SegmentDetailsActivity extends AppCompatActivity {
    public static final String SELECTED_FRAGMENT = "SELECTED_FRAGMENT";
    public static final String SELECTED_FRAGMENT_ID = "SELECTED_FRAGMENT_ID";
    private static final String TAG = SegmentDetailsActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    // the views
    protected DrawerLayout mDrawerLayout;
    // protected NavigationView mNavigationView;
    long mSegmentId;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        Bundle bundle = this.getIntent().getExtras();
        mSegmentId = bundle.getLong(SegmentsDatabaseManager.Segments.SEGMENT_ID);
        if (DEBUG) Log.d(TAG, "got segment id: " + mSegmentId);

        // set the title
        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(SegmentsDatabaseManager.Segments.TABLE_STARRED_SEGMENTS,
                new String[]{SegmentsDatabaseManager.Segments.SEGMENT_NAME},
                SegmentsDatabaseManager.Segments.SEGMENT_ID + "=?", new String[]{mSegmentId + ""},
                null, null, null);
        if (cursor.moveToFirst()) {
            String segmentName = cursor.getString(0);
            setTitle(segmentName);
        }
        cursor.close();
        SegmentsDatabaseManager.getInstance().closeDatabase();


        setContentView(R.layout.segment_details);

        Toolbar toolbar = findViewById(R.id.apps_toolbar);
        setSupportActionBar(toolbar);

        final ActionBar supportAB = getSupportActionBar();
        // supportAB.setHomeAsUpIndicator(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        supportAB.setDisplayHomeAsUpEnabled(true);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.TrainingTracker, R.string.TrainingTracker);
        actionBarDrawerToggle.syncState();

        Fragment fragment = SimpleSegmentOnMapFragment.newInstance(mSegmentId);
        String tag = SimpleSegmentOnMapFragment.TAG;

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content, fragment, tag);
        fragmentTransaction.commit();


        ViewCompat.setOnApplyWindowInsetsListener(
                mDrawerLayout,
                new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(
                            @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                        v.setPadding(insets.left, 0, insets.right, insets.bottom);
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                        mlp.topMargin = insets.top;
                        return WindowInsetsCompat.CONSUMED;
                    }
                });

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                            mDrawerLayout.closeDrawer(GravityCompat.START);
                        }
                        // else if (getSupportFragmentManager().getBackStackEntryCount() == 0
                        //        && mSelectedFragmentId != R.id.drawer_map) {
                        //     onNavigationItemSelected(mNavigationView.getMenu().findItem(R.id.drawer_map));
                        // }
                        else {
                            finish();
                        }
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (TrainingApplication.NoUnlocking()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }
}
