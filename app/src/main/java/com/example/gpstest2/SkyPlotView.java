package com.example.gpstest2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Iterator;

public class SkyPlotView extends View implements LocationListener, GpsStatus.Listener, GpsTestListener {

    private static int height, width;

    private double mOrientation = 0.0;

    private static int SAT_RADIUS;

    int minScreen, countSl;

    ArrayList<GpsSatellite> arrSl;

    Context mContext;
    WindowManager windowManager;
    LocationManager locationManager = null;
    private Paint gridP, horizonP, prnIdPaint, mSatelliteFillPaint;

    private boolean mStarted;

    private float mSnrCn0s[], mElevs[], mAzims[];  // Holds either SNR or C/N0 - see #65

    private float mSnrCn0UsedAvg = 0.0f;

    private float mSnrCn0InViewAvg = 0.0f;

    private boolean mHasEphemeris[], mHasAlmanac[], mUsedInFix[];

    private int mPrns[], mConstellationType[];

    private int mSvCount;

    private boolean mUseLegacyGnssApi = false;

    private boolean mIsSnrBad = false;

    public SkyPlotView(Context context) {
        super(context);
        init(context);
    }

    public SkyPlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SkyPlotView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public SkyPlotView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    @SuppressLint("MissingPermission")
    private void init(Context context) {

        getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        height = getHeight();
                        width = getWidth();
                        return true;
                    }
                }
        );

        mContext = context;
        windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        SAT_RADIUS = convertDpToPixels(7, context);

        gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridP.setColor(Color.BLUE);
        gridP.setStyle(Paint.Style.STROKE);

        horizonP = new Paint(Paint.ANTI_ALIAS_FLAG);
        horizonP.setColor(Color.RED);
        horizonP.setStyle(Paint.Style.STROKE);

        prnIdPaint = new Paint();
        prnIdPaint.setColor(Color.BLACK);
        prnIdPaint.setStyle(Paint.Style.STROKE);
        prnIdPaint
                .setTextSize(convertDpToPixels(SAT_RADIUS * 0.7f, context));
        prnIdPaint.setAntiAlias(true);

        mSatelliteFillPaint = new Paint();
        mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.red));
        mSatelliteFillPaint.setStyle(Paint.Style.FILL);
        mSatelliteFillPaint.setAntiAlias(true);
    }

    @Deprecated
    public void setSats(GpsStatus status) {
        Iterator<GpsSatellite> satellites = status.getSatellites().iterator();

        if (mSnrCn0s == null) {
            int length = status.getMaxSatellites();
            mSnrCn0s = new float[length];
            mElevs = new float[length];
            mAzims = new float[length];
            mPrns = new int[length];
            mHasEphemeris = new boolean[length];
            mHasAlmanac = new boolean[length];
            mUsedInFix = new boolean[length];
            // Constellation type isn't used, but instantiate it to avoid NPE in legacy devices
            mConstellationType = new int[length];
        }

        mSvCount = 0;
        int svInViewCount = 0;
        int svUsedCount = 0;
        float snrInViewSum = 0.0f;
        float snrUsedSum = 0.0f;
        mSnrCn0InViewAvg = 0.0f;
        mSnrCn0UsedAvg = 0.0f;
        while (satellites.hasNext()) {
            GpsSatellite satellite = satellites.next();
            mSnrCn0s[mSvCount] = satellite.getSnr(); // Store SNR values (see #65)
            mElevs[mSvCount] = satellite.getElevation();
            mAzims[mSvCount] = satellite.getAzimuth();
            mPrns[mSvCount] = satellite.getPrn();
            mHasEphemeris[mSvCount] = satellite.hasEphemeris();
            mHasAlmanac[mSvCount] = satellite.hasAlmanac();
            mUsedInFix[mSvCount] = satellite.usedInFix();
            // If satellite is in view, add signal to calculate avg
            if (satellite.getSnr() != 0.0f) {
                svInViewCount++;
                snrInViewSum = snrInViewSum + satellite.getSnr();
            }
            if (satellite.usedInFix()) {
                svUsedCount++;
                snrUsedSum = snrUsedSum + satellite.getSnr();
            }
            mSvCount++;
        }

        if (svInViewCount > 0) {
            mSnrCn0InViewAvg = snrInViewSum / svInViewCount;
        }
        if (svUsedCount > 0) {
            mSnrCn0UsedAvg = snrUsedSum / svUsedCount;
        }


        invalidate();
    }

    private void drawLine(Canvas c, float x1, float y1, float x2, float y2) {

        double angle = Math.toRadians(-mOrientation);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float centerX = (x1 + x2) / 2.0f;
        float centerY = (y1 + y2) / 2.0f;
        x1 -= centerX;
        y1 = centerY - y1;
        x2 -= centerX;
        y2 = centerY - y2;

        float X1 = cos * x1 + sin * y1 + centerX;
        float Y1 = -(-sin * x1 + cos * y1) + centerY;
        float X2 = cos * x2 + sin * y2 + centerX;
        float Y2 = -(-sin * x2 + cos * y2) + centerY;

        c.drawLine(X1, Y1, X2, Y2, gridP);
    }

    private void drawHorizon(Canvas canvas){
        float radius = minScreen/2;

        drawLine(canvas, 0, radius, 2 * radius, radius);
        drawLine(canvas, radius, 0, radius, 2 * radius);

        canvas.drawCircle(radius,radius,minScreen/3,gridP);
        canvas.drawCircle(radius,radius,elevToRadius(30.0f),gridP);
        canvas.drawCircle(radius,radius,elevToRadius(0.0f),gridP);
        canvas.drawCircle(radius,radius,radius,horizonP);
    }

    private void drawSatellite(Canvas canvas, int prn, float elev, float azim, float snr){
        double radius = elevToRadius(elev);
        azim -= mOrientation;
        double angle = (float) Math.toRadians(azim);

        final double PRN_X_SCALE = 1.4;
        final double PRN_Y_SCALE = 3.8;

        float x = (float) (minScreen/2 + radius * Math.sin(angle));
        float y = (float) (minScreen/2 - radius * Math.cos(angle));

        if(snr <= 10) mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.red));
        else if(snr <= 20) mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.orange));
        else if(snr <= 30) mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.yellow));
        else if(snr <= 50) mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.cyan));
        else mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.green));
        Paint fillPaint;
        fillPaint = getSatellitePaint(mSatelliteFillPaint);
        canvas.drawCircle(x, y, SAT_RADIUS, fillPaint);

        canvas.drawText(String.valueOf(prn), x - (int) (SAT_RADIUS * PRN_X_SCALE),
                y + (int) (SAT_RADIUS * PRN_Y_SCALE), prnIdPaint);
    }

    private Paint getSatellitePaint(Paint base) {
        Paint newPaint;
        newPaint = new Paint(base);
        //newPaint.setColor(getSatelliteColor(snrCn0));
        return newPaint;
    }

    private float elevToRadius(float elev){
        return minScreen/2 * (1.0f - elev / 90.0f);
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        minScreen = (width < height) ? width : height;

        drawHorizon(canvas);

        if (mElevs != null) {
            int numSats = mSvCount;
            for (int i = 0; i < numSats; i++) {
                if (mElevs[i] != 0.0f || mAzims[i] != 0.0f) {
                    drawSatellite(canvas, mPrns[i], mElevs[i], mAzims[i], mSnrCn0s[i]);
                }
            }
        }
    }

    //chuyển dp sang pixels
    public static int convertDpToPixels(float dp, Context context) {
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
        return px;
    }


    //tính toán, ước lượng kích thước cho View
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int sWidth = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(sWidth, sWidth);
    }




    @Override
    public void onOrientationChanged(double orientation, double tilt) {
        mOrientation = orientation;
        invalidate();
    }



    @Override
    public void onGpsStatusChanged(int event) {

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
}
