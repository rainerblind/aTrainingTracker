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

package com.atrainingtracker.trainingtracker.fragments.aftermath;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.ListFragment;
import androidx.cursoradapter.widget.CursorAdapter;
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
import android.widget.ListView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.activities.WorkoutDetailsActivity;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;
import com.atrainingtracker.trainingtracker.exporter.ExportManager;
import com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.helpers.DeleteWorkoutThread;
import com.atrainingtracker.trainingtracker.interfaces.ReallyDeleteDialogInterface;
import com.atrainingtracker.trainingtracker.interfaces.ShowWorkoutDetailsInterface;
import com.atrainingtracker.trainingtracker.ui.components.map.MapComponent;
import com.atrainingtracker.trainingtracker.ui.components.map.MapContentType;
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionData;
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider;
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.EditDescriptionDialogFragment;
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionViewHolder;
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusViewHolder;
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData;
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider;
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaValuesViewHolder;
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData;
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider;
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsViewHolder;
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.ChangeSportAndEquipmentDialogFragment;
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.EditWorkoutNameDialogFragment;
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData;
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider;
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderViewHolder;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.MapView;

import java.util.List;


public class WorkoutSummariesListFragment extends ListFragment {

    public static final String TAG = WorkoutSummariesListFragment.class.getSimpleName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private final IntentFilter mExportStatusChangedFilter = new IntentFilter(ExportStatusChangedBroadcaster.EXPORT_STATUS_CHANGED_INTENT);
    private final IntentFilter mFinishedDeletingFilter = new IntentFilter(DeleteWorkoutThread.FINISHED_DELETING);
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (DEBUG) Log.d(TAG, "onAttach");

        try {
            mShowWorkoutDetailsListener = (ShowWorkoutDetailsInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement UpdateWorkoutInterface");
        }

        try {
            mReallyDeleteDialogInterface = (ReallyDeleteDialogInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement ReallyDeleteWorkoutDialogInterface");
        }
    }

    //         @Override
//    public void onActivityCreated(Bundle savedInstanceState)  // TODO: move code to onResume?
//    {
//        super.onActivityCreated(savedInstanceState);
//        if (DEBUG) Log.d(TAG, "onActivityCreated");
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
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
        mExportManager = new ExportManager(getActivity());
        updateCursor();

        ContextCompat.registerReceiver(getActivity(), mExportStatusChangedReceiver, mExportStatusChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(getActivity(), mFinishedDeletingReceiver, mFinishedDeletingFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
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
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
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
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.workout_summaries_context, menu);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
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
                if (DEBUG) Log.i(TAG, "uploading to Strava selected");
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.STRAVA);
                return true;
            /* case R.id.runkeeperUpload:
                if (DEBUG) Log.i(TAG, "uploading to RunKeeper selected");
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.RUNKEEPER);
                return true; */
            /* case R.id.trainingPeaksUpload:
                if (DEBUG) Log.i(TAG, "uploading to TrainingPeaks selected");
                mShowWorkoutDetailsListener.exportWorkout(info.id, FileFormat.TRAINING_PEAKS);
                return true; */
            default:
                return super.onContextItemSelected(item);
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
        private final boolean DEBUG = TrainingApplication.getDebug(true);

        protected final Context mContext;

        protected ShowWorkoutDetailsInterface mUpdateWorkoutListener;

        private final WorkoutHeaderDataProvider headerDataProvider;
        private final WorkoutDetailsDataProvider detailsDataProvider;
        private final ExtremaDataProvider extremaDataProvider;
        private final DescriptionDataProvider descriptionDataProvider;


        // TODO: move other DataProviders to here.


        public WorkoutSummaryWithMapAdapter(Activity activity, Cursor cursor) {
            super(activity, cursor, 0);
            if (DEBUG) Log.d(TAG, "WorkoutSummaryWithMapAdapter");

            mContext = activity;

            try {
                mUpdateWorkoutListener = (ShowWorkoutDetailsInterface) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity + " must implement ShowWorkoutDetailsInterface");
            }

            // Instantiate data providers
            headerDataProvider = new WorkoutHeaderDataProvider(mContext, new EquipmentDbHelper(mContext));
            detailsDataProvider = new WorkoutDetailsDataProvider();
            extremaDataProvider = new ExtremaDataProvider(mContext);
            descriptionDataProvider = new DescriptionDataProvider();
        }


