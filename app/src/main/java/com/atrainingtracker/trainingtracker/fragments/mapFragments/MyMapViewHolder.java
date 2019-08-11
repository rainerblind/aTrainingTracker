package com.atrainingtracker.trainingtracker.fragments.mapFragments;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;

/**
 * Created by rainer on 06.10.16.
 */

public class MyMapViewHolder {
    public GoogleMap map;
    public MapView mapView;

    public MyMapViewHolder(GoogleMap map, MapView mapView) {
        this.map = map;
        this.mapView = mapView;
    }
}
