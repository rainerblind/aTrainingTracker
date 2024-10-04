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

package com.atrainingtracker.trainingtracker.segments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;


public class SegmentLeaderboardListFragment extends SwipeRefreshListFragment {

    public static final String TAG = SegmentLeaderboardListFragment.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private static final String SEGMENT_ID = "SEGMENT_ID";

    protected SQLiteDatabase mDb;
    protected Cursor mCursor;
    protected CursorAdapter mCursorAdapter;

    protected long mSegmentId;
    protected StravaSegmentsHelper mStravaSegmentsHelper;

    protected IntentFilter mLeaderboardUpdateCompleteFilter = new IntentFilter(StravaSegmentsIntentService.LEADERBOARD_UPDATE_COMPLETE_INTENT);
    protected IntentFilter mNewLeaderboardEntryFilter = new IntentFilter(StravaSegmentsIntentService.NEW_LEADERBOARD_ENTRY_INTENT);
    protected BroadcastReceiver mLeaderboardUpdateCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long segmentId = intent.getLongExtra(Segments.SEGMENT_ID, -1);
            if (segmentId == mSegmentId) {
                if (isRefreshing()
                        && intent.hasExtra(StravaSegmentsIntentService.RESULT_MESSAGE)
                        && intent.getStringExtra(StravaSegmentsIntentService.RESULT_MESSAGE) != null) {
                    Toast.makeText(context, context.getString(R.string.updating_starred_segments_failed) + intent.getStringExtra(StravaSegmentsIntentService.RESULT_MESSAGE), Toast.LENGTH_LONG).show();
                }

                onRefreshComplete();
            }
        }
    };
    BroadcastReceiver mNewLeaderboardEntryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.i(TAG, "newLeaderboardEntry");

            if (intent.hasExtra(Segments.SEGMENT_ID)
                    && mSegmentId == intent.getLongExtra(Segments.SEGMENT_ID, -1)) {
                updateCursor();
            }
        }
    };

    public static SegmentLeaderboardListFragment newInstance(long segmentId) {
        if (DEBUG) Log.i(TAG, "newInstance()");

        SegmentLeaderboardListFragment fragment = new SegmentLeaderboardListFragment();

        Bundle args = new Bundle();
        args.putLong(SEGMENT_ID, segmentId);
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * Called when the fragment is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        mSegmentId = getArguments().getLong(SEGMENT_ID);
        mStravaSegmentsHelper = new StravaSegmentsHelper(getContext());
    }

    // BEGIN_INCLUDE (setup_views)
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mCursorAdapter = new SegmentLeaderboardCursorAdapter(getActivity(), mCursor);
        setListAdapter(mCursorAdapter);

        setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (DEBUG) Log.i(TAG, "onRefresh called from SwipeRefreshLayout");

                initiateRefresh();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume()");

        if (mStravaSegmentsHelper.isLeaderboardUpdating(mSegmentId)) {
            setRefreshing(true);
        }

        mDb = SegmentsDatabaseManager.getInstance().getOpenDatabase();
        updateCursor();

        ContextCompat.registerReceiver(getContext(), mLeaderboardUpdateCompleteReceiver, mLeaderboardUpdateCompleteFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getContext(), mNewLeaderboardEntryReceiver, mNewLeaderboardEntryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        SegmentsDatabaseManager.getInstance().closeDatabase();

        getContext().unregisterReceiver(mLeaderboardUpdateCompleteReceiver);
        getContext().unregisterReceiver(mNewLeaderboardEntryReceiver);
    }

    protected void updateCursor() {
        if (DEBUG) Log.i(TAG, "updateCursor, segmentId=" + mSegmentId);

        mCursor = mDb.query(Segments.TABLE_SEGMENT_LEADERBOARD,
                SegmentLeaderboardCursorAdapter.FROM,           // columns
                Segments.SEGMENT_ID + "=?",               // selection
                new String[]{mSegmentId + ""},  // selectionArgs
                null, null,
                Segments.RANK + " ASC");

        if (DEBUG) Log.i(TAG, "got new cursor with " + mCursor.getCount() + " entries");

        mCursorAdapter.changeCursor(mCursor);
        mCursorAdapter.notifyDataSetChanged();
    }


    private void initiateRefresh() {
        Log.i(TAG, "initiateRefresh");

        new StravaSegmentsHelper(getContext()).getSegmentLeaderboard(mSegmentId);
    }

    private void onRefreshComplete() {
        Log.i(TAG, "onRefreshComplete");

        updateCursor();

        // Stop the refreshing indicator
        setRefreshing(false);
    }
}
