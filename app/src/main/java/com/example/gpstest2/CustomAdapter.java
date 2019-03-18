package com.example.gpstest2;

import android.content.Context;
import android.location.GpsSatellite;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomAdapter extends ArrayAdapter<GpsSatellite> {
    private Context context;
    private int resource;
    private List<GpsSatellite> arrSl;

    public CustomAdapter(Context context, int resource, List<GpsSatellite> arrSl) {
        super(context, resource, arrSl);
        this.context = context;
        this.resource = resource;
        this.arrSl = arrSl;
    }



    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_sl, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.tvPrn = (TextView) convertView.findViewById(R.id.tvPrn);
            viewHolder.tvAzi = (TextView) convertView.findViewById(R.id.tvAzi);
            viewHolder.tvEle = (TextView) convertView.findViewById(R.id.tvEle);
            viewHolder.tvSnr = (TextView) convertView.findViewById(R.id.tvSnr);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        GpsSatellite satellite = arrSl.get(position);
        viewHolder.tvPrn.setText(String.valueOf(satellite.getPrn()));
        viewHolder.tvAzi.setText(String.valueOf(satellite.getAzimuth()));
        viewHolder.tvEle.setText(String.valueOf(satellite.getElevation()));
        viewHolder.tvSnr.setText(String.valueOf(satellite.getSnr()));
        return convertView;
    }

    public class ViewHolder {
        TextView tvPrn, tvAzi, tvEle, tvSnr;

    }
}
