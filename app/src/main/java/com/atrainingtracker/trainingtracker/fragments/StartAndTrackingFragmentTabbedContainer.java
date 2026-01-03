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

package com.atrainingtracker.trainingtracker.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.tracker.TrackerService;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper;
import com.atrainingtracker.trainingtracker.dialogs.LapSummaryDialog;
import com.atrainingtracker.trainingtracker.interfaces.RemoteDevicesSettingsInterface;

import java.util.LinkedList;

/**
 * Created by rainer on 20.01.16.
 */
public class StartAndTrackingFragmentTabbedContainer extends Fragment {

    public static final String TAG = StartAndTrackingFragmentTabbedContainer.class.getSimpleName();
    public static final String ACTIVITY_TYPE = "ACTIVITY_TYPE";
    public static final String SELECTED_ITEM = "SELECTED_ITEM";
    public static final int CONTROL_ITEM = 0;
    private static final boolean DEBUG = true; // TrainingApplication.getDebug(false);
    private static final int SHOW_LAP_SUMMARY_TIME = 3000;
    private final IntentFilter mTrackingStartedFilter = new IntentFilter(TrackerService.TRACKING_STARTED_INTENT);
    private final IntentFilter mTrackingFinishedFilter = new IntentFilter(TrackerService.TRACKING_FINISHED_INTENT);
    private final IntentFilter mLapSummaryFilter = new IntentFilter(BANALService.LAP_SUMMARY);
    private final IntentFilter mUpdateActivityTypeFilter = new IntentFilter();
    private final IntentFilter mPauseChangedFilter = new IntentFilter();  // actions are added within onResume
    protected UpdateActivityTypeInterface mUpdateActivityInterface;
    protected RemoteDevicesSettingsInterface mRemoteDevicesSettingsInterface;
    protected BANALService.GetBanalServiceInterface mGetBanalServiceIf;
    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;
    ActivityType mActivityType = null; // ActivityType.getDefaultActivityType();
    int mSelectedItemNr = CONTROL_ITEM;
    final LinkedList<Integer> mViewIdList = new LinkedList<>();
    final LinkedList<String> mTitleList = new LinkedList<>();
    final BroadcastReceiver mTrackingStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (DEBUG) Log.i(TAG, "received TrackingStarted");
            mViewPager.setCurrentItem(1);
        }
    };
    final BroadcastReceiver mTrackingFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (DEBUG) Log.i(TAG, "received TrackingFinished");
            mViewPager.setCurrentItem(0);
        }
    };
    final BroadcastReceiver mLapSummaryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showLapSummaryDialog(intent.getIntExtra(BANALService.PREV_LAP_NR, 0),
                    intent.getStringExtra(BANALService.PREV_LAP_TIME_STRING),
                    intent.getStringExtra(BANALService.PREV_LAP_DISTANCE_STRING),
                    intent.getStringExtra(BANALService.PREV_LAP_SPEED_STRING));
        }
    };
    final BroadcastReceiver mPauseChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // get a new title for the first fragment
            mSectionsPagerAdapter.notifyDataSetChanged(); // hope this makes the job
        }
    };
    final BroadcastReceiver mUpdateActivityTypeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.i(TAG, "update activity type");
            updateActivityType();
        }
    };

    public static StartAndTrackingFragmentTabbedContainer newInstance(ActivityType activityType, int selectedItem) {
        if (DEBUG) Log.i(TAG, "newInstance");

        StartAndTrackingFragmentTabbedContainer startAndTrackingFragmentTabbedContainer = new StartAndTrackingFragmentTabbedContainer();

        Bundle args = new Bundle();
        args.putString(ACTIVITY_TYPE, activityType.name());
        args.putInt(SELECTED_ITEM, selectedItem);
        startAndTrackingFragmentTabbedContainer.setArguments(args);

        return startAndTrackingFragmentTabbedContainer;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        mActivityType = ActivityType.valueOf(getArguments().getString(ACTIVITY_TYPE));
        mSelectedItemNr = getArguments().getInt(SELECTED_ITEM);

        if (savedInstanceState != null && savedInstanceState.containsKey(SELECTED_ITEM)) {
            mSelectedItemNr = savedInstanceState.getInt(SELECTED_ITEM);
            if (DEBUG)
                Log.i(TAG, "got selected item from last time: " + savedInstanceState.getInt(SELECTED_ITEM));
        }

        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (DEBUG) Log.i(TAG, "onAttach");

        try {
            mUpdateActivityInterface = (UpdateActivityTypeInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement UpdateActivityTypeInterface");
        }

        try {
            mRemoteDevicesSettingsInterface = (RemoteDevicesSettingsInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + "must implement RemoteDevicesSettingsInterface");
        }

        try {
            mGetBanalServiceIf = (BANALService.GetBanalServiceInterface) context;
            mGetBanalServiceIf.registerConnectionStatusListener(new BANALService.GetBanalServiceInterface.ConnectionStatusListener() {
                @Override
                public void connectedToBanalService() {
                    if (DEBUG) Log.i(TAG, "mGetBanalServiceIf is ready");
                    updateActivityType();
                }

                @Override
                public void disconnectedFromBanalService() {

                }
            });
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement GetBanalServiceInterface");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreateView()");

        View v = inflater.inflate(R.layout.tabbed_tracking_fragment, container, false);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager());

        mViewPager = v.findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (DEBUG) Log.i(TAG, "onActivityCreated, savedInstanceState=" + savedInstanceState);

        if (savedInstanceState != null) {
            mSelectedItemNr = savedInstanceState.getInt(SELECTED_ITEM);
            if (DEBUG)
                Log.i(TAG, "got selected item from last time: " + savedInstanceState.getInt(SELECTED_ITEM));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        ContextCompat.registerReceiver(getActivity(), mTrackingStartedReceiver, mTrackingStartedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mTrackingFinishedReceiver, mTrackingFinishedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mLapSummaryReceiver, mLapSummaryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        mPauseChangedFilter.addAction(TrainingApplication.REQUEST_START_TRACKING);
        mPauseChangedFilter.addAction(TrainingApplication.REQUEST_PAUSE_TRACKING);
        mPauseChangedFilter.addAction(TrainingApplication.REQUEST_RESUME_FROM_PAUSED);
        ContextCompat.registerReceiver(getActivity(), mPauseChangedReceiver, mPauseChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        mUpdateActivityTypeFilter.addAction(BANALService.SENSORS_CHANGED);
        mUpdateActivityTypeFilter.addAction(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT);
        ContextCompat.registerReceiver(getActivity(), mUpdateActivityTypeReceiver, mUpdateActivityTypeFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        createViewIdList();

        // try to get the current ActivityType
        updateActivityType();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        try {
            getActivity().unregisterReceiver(mTrackingStartedReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mTrackingFinishedReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mLapSummaryReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mPauseChangedReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mUpdateActivityTypeReceiver);
        } catch (Exception e) {
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mSelectedItemNr = mViewPager.getCurrentItem();
        outState.putInt(SELECTED_ITEM, mSelectedItemNr);
        if (DEBUG)
            Log.i(TAG, "onSaveInstanceState: saved selected item:" + mViewPager.getCurrentItem());

        super.onSaveInstanceState(outState);
    }

    protected void updateActivityType() {
        if (DEBUG) Log.i(TAG, "updateActivityType");

        ActivityType activityType = ActivityType.getDefaultActivityType();

        if (mGetBanalServiceIf != null && mGetBanalServiceIf.getBanalServiceComm() != null) {
            activityType = mGetBanalServiceIf.getBanalServiceComm().getActivityType();
        } else {
            Log.i(TAG, "no connection to BANALService");
        }

        if (activityType != mActivityType) {
            mActivityType = activityType;
            if (DEBUG) Log.i(TAG, "got new ActivityType: " + mActivityType);
            mUpdateActivityInterface.updateActivityType(mViewPager.getCurrentItem());
        }
    }

    protected void createViewIdList() {
        if (DEBUG)
            Log.i(TAG, "createViewIdList(), mActivityType=" + mActivityType + "mSelectedItemNr=" + mSelectedItemNr);

        TrackingViewsDatabaseManager databaseManager = TrackingViewsDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                null,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE + "=?",
                new String[]{mActivityType.name()},
                null,
                null,
                TrackingViewsDbHelper.LAYOUT_NR + " ASC");

        mTitleList.clear();
        mViewIdList.clear();
        while (cursor.moveToNext()) {
            mTitleList.add(cursor.getString(cursor.getColumnIndex(TrackingViewsDbHelper.NAME)));
            mViewIdList.add(cursor.getInt(cursor.getColumnIndex(TrackingViewsDbHelper.C_ID)));
        }
        cursor.close();
        databaseManager.closeDatabase();

        mSectionsPagerAdapter.notifyDataSetChanged();
        if (mSelectedItemNr > mSectionsPagerAdapter.getCount()) {
            mSelectedItemNr--;
        }
        mViewPager.setCurrentItem(mSelectedItemNr);
    }

    public void showLapSummaryDialog(int lapNr, String lapTime, String lapDistance, String lapSpeed) {

        if (getContext() == null) {
            Log.i(TAG, "WTF: getContext() == null, so we return");
            return;
        }

        if (mViewPager.getCurrentItem() == 0) {
            return;
        }  // the control tracking fragment is in the foreground, so we do not show this dialog/info

        LapSummaryDialog lapSummaryDialog = LapSummaryDialog.newInstance(lapNr, lapTime, lapDistance, lapSpeed);
        lapSummaryDialog.show(getFragmentManager(), LapSummaryDialog.TAG);
    }

    public interface UpdateActivityTypeInterface {
        void updateActivityType(int selectedItemNr);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getItem(" + position + ")");

            if (position == 0) {
                return new ControlTrackingFragment();
            } else if (position < getCount()) {
                return TrackingFragment.newInstance(mViewIdList.get(position - 1), mActivityType);
            } else {
                return TrackingFragment.newInstance(mViewIdList.getLast(), mActivityType);
            }
        }

        @Override
        public int getCount() {
            //noinspection UnnecessaryLocalVariable
            int count = mViewIdList.size() + 1;

            // if (DEBUG) Log.i(TAG, "SectionsPagerAdapter.getCount(): returning " + count);

            return count;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                if (TrainingApplication.isTracking()) {
                    if (TrainingApplication.isPaused()) {
                        return getString(R.string.Paused);
                    } else {
                        return getString(R.string.Tracking);
                    }
                } else {
                    return getString(R.string.tab_start);
                }
            } else if (position < getCount()) {
                return mTitleList.get(position - 1);
            } else {
                return mTitleList.getLast();
            }
        }
    }
}
