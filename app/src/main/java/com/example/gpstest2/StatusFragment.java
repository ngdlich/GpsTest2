package com.example.gpstest2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GpsSatellite;
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
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;


public class StatusFragment extends Fragment  implements LocationListener, GpsStatus.Listener{

    TextView tvLat, tvLong, tvInView;
    LocationManager locationManager = null;
    ListView lv;
    ArrayList<GpsSatellite> arrSl = new ArrayList<>();

    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {


        View v = inflater.inflate(R.layout.status, container, false);
        tvLat = v.findViewById(R.id.tvLat);
        tvLong = v.findViewById(R.id.tvLong);
        tvInView = v.findViewById(R.id.tvInView);
        lv = v.findViewById(R.id.lv);

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

        //Retrieves information about the current status of the GPS engine
        @SuppressLint("MissingPermission") GpsStatus gpsStatus = locationManager.getGpsStatus(null);

        if(gpsStatus != null) {
            //Returns an array of GpsSatellite objects, which represent the current state of the GPS engine.
            Iterable<GpsSatellite>satellites = gpsStatus.getSatellites();
            Iterator<GpsSatellite> sat = satellites.iterator();
            int i=0;
            arrSl.clear();

            while (sat.hasNext()) {
                i++;
                GpsSatellite satellite = sat.next();
                arrSl.add(satellite);
            }

            if(getActivity() != null){
                CustomAdapter arrayAdapter = new CustomAdapter(getActivity(), R.layout.item_sl, arrSl);
                lv.setAdapter(arrayAdapter);
                tvInView.setText(String.valueOf(i));
            }



        }
    }

    @Override
    public void onLocationChanged(Location location) {

        tvLat.setText(String.valueOf(location.getLatitude()));
        tvLong.setText(String.valueOf(location.getLongitude()));
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
}

