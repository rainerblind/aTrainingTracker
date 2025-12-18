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


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.dialogs.ReallyDeleteWorkoutDialog;
import com.atrainingtracker.trainingtracker.fragments.EditWorkoutFragment;
import com.atrainingtracker.trainingtracker.fragments.ExportStatusDialogFragment;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapAftermathFragment;
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaValuesThread;
import com.atrainingtracker.trainingtracker.helpers.DeleteWorkoutThread;
import com.atrainingtracker.trainingtracker.interfaces.ReallyDeleteDialogInterface;


public class WorkoutDetailsActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        ReallyDeleteDialogInterface {
    public static final String SELECTED_FRAGMENT = "SELECTED_FRAGMENT";
    public static final String SELECTED_FRAGMENT_ID = "SELECTED_FRAGMENT_ID";
    private static final String TAG = "WorkoutDetailsActivity";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final int DEFAULT_SELECTED_FRAGMENT_ID = R.id.drawer_map;
    private static final String CALCULATING_EXTREMA_VALUES = "CALCULATING_EXTREMA_VALUES";
    private final IntentFilter mFinishedCalculatingExtremaValuesFilter = new IntentFilter(CalcExtremaValuesThread.FINISHED_CALCULATING_EXTREMA_VALUES);
    // remember which fragment should be shown
    protected int mSelectedFragmentId = DEFAULT_SELECTED_FRAGMENT_ID;
    // the views
    protected DrawerLayout mDrawerLayout;
    protected NavigationView mNavigationView;
    long mWorkoutID;
    private boolean mCalculatingExtremaValues = false;
    private final BroadcastReceiver mFinishedCalculatingExtremaValuesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // remove the progress view
            findViewById(R.id.llProgress).setVisibility(View.GONE);
            mCalculatingExtremaValues = false;
        }
    };

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        Bundle bundle = this.getIntent().getExtras();
        mWorkoutID = bundle.getLong(WorkoutSummaries.WORKOUT_ID);
        if (DEBUG) Log.d(TAG, "get workout id: " + mWorkoutID);

        setContentView(R.layout.workout_details);

        Toolbar toolbar = findViewById(R.id.apps_toolbar);
        setSupportActionBar(toolbar);

        final ActionBar supportAB = getSupportActionBar();
        // supportAB.setHomeAsUpIndicator(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        supportAB.setDisplayHomeAsUpEnabled(true);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.TrainingTracker, R.string.TrainingTracker);
        actionBarDrawerToggle.syncState();

        mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setItemIconTintList(null);  // avoid converting the icons to black and white or gray and white
        mNavigationView.setNavigationItemSelectedListener(this);

        if (savedInstanceState != null) {
            mSelectedFragmentId = savedInstanceState.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID);
            mCalculatingExtremaValues = savedInstanceState.getBoolean(CALCULATING_EXTREMA_VALUES, false);
        }
        if (getIntent().hasExtra(SELECTED_FRAGMENT)) {
            switch (SelectedFragment.valueOf(getIntent().getStringExtra(SELECTED_FRAGMENT))) {
                case MAP:
                    mSelectedFragmentId = R.id.drawer_map;
                    break;

                case EDIT_DETAILS:
                    mSelectedFragmentId = R.id.edit_workout_details;
                    break;
            }
        }

        if (DEBUG) Log.i(TAG, "now, we select the main fragment");
        // now, create and show the main fragment
        onNavigationItemSelected(mNavigationView.getMenu().findItem(mSelectedFragmentId));

        if (mCalculatingExtremaValues) {  // currently calculating the extrema values
            findViewById(R.id.llProgress).setVisibility(View.VISIBLE);
        } else if (!WorkoutSummariesDatabaseManager.getBoolean(mWorkoutID, WorkoutSummaries.EXTREMA_VALUES_CALCULATED)) {  // extrema values are not yet calculated
            Log.i(TAG, "calculate the extrema values");
            mCalculatingExtremaValues = true;

            // show the progress view
            findViewById(R.id.llProgress).setVisibility(View.VISIBLE);

            // now, calc the extrema values in the background
            (new CalcExtremaValuesThread(this, findViewById(R.id.tvProgressMessage), mWorkoutID)).start();
        }
        // knappar beskärda

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
                        return windowInsets;
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
        if (DEBUG) Log.d(TAG, "onResume");
        super.onResume();

        if (TrainingApplication.NoUnlocking()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        ContextCompat.registerReceiver(this,mFinishedCalculatingExtremaValuesReceiver, mFinishedCalculatingExtremaValuesFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        // now, create and show the main fragment
        // onNavigationItemSelected(mNavigationView.getMenu().findItem(R.id.edit_workout_details));
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onSaveInstanceState");

        savedInstanceState.putInt(SELECTED_FRAGMENT_ID, mSelectedFragmentId);
        savedInstanceState.putBoolean(CALCULATING_EXTREMA_VALUES, mCalculatingExtremaValues);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause");

        try {
            unregisterReceiver(mFinishedCalculatingExtremaValuesReceiver);
        } catch (IllegalArgumentException e) {
        }
    }

    /* Called when an options item is clicked */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        if (DEBUG) Log.i(TAG, "onNavigationItemSelected");

        mDrawerLayout.closeDrawers();

        if (menuItem.isChecked()) {  // itemMenu is already selected
            // nothing to do ???
            return true;
        }

        // else: itemMenu not yet selected
        menuItem.setChecked(true);

        // just for debugging
        // if (DEBUG) Toast.makeText(getApplicationContext(), menuItem.getTitle(), Toast.LENGTH_SHORT).show();

        // save
        mSelectedFragmentId = menuItem.getItemId();

        setContentFragment(menuItem.getItemId());

        return true;
    }

    // TODO: inline
    private void setContentFragment(int menuId) {
        Fragment fragment = null;
        String tag = null;
        switch (menuId) {
            case R.id.edit_workout_details:
                fragment = EditWorkoutFragment.newInstance(mWorkoutID);
                tag = EditWorkoutFragment.TAG;
                break;

            case R.id.drawer_map:
                fragment = TrackOnMapAftermathFragment.newInstance(mWorkoutID);
                tag = TrackOnMapAftermathFragment.TAG;
                break;

            case R.id.drawer_export_status:
                fragment = ExportStatusDialogFragment.newInstance(mWorkoutID);
                tag = ExportStatusDialogFragment.TAG;
                break;
        }

        if (fragment != null) {
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content, fragment, tag);
            fragmentTransaction.commit();
        }
    }

    @Override
    public void confirmDeleteWorkout(long workoutId) {
        ReallyDeleteWorkoutDialog newFragment = ReallyDeleteWorkoutDialog.newInstance(workoutId);
        newFragment.show(getSupportFragmentManager(), "reallyDelete");
    }

    @Override
    public void reallyDeleteWorkout(long workoutId) {
        (new DeleteWorkoutThread(this, new Long[]{workoutId})).start();

        finish();
    }

    public enum SelectedFragment {MAP, EDIT_DETAILS}

}
