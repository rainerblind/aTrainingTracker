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

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.MyMapViewHolder;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.Roughness;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsHelper;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by rainer on 10.08.16.
 */

public class StarredSegmentsCursorAdapter extends CursorAdapter {
    protected static final String[] FROM = {Segments.SEGMENT_ID, Segments.C_ID, Segments.SEGMENT_NAME, Segments.DISTANCE, Segments.AVERAGE_GRADE, Segments.CLIMB_CATEGORY};
    private final String TAG = StarredSegmentsCursorAdapter.class.getSimpleName();
    private final boolean DEBUG = TrainingApplication.getDebug(false);
    protected final Activity mActivity;
    protected final Context mContext;
    // protected static final int[]    TO   = {R.id.tvSegmentName,  R.id.tvSegmentName, R.id.tvSegmentName,    R.id.tvSegmentDistance, R.id.tvSegmentAverageGrade, R.id.tvSegmentClimbCategory, R.id.tvSegmentPRTime, R.id.tvSegmentRank, R.id.tvSegmentPRDate, R.id.tvSegmentLastUpdated};
    @Nullable
    ShowSegmentDetailsInterface mShowSegmentDetailsListener = null;
    final DistanceFormatter distanceFormatter = new DistanceFormatter();
    final TimeFormatter timeFormatter = new TimeFormatter();
    final SimpleDateFormat dateAndTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); // 2013-03-29T13:49:35Z
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);               // 2013-03-29
    final StravaSegmentsHelper mStravaSegmentsHelper;
    private boolean isPlayServiceAvailable = true;

    public StarredSegmentsCursorAdapter(Activity activity, Cursor cursor, StravaSegmentsHelper stravaSegmentsHelper, ShowSegmentDetailsInterface showSegmentDetailsInterface) {
        super(activity, cursor, 0);

        mContext = activity;
        mActivity = activity;
        mShowSegmentDetailsListener = showSegmentDetailsInterface;

        mStravaSegmentsHelper = stravaSegmentsHelper;

        isPlayServiceAvailable = checkPlayServices();
    }

    @NonNull
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (DEBUG) Log.i(TAG, "newView");

        View row = LayoutInflater.from(context).inflate(R.layout.segment_list_row, null);

        ViewHolder viewHolder = new ViewHolder(null, null);

        // set all the views of the view holder
        viewHolder.tvName = row.findViewById(R.id.tvSegmentName);
        viewHolder.tvDistance = row.findViewById(R.id.tvSegmentDistance);
        viewHolder.tvAverageGrade = row.findViewById(R.id.tvSegmentAverageGrade);
        viewHolder.tvClimbCategory = row.findViewById(R.id.tvSegmentClimbCategory);
        viewHolder.mapView = row.findViewById(R.id.starred_segments_mapView);

        viewHolder.llSegmentsHeader = row.findViewById(R.id.llSegmentsHeader);

        viewHolder.initializeMapView();

        row.setTag(viewHolder);

        return row;
    }

    @Override
    public void bindView(@NonNull View view, @NonNull Context context, @NonNull Cursor cursor) {
        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        final long segmentId = cursor.getLong(cursor.getColumnIndex(Segments.SEGMENT_ID));
        viewHolder.segmentId = segmentId;

        viewHolder.tvName.setText(cursor.getString(cursor.getColumnIndex(Segments.SEGMENT_NAME)));
        viewHolder.tvDistance.setText(distanceFormatter.format_with_units(cursor.getDouble(cursor.getColumnIndex(Segments.DISTANCE))));
        viewHolder.tvAverageGrade.setText(String.format(Locale.getDefault(), "%.1f %%", cursor.getDouble(cursor.getColumnIndex(Segments.AVERAGE_GRADE))));
        viewHolder.tvClimbCategory.setText(StravaHelper.translateClimbCategory(cursor.getInt(cursor.getColumnIndex(Segments.CLIMB_CATEGORY))));

        if (isPlayServiceAvailable) {
            viewHolder.mapView.setVisibility(View.VISIBLE);
            if (viewHolder.map != null) {
                viewHolder.showSegmentOnMap(segmentId);
            }
        } else {
            viewHolder.mapView.setVisibility(View.GONE);
        }

        viewHolder.llSegmentsHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mShowSegmentDetailsListener.startSegmentDetailsActivity(segmentId);
            }
        });

    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return (apiAvailability.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS);
    }


    public interface ShowSegmentDetailsInterface {
        void startSegmentDetailsActivity(long segmentId);
    }

    public class ViewHolder
            extends MyMapViewHolder
            implements OnMapReadyCallback {

        long segmentId;
        TextView tvName;
        TextView tvDistance;
        TextView tvAverageGrade;
        TextView tvClimbCategory;
        LinearLayout llSegmentsHeader;

        public ViewHolder(GoogleMap map, MapView mapView) {
            super(map, mapView);
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            MapsInitializer.initialize(mContext);
            map = googleMap;
            showSegmentOnMap(segmentId);
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

        public void showSegmentOnMap(final long segmentId) {
            if (DEBUG) Log.i(TAG, "showSegmentOnMap: segmentId=" + segmentId);

            if (map == null) {
                mapView.setVisibility(View.GONE);
            } else {
                mapView.setVisibility(View.VISIBLE);

                // first, configure the map
                map.getUiSettings().setMapToolbarEnabled(false);
                map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        mShowSegmentDetailsListener.startSegmentDetailsActivity(segmentId);
                    }
                });

                ((TrainingApplication) mActivity.getApplication()).segmentOnMapHelper.showSegmentOnMap(mContext, this, segmentId, Roughness.ALL, true, false);

                if (DEBUG) Log.i(TAG, "end of showSegmentOnMap()");
            }
        }
    }
}