package com.example.gpstest2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Iterator;

import static java.lang.Math.min;
import static java.lang.Math.sqrt;

public class SkyPlotView extends View implements LocationListener, GpsStatus.Listener, GpsTestListener {

    private static int height, width;
    public static final float MIN_VALUE_SNR = 0.0f;
    public static final float MAX_VALUE_SNR = 30.0f;

    private double mOrientation = 0.0;

    private float mSnrThresholds[];

    private int mSnrColors[];

    private static int SAT_RADIUS, S_RADIUS;

    int minScreen, countSl;

    ArrayList<GpsSatellite> arrSl;

    Context mContext;
    WindowManager windowManager;

    private Paint gridP, horizonP, prnIdPaint, mSatelliteFillPaint,
    mSatelliteStrokePaint, mNorthPaint, mNorthFillPaint, mDegPaint;

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
        SAT_RADIUS = convertDpToPixels(5, context);
        S_RADIUS = convertDpToPixels(8, context);

        gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridP.setColor(Color.BLUE);
        gridP.setStyle(Paint.Style.STROKE);
        gridP.setPathEffect(new DashPathEffect(new float[]{2,4},50));

        horizonP = new Paint(Paint.ANTI_ALIAS_FLAG);
        horizonP.setColor(Color.RED);
        horizonP.setStyle(Paint.Style.STROKE);
        horizonP.setStrokeWidth(3.0f);


        prnIdPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        prnIdPaint.setColor(Color.BLACK);
        prnIdPaint
                .setTextSize(convertDpToPixels(SAT_RADIUS * 0.35f, context));

        mSatelliteFillPaint = new Paint();
        mSatelliteFillPaint.setColor(ContextCompat.getColor(mContext, R.color.red));
        mSatelliteFillPaint.setStyle(Paint.Style.FILL);
        mSatelliteFillPaint.setAntiAlias(true);

        mSatelliteStrokePaint = new Paint();
        mSatelliteStrokePaint.setColor(Color.BLACK);
        mSatelliteStrokePaint.setStyle(Paint.Style.STROKE);
        mSatelliteStrokePaint.setStrokeWidth(2.0f);
        mSatelliteStrokePaint.setAntiAlias(true);

        mSnrThresholds = new float[]{MIN_VALUE_SNR, 10.0f, 20.0f, MAX_VALUE_SNR};
        mSnrColors = new int[]{ContextCompat.getColor(mContext, R.color.gray),
                ContextCompat.getColor(mContext, R.color.red),
                ContextCompat.getColor(mContext, R.color.yellow),
                ContextCompat.getColor(mContext, R.color.green)};

        mDegPaint = new Paint();
        mDegPaint.setColor(Color.BLACK);
        mDegPaint
                .setTextSize(convertDpToPixels(SAT_RADIUS * 0.3f, context));
        mDegPaint.setTextAlign(Paint.Align.CENTER);

        mNorthPaint = new Paint();
        mNorthPaint.setColor(Color.BLACK);
        mNorthPaint.setStyle(Paint.Style.STROKE);
        mNorthPaint.setStrokeWidth(4.0f);
        mNorthPaint.setAntiAlias(true);

        mNorthFillPaint = new Paint();
        mNorthFillPaint.setColor(Color.GRAY);
        mNorthFillPaint.setStyle(Paint.Style.FILL);
        mNorthFillPaint.setStrokeWidth(4.0f);
        mNorthFillPaint.setAntiAlias(true);
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

    private void drawLine(Canvas c, float x1, float y1, float x2, float y2, String sDeg1, String sDeg2, int deg1, int deg2) {

        if(x1 < minScreen/2 && y1 > minScreen/2){
            x1 += 10;
            y1 -= 10;
            x2 -= 10;
            y2 += 10;
        }
        x1 += 10;
        y1 += 10;
        x2 -= 10;
        y2 -= 10;
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

        c.save();
        c.rotate((float) (deg1 - mOrientation), X1 ,
                Y1 );
        c.drawText(sDeg1, X1, Y1 , mDegPaint);
        c.restore();

        c.save();
        c.rotate((float) (deg2 - mOrientation), X2 ,
                Y2 );
        c.drawText(sDeg2, X2, Y2, mDegPaint);
        c.restore();


    }

