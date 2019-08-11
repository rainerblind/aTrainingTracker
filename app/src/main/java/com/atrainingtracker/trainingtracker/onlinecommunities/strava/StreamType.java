package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;

/**
 * Created by rainer on 07.09.16.
 */

public enum StreamType {
    SEGMENT(Segments.TABLE_SEGMENT_STREAMS, Segments.SEGMENT_ID, "segments", "latlng, distance, altitude"),
    SEGMENT_EFFORT(Segments.TABLE_EFFORT_STREAMS, Segments.EFFORT_ID, "segment_efforts", "latlng, distance, heartrate, watts, cadence, velocity_smooth");

    public final String table;
    public final String idName;
    public final String urlPart;
    public final String requestStreamTypes;

    StreamType(String table, String idName, String urlPart, String requestStreamTypes) {
        this.table = table;
        this.idName = idName;
        this.urlPart = urlPart;
        this.requestStreamTypes = requestStreamTypes;
    }

}
