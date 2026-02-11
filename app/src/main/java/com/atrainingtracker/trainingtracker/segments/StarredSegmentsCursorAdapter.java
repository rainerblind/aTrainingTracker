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
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsHelper;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;
import com.atrainingtracker.trainingtracker.ui.components.map.MapComponent;
import com.atrainingtracker.trainingtracker.ui.components.map.MapContentType;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.MapView;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by rainer on 10.08.16.
 */

public class StarredSegmentsCursorAdapter extends CursorAdapter {
    protected static final String[] FROM = {Segments.SEGMENT_ID, Segments.C_ID, Segments.SEGMENT_NAME, Segments.CITY, Segments.COUNTRY, Segments.DISTANCE, Segments.AVERAGE_GRADE, Segments.MAXIMUM_GRADE, Segments.ELEVATION_LOW, Segments.ELEVATION_HIGH, Segments.CLIMB_CATEGORY, Segments.PR_TIME};
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

        ViewHolder viewHolder = new ViewHolder(row, mActivity);
        row.setTag(viewHolder);
        return row;
    }

    @Override
    public void bindView(@NonNull View view, @NonNull Context context, @NonNull Cursor cursor) {
        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        final long segmentId = cursor.getLong(cursor.getColumnIndex(Segments.SEGMENT_ID));
        viewHolder.segmentId = segmentId;

        viewHolder.tvName.setText(cursor.getString(cursor.getColumnIndex(Segments.SEGMENT_NAME)));

        int prTimeInSeconds = cursor.getInt(cursor.getColumnIndex(Segments.PR_TIME));
        if (prTimeInSeconds > 0) {
            viewHolder.layoutPr.setVisibility(View.VISIBLE);
            viewHolder.tvPrTime.setText(timeFormatter.format(prTimeInSeconds)); // Use a formatting helper
        } else {
            viewHolder.layoutPr.setVisibility(View.GONE);
        }

        // Set the city text
        String city = cursor.getString(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.CITY));
        if (city != null && !city.isEmpty()) {
            viewHolder.tvCity.setText(city);
            viewHolder.tvCity.setVisibility(View.VISIBLE);
        } else {
            // Hide the view if there is no city data to avoid an empty space
           viewHolder.tvCity.setVisibility(View.GONE);
        }

        viewHolder.tvDistance.setText(distanceFormatter.format_with_units(cursor.getDouble(cursor.getColumnIndex(Segments.DISTANCE))));

        // Prepend the Unicode symbol for average (Ø) to the text.
        String avgGradeText = String.format(Locale.getDefault(), "\u00D8 %.1f%%", cursor.getDouble(cursor.getColumnIndex(Segments.AVERAGE_GRADE)));
        viewHolder.tvAverageGrade.setText(avgGradeText);

        float maxGrade = cursor.getFloat(cursor.getColumnIndexOrThrow(SegmentsDatabaseManager.Segments.MAXIMUM_GRADE));
        viewHolder.tvMaxGrade.setText(String.format(Locale.US, "%.1f%% Max", maxGrade));

        int climbCategory = cursor.getInt(cursor.getColumnIndex(Segments.CLIMB_CATEGORY));
        if (climbCategory > 0) {
            viewHolder.tvClimbCategory.setText(StravaHelper.translateClimbCategory(climbCategory));
            viewHolder.tvClimbCategory.setVisibility(View.VISIBLE);
        } else {
            // Hide the chip if the category is 0 or less (not available)
            viewHolder.tvClimbCategory.setVisibility(View.GONE);
        }

        // Calculate and set Elevation Gain
        double elevHigh = cursor.getDouble(cursor.getColumnIndex(Segments.ELEVATION_HIGH));
        double elevLow = cursor.getDouble(cursor.getColumnIndex(Segments.ELEVATION_LOW));
        long elevationGain = Math.round(elevHigh - elevLow);
        viewHolder.tvElevationGain.setText(String.format(Locale.getDefault(), "%d m", elevationGain));
        viewHolder.tvElevationMin.setText(String.format(Locale.getDefault(), "%d m", Math.round(elevLow)));
        viewHolder.tvElevationMax.setText(String.format(Locale.getDefault(), "%d m", Math.round(elevHigh)));

        if (isPlayServiceAvailable) {
            // Simply delegate to the universal MapComponent
            viewHolder.mapComponent.bind(segmentId, MapContentType.SEGMENT_TRACK);
        } else {
            viewHolder.mapComponent.setVisible(false);
        }

        viewHolder.rowView.setOnClickListener(v -> {
            mShowSegmentDetailsListener.startSegmentDetailsActivity(segmentId);
        });
    }

    /**
     * Helper method to format seconds into a time string (e.g., HH:MM:SS or MM:SS).
     * @param totalSeconds The total time in seconds.
     * @return A formatted string.
     */
    private String formatSeconds(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
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

    public class ViewHolder {

        long segmentId;
        final View rowView;
        final TextView tvClimbCategory;
        final TextView tvName;
        final View layoutPr; // The LinearLayout for the PR
        final TextView tvPrTime;
        final TextView tvCity;
        final TextView tvDistance;
        final TextView tvAverageGrade;
        final TextView tvMaxGrade;
        final TextView tvElevationGain;
        final TextView tvElevationMin;
        final TextView tvElevationMax;
        final MapComponent mapComponent;


        public ViewHolder(View row, Activity activity) {
            // Find all views
            rowView = row;
            tvClimbCategory = row.findViewById(R.id.textViewCategoryChip);
            tvName = row.findViewById(R.id.textViewSegmentName);
            layoutPr = row.findViewById(R.id.layout_pr);
            tvPrTime = row.findViewById(R.id.textViewPrTime);
            tvCity = row.findViewById(R.id.textViewCity);
            tvDistance = row.findViewById(R.id.textViewDistance);
            tvAverageGrade = row.findViewById(R.id.textViewAvgGrade);
            tvElevationGain = row.findViewById(R.id.textViewElevationGain);
            tvElevationMin = row.findViewById(R.id.textViewElevationMin);
            tvElevationMax = row.findViewById(R.id.textViewElevationMax);
            tvMaxGrade = row.findViewById(R.id.textViewMaxGrade);

            MapView mapView = row.findViewById(R.id.mapViewSegment);

            // Create the MapComponent, passing the map's click listener logic
            mapComponent = new MapComponent(mapView, activity, segmentId -> {
                mShowSegmentDetailsListener.startSegmentDetailsActivity(segmentId);
                return null;
            });
        }

    }
}