    private void drawHorizon(Canvas canvas){
        float radius = minScreen/2  ;

        drawLine(canvas, 0 , radius, 2 * radius, radius,
                "270", "90", -90, 90);
        drawLine(canvas, radius, 0, radius, 2 * radius,
                "0", "180", 0, 180);

        drawLine(canvas, (float) (radius * (1 - sqrt(3.0)/2.0) ), radius/2  , (float) (radius * (1 + sqrt(3.0)/2.0)), (float) (1.5 * radius),
                "300", "120", -60, 120);
        drawLine(canvas, (float) (radius * (1 - sqrt(3.0)/2.0)), (float) (1.5 * radius), (float) (radius * (1 + sqrt(3.0)/2.0)), radius/2,
                "240", "60", -120, 60);

        drawLine(canvas, radius/2, (float) (radius * (1 - sqrt(3.0)/2.0)), (float) (1.5 * radius), (float) (radius * (1 + sqrt(3.0)/2.0)),
                "330", "150", -30, 150);
        drawLine(canvas, radius/2, (float) (radius * (1 + sqrt(3.0)/2.0)), (float) (1.5 * radius), (float) (radius * (1 - sqrt(3.0)/2.0)),
                "210", "30", -150, 30);

        //canvas.drawCircle(radius,radius,minScreen/3,gridP);
        canvas.drawCircle(radius,radius,elevToRadius(30.0f),gridP);
        canvas.drawCircle(radius,radius,elevToRadius(60.0f),gridP);
        canvas.drawCircle(radius,radius,elevToRadius(0.0f),gridP);
        canvas.drawCircle(radius,radius,radius ,horizonP);
    }

    private void drawNorthIndicator(Canvas c) {
        float radius = minScreen / 2 +10 ;
        double angle = Math.toRadians(-mOrientation);
        final float ARROW_HEIGHT_SCALE = 0.03f;
        final float ARROW_WIDTH_SCALE = 0.05f;

        float x1, y1;  // Tip of arrow
        x1 = radius ;
        y1 = elevToRadius(90.0f);

        float x2, y2;
        x2 = x1 + radius * ARROW_HEIGHT_SCALE;
        y2 = y1 + radius * ARROW_WIDTH_SCALE;

        float x3, y3;
        x3 = x1 - radius * ARROW_HEIGHT_SCALE;
        y3 = y1 + radius * ARROW_WIDTH_SCALE;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        // Rotate arrow around center point
        Matrix matrix = new Matrix();
        matrix.postRotate((float) -mOrientation, radius, radius);
        path.transform(matrix);

        c.drawPath(path, mNorthPaint);
        c.drawPath(path, mNorthFillPaint);
    }

    private void drawSatellite(Canvas canvas, int prn, float elev, float azim,
                               float snrCn0){
        double radius = elevToRadius(elev);
        azim -= mOrientation;
        double angle = (float) Math.toRadians(azim);

        final double PRN_X_SCALE = 1;
        final double PRN_Y_SCALE = 2.5;

        float x = (float) (minScreen/2 + radius * Math.sin(angle));
        float y = (float) (minScreen/2 - radius * Math.cos(angle));

        Paint fillPaint;
        fillPaint = getSatellitePaint(mSatelliteFillPaint, snrCn0);
        //canvas.drawCircle(x, y, SAT_RADIUS, fillPaint);

        Paint strokePaint;

            strokePaint = mSatelliteStrokePaint;


        if (prn >= 1 && prn <= 32) { //GPS
            canvas.drawCircle(x, y, SAT_RADIUS, fillPaint);
            canvas.drawCircle(x, y, SAT_RADIUS, strokePaint);
        } else if (prn == 33) {

        } else if (prn == 39) {

        } else if (prn >= 40 && prn <= 41) {

        } else if (prn == 46) {

        } else if (prn == 48) {

        } else if (prn == 49) {

        } else if (prn == 51) {

        } else if (prn >= 65 && prn <= 96) {
            canvas.drawRect(x - SAT_RADIUS, y - SAT_RADIUS, x + SAT_RADIUS, y + SAT_RADIUS,
                    fillPaint);
            canvas.drawRect(x - SAT_RADIUS, y - SAT_RADIUS, x + SAT_RADIUS, y + SAT_RADIUS,
                    strokePaint);
        } else if (prn >= 193 && prn <= 200) {
            drawHexagon(canvas, x, y, fillPaint, strokePaint);
        } else if (prn >= 201 && prn <= 235) {
            drawPentagon(canvas, x, y, fillPaint, strokePaint);
        } else if (prn >= 301 && prn <= 330) {
            drawTriangle(canvas, x, y, fillPaint, strokePaint);
        } else {

        }



        canvas.drawText(String.valueOf(prn), x - (int) (SAT_RADIUS * PRN_X_SCALE),
                y + (int) (SAT_RADIUS * PRN_Y_SCALE), prnIdPaint);
    }

    private Paint getSatellitePaint(Paint base, float snrCn0) {
        Paint newPaint;
        newPaint = new Paint(base);
        newPaint.setColor(getSatelliteColor(snrCn0));
        return newPaint;
    }