        @NonNull
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            if (DEBUG) Log.i(TAG, "newView");

            View row = LayoutInflater.from(context).inflate(R.layout.workout_summaries_row, parent, false);
            ViewHolder viewHolder = new ViewHolder(row, (Activity) mContext);

            row.setTag(viewHolder);
            return row;
        }

        @Override
        public void bindView(@NonNull View view, @NonNull Context context, @NonNull Cursor cursor) {
            if (DEBUG) Log.i(TAG, "bindView");

            final long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));

            ViewHolder viewHolder = (ViewHolder) view.getTag();
            viewHolder.workoutId = workoutId;

            // --- now, set the values of the views

            // -- header
            if (viewHolder.headerViewHolder != null) {
                WorkoutHeaderData headerData = headerDataProvider.createWorkoutHeaderData(cursor);
                viewHolder.headerViewHolder.bind(headerData);


                // attach long-click listener for changing the workout name
                viewHolder.headerViewHolder.getWorkoutNameView().setOnLongClickListener(v -> {
                    if (DEBUG) Log.d(TAG, "Workout name long-clicked for workoutId: " + workoutId);

                    // Get the current workout name from the header data
                    String currentWorkoutName = headerData.getWorkoutName();

                    // Create the dialog instance.
                    EditWorkoutNameDialogFragment dialogFragment = EditWorkoutNameDialogFragment.newInstance(currentWorkoutName);

                    // define the callback action
                    dialogFragment.setOnWorkoutNameChanged(newName -> {
                        // When the name is changed:
                        // First, update the database.
                        WorkoutSummariesDatabaseManager.updateWorkoutName(workoutId, newName);

                        // Then, update the cursor to refresh the UI.
                        if (DEBUG) Log.d(TAG, "onWorkoutNameChanged callback received. Updating cursor.");
                        updateCursor();

                        return null; // Return null to satisfy Kotlin's Unit
                    });

                    // Show the dialog
                    dialogFragment.show(getChildFragmentManager(), "EditWorkoutNameDialogFragment");

                    return true; // Consume the event
                });

                // attach long-click listener for changing sport and equipment
                viewHolder.headerViewHolder.getSportContainerView().setOnLongClickListener(v -> {

                    String currentEquipmentName = headerData.getEquipmentName();
                    long currentSportId = headerData.getSportId();

                    // Create the dialog instance
                    ChangeSportAndEquipmentDialogFragment dialogFragment = ChangeSportAndEquipmentDialogFragment.newInstance(currentSportId, currentEquipmentName);

                    // Set the "onSave" lambda.
                    dialogFragment.setOnSave((newSportId, newEquipmentId) -> {
                        // update the db
                        WorkoutSummariesDatabaseManager.updateSportAndEquipment(workoutId, newSportId, newEquipmentId);

                        // Refresh the UI
                        updateCursor();
                        return null; // For Kotlin Unit
                    });

                    // 3. Show the dialog
                    dialogFragment.show(getChildFragmentManager(), "ChangeSportAndEquipmentDialog");

                    return true; // Consume long click
                });

            }

            // -- description
            if (viewHolder.descriptionViewHolder != null) {
                DescriptionData descriptionData = descriptionDataProvider.createDescriptionData(cursor);
                viewHolder.descriptionViewHolder.bind(descriptionData);

                // --- Set the long click listener for editing ---
                viewHolder.descriptionViewHolder.getRootView().setOnLongClickListener(v -> {
                    // Create the "dumb" dialog instance
                    EditDescriptionDialogFragment dialogFragment = EditDescriptionDialogFragment.newInstance(
                            descriptionData.getDescription(),
                            descriptionData.getGoal(),
                            descriptionData.getMethod()
                    );

                    // Set the callback -> update the database
                    dialogFragment.setOnDescriptionChanged((newDescription, newGoal, newMethod) -> {
                        WorkoutSummariesDatabaseManager.updateDescription(workoutId, newDescription, newGoal, newMethod);
                        updateCursor();
                        return null; // for Kotlin Unit
                    });

                    // show the dialog
                    dialogFragment.show(getChildFragmentManager(), "EditDescriptionDialog");
                    return true; // Consume the long click
                });
            }

            // --workout details
            if (viewHolder.detailsViewHolder != null) {
                WorkoutDetailsData detailsData = detailsDataProvider.createWorkoutDetailsData(cursor, workoutId);
                viewHolder.detailsViewHolder.bind(detailsData);
            }

            // -- extrema values
            long sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID));
            BSportType bSportType = SportTypeDatabaseManager.getBSportType(sportId);
            List<ExtremaData> extremaList = extremaDataProvider.getExtremaDataList(workoutId, bSportType);
            if (viewHolder.extremaValuesViewHolder != null) {
                viewHolder.extremaValuesViewHolder.bind(extremaList);
            }

            // -- map
            if (viewHolder.mapComponent != null) {
                if (isPlayServiceAvailable) {
                    viewHolder.mapComponent.bind(workoutId, MapContentType.WORKOUT_TRACK); // Just call bind!
                } else {
                    viewHolder.mapComponent.setVisible(false);
                }
            }

            String fileBaseName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));
            if (viewHolder.exportStatusViewHolder != null) {
                viewHolder.exportStatusViewHolder.bind(fileBaseName);
            }

            // --- Click listeners
            // first, create a click listener
            View.OnClickListener detailsClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DEBUG) Log.d(TAG, "Details area clicked for workoutId: " + workoutId);
                    TrainingApplication.startWorkoutDetailsActivity(workoutId, WorkoutDetailsActivity.SelectedFragment.EDIT_DETAILS);
                }
            };

            // add the (short) click listener to all the relevant views.
            if (viewHolder.headerViewHolder != null) {
                viewHolder.headerViewHolder.getView().setOnClickListener(detailsClickListener);
                viewHolder.detailsViewHolder.getView().setOnClickListener(detailsClickListener);
                viewHolder.extremaValuesViewHolder.getView().setOnClickListener(detailsClickListener);
                viewHolder.headerViewHolder.getWorkoutNameView().setOnClickListener(detailsClickListener);
                viewHolder.headerViewHolder.getSportContainerView().setOnClickListener(detailsClickListener);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // ViewHolder
    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ViewHolder {

        final WorkoutHeaderViewHolder headerViewHolder;
        final WorkoutDetailsViewHolder detailsViewHolder;
        final DescriptionViewHolder descriptionViewHolder;
        final ExtremaValuesViewHolder extremaValuesViewHolder;
        final ExportStatusViewHolder exportStatusViewHolder;
        final MapComponent mapComponent;

        long workoutId;

        public ViewHolder(View row, Activity activity) {
            // Find component views
            View headerView = row.findViewById(R.id.workout_header_include);
            View detailsView = row.findViewById(R.id.workout_details_include);
            View extremaValuesView = row.findViewById(R.id.extrema_values_include);
            View descriptionView = row.findViewById(R.id.workout_description_include);
            View exportStatusView = row.findViewById(R.id.export_status_include);
            MapView mapView = row.findViewById(R.id.workout_summaries_mapView);

            // Create component ViewHolders/Components
            this.headerViewHolder = (headerView != null) ? new WorkoutHeaderViewHolder(headerView) : null;
            this.detailsViewHolder = (detailsView != null) ? new WorkoutDetailsViewHolder(detailsView, activity) : null;
            this.extremaValuesViewHolder = (extremaValuesView != null) ? new ExtremaValuesViewHolder(extremaValuesView) : null;
            this.descriptionViewHolder = (descriptionView != null) ? new DescriptionViewHolder(descriptionView) : null;
            this.exportStatusViewHolder = (exportStatusView != null) ? new ExportStatusViewHolder(exportStatusView) : null;

            this.mapComponent = new MapComponent(mapView, activity, workoutId -> {
                // When the map is clicked, start the details activity for that workout, showing the map fragment.
                TrainingApplication.startWorkoutDetailsActivity(workoutId, WorkoutDetailsActivity.SelectedFragment.MAP);
                return null;
            });
        }
    }
}
