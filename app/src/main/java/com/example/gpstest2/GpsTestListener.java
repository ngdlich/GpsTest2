package com.example.gpstest2;

import android.location.GpsStatus;
import android.location.LocationListener;

public interface GpsTestListener extends LocationListener {

    @Deprecated
    void onGpsStatusChanged(int event, GpsStatus status);

    void onOrientationChanged(double orientation, double tilt);

}