    private void drawTriangle(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        float x1, y1;  // Top
        x1 = x;
        y1 = y - SAT_RADIUS;

        float x2, y2; // Lower left
        x2 = x - SAT_RADIUS;
        y2 = y + SAT_RADIUS;

        float x3, y3; // Lower right
        x3 = x + SAT_RADIUS;
        y3 = y + SAT_RADIUS;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawDiamond(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        Path path = new Path();
        path.moveTo(x, y - SAT_RADIUS);
        path.lineTo(x - SAT_RADIUS * 1.5f, y);
        path.lineTo(x, y + SAT_RADIUS);
        path.lineTo(x + SAT_RADIUS * 1.5f, y);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawPentagon(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        Path path = new Path();
        path.moveTo(x, y - SAT_RADIUS);
        path.lineTo(x - SAT_RADIUS, y - (SAT_RADIUS / 3));
        path.lineTo(x - 2 * (SAT_RADIUS / 3), y + SAT_RADIUS);
        path.lineTo(x + 2 * (SAT_RADIUS / 3), y + SAT_RADIUS);
        path.lineTo(x + SAT_RADIUS, y - (SAT_RADIUS / 3));
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }

    private void drawHexagon(Canvas c, float x, float y, Paint fillPaint, Paint strokePaint) {
        final float MULTIPLIER = 0.6f;
        final float SIDE_MULTIPLIER = 1.4f;
        Path path = new Path();
        // Top-left
        path.moveTo(x - SAT_RADIUS * MULTIPLIER, y - SAT_RADIUS);
        // Left
        path.lineTo(x - SAT_RADIUS * SIDE_MULTIPLIER, y);
        // Bottom
        path.lineTo(x - SAT_RADIUS * MULTIPLIER, y + SAT_RADIUS);
        path.lineTo(x + SAT_RADIUS * MULTIPLIER, y + SAT_RADIUS);
        // Right
        path.lineTo(x + SAT_RADIUS * SIDE_MULTIPLIER, y);
        // Top-right
        path.lineTo(x + SAT_RADIUS * MULTIPLIER, y - SAT_RADIUS);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, strokePaint);
    }



    /**
     * Gets the paint color for a satellite based on provided SNR or C/N0 and the thresholds defined in this class
     *
     * @param snrCn0 the SNR to use (if using legacy GpsStatus) or the C/N0 to use (if using is
     *               GnssStatus) to generate the satellite color based on signal quality
     * @return the paint color for a satellite based on provided SNR or C/N0
     */
    public synchronized int getSatelliteColor(float snrCn0) {
        int numSteps;
        final float thresholds[];
        final int colors[];


            // Use legacy SNR ranges/colors for Android versions less than Android 7.0 or if user selects legacy API (see #76)
            numSteps = mSnrThresholds.length;
            thresholds = mSnrThresholds;
            colors = mSnrColors;


        if (snrCn0 <= thresholds[0]) {
            return colors[0];
        }

        if (snrCn0 >= thresholds[numSteps - 1]) {
            return colors[numSteps - 1];
        }

        for (int i = 0; i < numSteps - 1; i++) {
            float threshold = thresholds[i];
            float nextThreshold = thresholds[i + 1];
            if (snrCn0 >= threshold && snrCn0 <= nextThreshold) {
                int c1, r1, g1, b1, c2, r2, g2, b2, c3, r3, g3, b3;
                float f;

                c1 = colors[i];
                r1 = Color.red(c1);
                g1 = Color.green(c1);
                b1 = Color.blue(c1);

                c2 = colors[i + 1];
                r2 = Color.red(c2);
                g2 = Color.green(c2);
                b2 = Color.blue(c2);

                f = (snrCn0 - threshold) / (nextThreshold - threshold);

                r3 = (int) (r2 * f + r1 * (1.0f - f));
                g3 = (int) (g2 * f + g1 * (1.0f - f));
                b3 = (int) (b2 * f + b1 * (1.0f - f));
                c3 = Color.rgb(r3, g3, b3);

                return c3;
            }
        }
        return Color.MAGENTA;
    }

    private float elevToRadius(float elev){
        return (minScreen/2 - SAT_RADIUS - 5) * (1.0f - elev / 90.0f);
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        minScreen = (width < height) ? width : height;

        drawHorizon(canvas);

        drawNorthIndicator(canvas);

        if (mElevs != null) {
            int numSats = mSvCount;
            for (int i = 0; i < numSats; i++) {
                if (mElevs[i] != 0.0f || mAzims[i] != 0.0f) {
                    drawSatellite(canvas, mPrns[i],
                            mElevs[i], mAzims[i],
                            mSnrCn0s[i]);
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
    public void onGpsStatusChanged(int event, GpsStatus status) {

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
