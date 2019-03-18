package com.example.gpstest2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SkyPlotFragment extends Fragment implements LocationListener, GpsStatus.Listener, GpsTestListener  {

    private SkyPlotView skyPlotView;
    LocationManager locationManager = null;

    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {


        View v = inflater.inflate(R.layout.sky_plot, container, false);
        skyPlotView = v.findViewById(R.id.sky_plot);

        return  v;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);

        locationManager.addGpsStatusListener(this);

    }

    @Override
    public void onGpsStatusChanged(int event) {

        @SuppressLint("MissingPermission") GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        skyPlotView.setSats(gpsStatus);
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onOrientationChanged(double orientation, double tilt) {
        if (skyPlotView != null) {
            skyPlotView.onOrientationChanged(orientation, tilt);
        }
    }
}
