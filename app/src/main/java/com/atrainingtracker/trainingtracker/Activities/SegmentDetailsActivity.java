package com.atrainingtracker.trainingtracker.Activities;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.segments.SegmentLeaderboardListFragment;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.atrainingtracker.trainingtracker.segments.SimpleSegmentOnMapFragment;


public class SegmentDetailsActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    public static final String SELECTED_FRAGMENT = "SELECTED_FRAGMENT";
    public static final String SELECTED_FRAGMENT_ID = "SELECTED_FRAGMENT_ID";
    private static final String TAG = SegmentDetailsActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static final int DEFAULT_SELECTED_FRAGMENT_ID = R.id.drawer_map;
    // remember which fragment should be shown
    protected int mSelectedFragmentId = DEFAULT_SELECTED_FRAGMENT_ID;
    // the views
    protected DrawerLayout mDrawerLayout;
    protected NavigationView mNavigationView;
    long mSegmentId;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        Bundle bundle = this.getIntent().getExtras();
        mSegmentId = bundle.getLong(SegmentsDatabaseManager.Segments.SEGMENT_ID);
        if (DEBUG) Log.d(TAG, "got setment id: " + mSegmentId);

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

        mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setItemIconTintList(null);  // avoid converting the icons to black and white or gray and white
        mNavigationView.setNavigationItemSelectedListener(this);

        if (savedInstanceState != null) {
            mSelectedFragmentId = savedInstanceState.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID);
        }
        if (getIntent().hasExtra(SELECTED_FRAGMENT)) {
            switch (SelectedFragment.valueOf(getIntent().getStringExtra(SELECTED_FRAGMENT))) {
                case MAP:
                    mSelectedFragmentId = R.id.drawer_map;
                    break;

                case LEADERBOARD:
                    mSelectedFragmentId = R.id.drawer_segment_leaderboard;
                    break;
            }
        }

        if (DEBUG) Log.i(TAG, "now, we select the main fragment");
        // now, create and show the main fragment
        onNavigationItemSelected(mNavigationView.getMenu().findItem(mSelectedFragmentId));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (TrainingApplication.NoUnlocking()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onSaveInstanceState");

        savedInstanceState.putInt(SELECTED_FRAGMENT_ID, mSelectedFragmentId);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {

        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        // else if (getSupportFragmentManager().getBackStackEntryCount() == 0
        //        && mSelectedFragmentId != R.id.drawer_map) {
        //     onNavigationItemSelected(mNavigationView.getMenu().findItem(R.id.drawer_map));
        // }
        else {
            super.onBackPressed();
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

    // TODO: inline???
    private void setContentFragment(int menuId) {
        Fragment fragment = null;
        String tag = null;
        switch (menuId) {
            case R.id.drawer_segment_leaderboard:
                fragment = SegmentLeaderboardListFragment.newInstance(mSegmentId);
                tag = SegmentLeaderboardListFragment.TAG;
                break;

            case R.id.drawer_map:
                fragment = SimpleSegmentOnMapFragment.newInstance(mSegmentId);
                tag = SimpleSegmentOnMapFragment.TAG;
                break;
        }

        if (fragment != null) {
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content, fragment, tag);
            fragmentTransaction.commit();
        }
    }

    public enum SelectedFragment {MAP, LEADERBOARD}

}
