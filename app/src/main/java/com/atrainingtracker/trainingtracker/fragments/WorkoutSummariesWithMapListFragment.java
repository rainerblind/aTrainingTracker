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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.Sensor.formater.DistanceFormater;
import com.atrainingtracker.banalservice.Sensor.formater.TimeFormater;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.Activities.WorkoutDetailsActivity;
import com.atrainingtracker.trainingtracker.Exporter.ExportManager;
import com.atrainingtracker.trainingtracker.Exporter.ExportStatus;
import com.atrainingtracker.trainingtracker.Exporter.ExportType;
import com.atrainingtracker.trainingtracker.Exporter.FileFormat;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.MyMapViewHolder;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.Roughness;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapHelper;
import com.atrainingtracker.trainingtracker.helpers.DeleteWorkoutTask;
import com.atrainingtracker.trainingtracker.interfaces.ReallyDeleteDialogInterface;
import com.atrainingtracker.trainingtracker.interfaces.ShowWorkoutDetailsInterface;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

import java.util.EnumMap;

// import android.view.View.OnClickListener;

public class WorkoutSummariesWithMapListFragment extends ListFragment {
    public static final String TAG = WorkoutSummariesWithMapListFragment.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & false;
    private final IntentFilter mExportStatusChangedFilter = new IntentFilter(ExportManager.EXPORT_STATUS_CHANGED_INTENT);
    private final IntentFilter mFinishedDeletingFilter = new IntentFilter(DeleteWorkoutTask.FINISHED_DELETING);
    protected SQLiteDatabase mDb;
    protected ExportManager mExportManager;
    protected Cursor mCursor;
    protected WorkoutSummaryWithMapAdapter mAdapter;
    private final BroadcastReceiver mExportStatusChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            updateCursor();
            mAdapter.notifyDataSetChanged();
        }
    };
    private final BroadcastReceiver mFinishedDeletingReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            updateCursor();
            mAdapter.notifyDataSetChanged();
        }
    };
    protected ListView mListView;
    private ShowWorkoutDetailsInterface mShowWorkoutDetailsListener;
    private ReallyDeleteDialogInterface mReallyDeleteDialogInterface;
    private boolean isPlayServiceAvailable = true;
    private AbsListView.RecyclerListener mRecycleListener = new AbsListView.RecyclerListener() {

        @Override
        public void onMovedToScrapHeap(View view) {
            ViewHolder holder = (ViewHolder) view.getTag();
            if (holder != null && holder.map != null) {
                // Clear the map and free up resources by changing the map type to none
                holder.map.clear();
                holder.map.setMapType(GoogleMap.MAP_TYPE_NONE);
            }

        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (DEBUG) Log.d(TAG, "onAttach");

        try {
            mShowWorkoutDetailsListener = (ShowWorkoutDetailsInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement UpdateWorkoutInterface");
        }

        try {
            mReallyDeleteDialogInterface = (ReallyDeleteDialogInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ReallyDeleteWorkoutDialogInterface");
        }
    }

    //         @Override
//    public void onActivityCreated(Bundle savedInstanceState)  // TODO: move code to onResume?
//    {
//        super.onActivityCreated(savedInstanceState);
//        if (DEBUG) Log.d(TAG, "onActivityCreated");
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (DEBUG) Log.i(TAG, "onViewCreated");

        mListView = getListView();
//        mListView.setOnItemClickListener(new OnItemClickListener() {
//            public void onItemClick(AdapterView parent, View view, int position, long id) {
//                if (DEBUG) Log.d(TAG, "on ItemClick: view.getId=" + view.getId() + ", position=" + position + " , id=" + id);
//                // mShowWorkoutDetailsListener.startWorkoutDetailsActivity(id, WorkoutDetailsActivity.SelectedFragment.EDIT_DETAILS);
//                // TODO: make the foo here => does not work :-(
//            }
//        });
        mListView.setBackgroundColor(Color.WHITE);
        mListView.setRecyclerListener(mRecycleListener);

        registerForContextMenu(mListView);

        mAdapter = new WorkoutSummaryWithMapAdapter(getActivity(), mCursor);
        setListAdapter(mAdapter);

    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume");

        isPlayServiceAvailable = checkPlayServices();

        mDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        mExportManager = new ExportManager(getActivity(), TAG);
        updateCursor();

        getActivity().registerReceiver(mExportStatusChangedReceiver, mExportStatusChangedFilter);
        getActivity().registerReceiver(mFinishedDeletingReceiver, mFinishedDeletingFilter);

    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause");

        try {
            getActivity().unregisterReceiver(mExportStatusChangedReceiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            getActivity().unregisterReceiver(mFinishedDeletingReceiver);
        } catch (IllegalArgumentException e) {
        }

        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();
        //if (mCursor != null)  {
        //    // mCursor.close();
        //    mCursor = null;
        //}
        mExportManager.onFinished(TAG);
    }

    // TODO: rename
    // public void requeryCursor()
    // {
    //    	if (DEBUG) Log.d(TAG, "requeryCursor");
    //   	if (mCursor != null) {
    //		mCursor.requery();
    //	}
    //}

    /**
     * Called first time user clicks on the menu button
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu");

        inflater.inflate(R.menu.workout_list_menu, menu);
    }

    protected void updateCursor() {
        if (DEBUG) Log.i(TAG, "updateCursor");

        mCursor = mDb.query(WorkoutSummaries.TABLE,
                null,
                null,
                null,
                null,
                null,
                WorkoutSummaries.TIME_START + " DESC");

        mAdapter.changeCursor(mCursor);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.workout_summaries_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (DEBUG) Log.i(TAG, "onContextItemSelected");

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            // case R.id.contextEdit:
            //    mUpdateWorkoutListener.updateWorkout(info.id);
            //    return true;
            case R.id.contextDelete:
                mReallyDeleteDialogInterface.confirmDeleteWorkout(info.id);
                return true;
            case R.id.csvWrite:
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.CSV);
                return true;
            case R.id.jsonWrite:
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.GC);
                return true;
            case R.id.tcxWrite:
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.TCX);
                return true;
            case R.id.gpxWrite:
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.GPX);
                return true;
            case R.id.stravaUpload:
                if (DEBUG) Log.i(TAG, "uplading to Strava selected");
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.STRAVA);
                return true;
            case R.id.runkeeperUpload:
                if (DEBUG) Log.i(TAG, "uploading to RunKeeper selected");
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.RUNKEEPER);
                return true;
            case R.id.trainingPeaksUpload:
                if (DEBUG) Log.i(TAG, "uploading to TrainingPeaks selected");
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.TRAINING_PEAKS);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void setStatusInfo(ViewHolder viewHolder, final Context context, final String fileBaseName) {
        if (DEBUG) Log.d(TAG, "setStatusInfo: " + fileBaseName);

        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> foo = mExportManager.getExportStatus(fileBaseName);
        if (DEBUG && foo == null) Log.d(TAG, "WTF: foo == null");

        for (ExportType exportType : ExportType.values()) {
            if (DEBUG) Log.d(TAG, "looking for " + exportType);

            String text = context.getString(R.string.something_strange_happened);
            FileFormat processingFileFormat = FileFormat.CSV;

            ImageView ivStatus = new ImageView(context);
            switch (exportType) {
                case FILE:
                    ivStatus = viewHolder.ivFile;
                    break;
                case DROPBOX:
                    ivStatus = viewHolder.ivDropbox;
                    break;
                case COMMUNITY:
                    ivStatus = viewHolder.ivCommunities;
                    break;
            }

            EnumMap<ExportStatus, Integer> exportStatusCounter = new EnumMap<ExportStatus, Integer>(ExportStatus.class);
            // initialize with 0
            for (ExportStatus exportStatus : ExportStatus.values()) {
                exportStatusCounter.put(exportStatus, 0);
            }

            EnumMap<FileFormat, ExportStatus> bar = foo.get(exportType);
            for (FileFormat fileFormat : FileFormat.values()) {
                ExportStatus exportStatus = bar.get(fileFormat);
                // avoid a NullPointer Exception when there is a workout in the summaries DB but not in the exportStatus DB
                if (exportStatus != null && exportStatusCounter != null && exportStatusCounter.get(exportStatus) != null) {
                    exportStatusCounter.put(exportStatus, exportStatusCounter.get(exportStatus) + 1);
                    if (exportStatus == ExportStatus.PROCESSING) {
                        processingFileFormat = fileFormat;
                    }
                }
            }

            int numberOfFileFormats = FileFormat.values().length;
            int unwanted = exportStatusCounter.get(ExportStatus.UNWANTED);
            int wanted = numberOfFileFormats - unwanted;

            if (unwanted == numberOfFileFormats) {

                ivStatus.setImageResource(R.drawable.ic_not_interested_black_24dp);

                switch (exportType) {
                    case FILE:
                        text = context.getString(R.string.exporting_to_file_not_wanted);
                        break;
                    case DROPBOX:
                        text = context.getString(R.string.uploading_to_dropbox_not_wanted);
                        break;
                    case COMMUNITY:
                        text = context.getString(R.string.uploading_to_community_not_wanted);
                        break;
                }
            } else if (exportStatusCounter.get(ExportStatus.FINISHED_FAILED) == 1) {

                ivStatus.setImageResource(R.drawable.export_failed);

                switch (exportType) {
                    case FILE:
                        text = context.getString(R.string.exporting_to_file_failed_for_one, wanted);
                        break;
                    case DROPBOX:
                        text = context.getString(R.string.uploading_to_dropbox_failed_for_one, wanted);
                        break;
                    case COMMUNITY:
                        text = context.getString(R.string.uploading_to_community_failed_for_one, wanted);
                        break; // TODO: which one???
                }
            } else if (exportStatusCounter.get(ExportStatus.FINISHED_FAILED) > 1) {

                ivStatus.setImageResource(R.drawable.export_failed);
                int failed = exportStatusCounter.get(ExportStatus.FINISHED_FAILED);
                switch (exportType) {
                    case FILE:
                        text = context.getString(R.string.exporting_to_file_failed_for_several, failed, wanted);
                        break;
                    case DROPBOX:
                        text = context.getString(R.string.uploading_to_dropbox_failed_for_several, failed, wanted);
                        break;
                    case COMMUNITY:
                        text = context.getString(R.string.uploading_to_community_failed_for_several, failed, wanted);
                        break;
                }

            } else if (exportStatusCounter.get(ExportStatus.FINISHED_SUCCESS) == wanted) {

                ivStatus.setImageResource(R.drawable.export_success);

                switch (exportType) {
                    case FILE:
                        text = context.getString(R.string.successfully_exported_to_file, wanted, wanted);
                        break;
                    case DROPBOX:
                        text = context.getString(R.string.successfully_uploaded_to_dropbox, wanted, wanted);
                        break;
                    case COMMUNITY:
                        text = context.getString(R.string.successfully_uploaded_to_community, wanted, wanted);
                        break;
                }

            } else if (exportStatusCounter.get(ExportStatus.PROCESSING) > 0) {
                ivStatus.setImageResource(R.drawable.ic_cached_black_24dp);
                String fileFormat = processingFileFormat.toString();
                int finished = exportStatusCounter.get(ExportStatus.FINISHED_SUCCESS) + 1;
                switch (exportType) {
                    case FILE:
                        text = context.getString(R.string.exporting_to_file, fileFormat, finished, wanted);
                        break;
                    case DROPBOX:
                        text = context.getString(R.string.uploading_to_dropbox, fileFormat, finished, wanted);
                        break;
                    case COMMUNITY:
                        text = context.getString(R.string.uploading_to_community, fileFormat, finished, wanted);
                        break;
                }

            } else if (exportStatusCounter.get(ExportStatus.TRACKING) > 0) {
                ivStatus.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
                text = context.getString(R.string.tracking);
            } else if (exportStatusCounter.get(ExportStatus.WAITING) > 0) {
                ivStatus.setImageResource(R.drawable.ic_hourglass_empty_black_24dp);
                int waiting = exportStatusCounter.get(ExportStatus.WAITING);
                switch (exportType) {
                    case FILE:
                        text = context.getString(R.string.waiting_to_export_to_file, waiting, wanted);
                        break;
                    case DROPBOX:
                        text = context.getString(R.string.waiting_to_upload_to_dropbox, waiting, wanted);
                        break;
                    case COMMUNITY:
                        text = context.getString(R.string.waiting_to_upload_to_community, waiting, wanted);
                        break;
                }

            } else {
                if (DEBUG) Log.d(TAG, "case not handled");
                ivStatus.setImageResource(R.drawable.export_error);
            }

            if (DEBUG) Log.d(TAG, text);

            TextView textView = new TextView(context);
            switch (exportType) {
                case FILE:
                    textView = viewHolder.tvFile;
                    break;
                case DROPBOX:
                    textView = viewHolder.tvDropbox;
                    break;
                case COMMUNITY:
                    textView = viewHolder.tvCommumities;
                    break;
            }
            textView.setText(text);
        }

    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return (apiAvailability.isGooglePlayServicesAvailable(getContext()) == ConnectionResult.SUCCESS);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // finally, the adapter
    ////////////////////////////////////////////////////////////////////////////////////////////////
    class WorkoutSummaryWithMapAdapter
            extends CursorAdapter {
        private final String TAG = WorkoutSummaryWithMapAdapter.class.getName();
        private final boolean DEBUG = TrainingApplication.DEBUG & true;

        protected Context mContext = null;

        protected ShowWorkoutDetailsInterface mUpdateWorkoutListener = null;


        public WorkoutSummaryWithMapAdapter(Activity activity, Cursor cursor) {
            super(activity, cursor, 0);
            if (DEBUG) Log.d(TAG, "WorkoutSummmarywithMapAdapter");

            mContext = activity;

            try {
                mUpdateWorkoutListener = (ShowWorkoutDetailsInterface) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString() + " must implement ShowWorkoutDetailsInterface");
            }
        }


        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            if (DEBUG) Log.i(TAG, "newView");

            View row = LayoutInflater.from(context).inflate(R.layout.workout_summaries_with_map_row, null);

            // ??? LinearLayout llRow = (LinearLayout) row.findViewById(R.id.ll_workout_summaries_row);

            ViewHolder viewHolder = new ViewHolder(null, null);
            // workoutId is set in bindView()
            // GoogleMap is set during initialization
            // MapView   is set in a few seconds

            viewHolder.llSummary = row.findViewById(R.id.ll_workout_summaries_summary);
            viewHolder.tvDateAndTime = row.findViewById(R.id.tv_workout_summaries_date_and_time);
            viewHolder.tvName = row.findViewById(R.id.tv_workout_summaries_name);
            viewHolder.tvDistanceTypeAndDuration = row.findViewById(R.id.tv_worktout_summaries_distance_type_and_duration);
            viewHolder.mapView = row.findViewById(R.id.workout_summaries_mapView);
            viewHolder.llExportStatus = row.findViewById(R.id.ll_workout_summaries_export_status);
            viewHolder.ivFile = row.findViewById(R.id.iv_workout_summaries_export_status_file);
            viewHolder.tvFile = row.findViewById(R.id.tv_workout_summaries_export_status_file);
            viewHolder.ivDropbox = row.findViewById(R.id.iv_workout_summaries_export_status_dropbox);
            viewHolder.tvDropbox = row.findViewById(R.id.tv_workout_summaries_export_status_dropbox);
            viewHolder.ivCommunities = row.findViewById(R.id.iv_workout_summaries_export_status_communities);
            viewHolder.tvCommumities = row.findViewById(R.id.tv_workout_summaries_export_status_communities);

            viewHolder.initializeMapView();

            row.setTag(viewHolder);
            return row;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            if (DEBUG) Log.i(TAG, "bindView");

            final long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
            String fileBaseName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));

            // Text for distance_type_and_duration
            double distance_m = cursor.getDouble(cursor.getColumnIndex(WorkoutSummaries.DISTANCE_TOTAL_m));
            String sport = SportTypeDatabaseManager.getUIName(cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID)));
            double time_s = cursor.getDouble(cursor.getColumnIndex(WorkoutSummaries.TIME_TOTAL_s));
            String distanceTypeAndDuration = context.getString(R.string.distance_distanceUnit_sport_time_format,
                    (new DistanceFormater()).format(distance_m),
                    context.getString(MyHelper.getDistanceUnitNameId()),
                    sport,
                    (new TimeFormater()).format(time_s));

            ViewHolder viewHolder = (ViewHolder) view.getTag();
            viewHolder.workoutId = workoutId;

            // first, configure the different OnClickListeners
            viewHolder.llSummary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TrainingApplication.startWorkoutDetailsActivity(workoutId, WorkoutDetailsActivity.SelectedFragment.EDIT_DETAILS);
                }
            });

            viewHolder.llExportStatus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mUpdateWorkoutListener.showExportStatusDialog(workoutId);
                }
            });

            // now, set the values of the views
            // viewHolder.tvDateAndTime.setText(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.TIME_START)));
            viewHolder.tvDateAndTime.setText(WorkoutSummariesDatabaseManager.getStartTime(workoutId, "localtime"));


            viewHolder.tvName.setText(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.WORKOUT_NAME)));
            viewHolder.tvDistanceTypeAndDuration.setText(distanceTypeAndDuration);

            if (isPlayServiceAvailable) {
                viewHolder.mapView.setVisibility(View.VISIBLE);
                if (viewHolder.map != null) {
                    viewHolder.showTrackOnMap(workoutId);
                }
            } else {
                viewHolder.mapView.setVisibility(View.GONE);
            }

            setStatusInfo(viewHolder, context, fileBaseName);
        }
    }

    class ViewHolder
            extends MyMapViewHolder
            implements OnMapReadyCallback {

        long workoutId;
        LinearLayout llSummary;
        TextView tvDateAndTime;
        TextView tvName;
        TextView tvDistanceTypeAndDuration;
        LinearLayout llExportStatus;

        // MapView mapView;
        // GoogleMap map;
        ImageView ivFile;
        TextView tvFile;
        ImageView ivDropbox;
        TextView tvDropbox;
        ImageView ivCommunities;
        TextView tvCommumities;

        public ViewHolder(GoogleMap map, MapView mapView) {
            super(map, mapView);
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            MapsInitializer.initialize(getActivity());
            // -MapsInitializer.initialize(getActivity().getApplicationContext());
            map = googleMap;
            showTrackOnMap(workoutId);
        }

        /**
         * Initialises the MapView by calling its lifecycle methods.
         */
        public void initializeMapView() {
            if (mapView != null) {
                // Initialise the MapView
                mapView.onCreate(null);
                // Set the map ready callback to receive the GoogleMap object
                mapView.getMapAsync(this);
            }
        }

        public void showTrackOnMap(final long workoutId) {
            if (DEBUG) Log.i(TAG, "showMainTrackOnMap: workoutId=" + workoutId);

            if (map == null) {
                mapView.setVisibility(View.GONE);
            } else {
                mapView.setVisibility(View.VISIBLE);

                // first, configure the map
                map.getUiSettings().setMapToolbarEnabled(false);
                map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        TrainingApplication.startWorkoutDetailsActivity(workoutId, WorkoutDetailsActivity.SelectedFragment.MAP);
                    }
                });

                ((TrainingApplication) getActivity().getApplication()).trackOnMapHelper.showTrackOnMap(this, workoutId, Roughness.MEDIUM, TrackOnMapHelper.TrackType.BEST, true, false);

                if (DEBUG) Log.i(TAG, "end of showTrackOnMap()");
            }
        }
    }
}
