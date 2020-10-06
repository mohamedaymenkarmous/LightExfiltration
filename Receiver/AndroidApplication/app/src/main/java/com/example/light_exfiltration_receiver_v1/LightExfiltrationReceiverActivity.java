package com.example.light_exfiltration_receiver_v1;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class LightExfiltrationReceiverActivity extends AppCompatActivity {

    private static final String TAG = "Color";

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mCaptureState = STATE_PREVIEW;
    private TextureView mTextureView;
    int[] pixel={0,0,0};
    int[] basePixel={};
    float[] hsv={};
    float[] statedHsv={};
    //float[] oldHsv={};//older than hsv
    float[] baseHsv={};
    long time= System.currentTimeMillis();
    int minSaturation=600;//6%
    int minHue=1000;//10°
    int bitsNumber=1;
    boolean advancedMode=true;
    String newdata="";
    String finalDecodedNewData="";
    CameraManager cameraManager;
    int sensorSensitivity=-1;
    private SeekBar sBarSensorSensitivity;
    private TextView tViewSensorSensitivity;
    int frameDuration=-1;
    int zoom=10;
    private SeekBar sBarZoom;
    private TextView tViewZoom;
    private Spinner spinnerDecodingMode;
    int exposureTime=-1;
    private SeekBar sBarExposureTime;
    private TextView tViewExposureTime;
    int radius=10;
    private SeekBar sBarRadius;
    private TextView tViewRadius;
    private SeekBar sBarMinSaturation;
    private TextView tViewMinSaturation;
    private SeekBar sBarMinHue;
    private TextView tViewMinHue;
    private TextView tViewColor;
    private TextView tViewStatus;
    private TextView tViewTimeFrequency;
    private TextView tViewColorsNumber;
    private TextView tViewReceived;
    private TextView tViewBits;
    private TextView tViewDecoded;
    private ImageView mImageView;
    private String status="Ready";
    ArrayList<float[]> colorRanges=new ArrayList<float[]>();
    boolean reconDone=false,reconDoneDefault=false;
    boolean colorChanging=false;
    long timer=0;
    long previousTime=0;
    long currentTime=0;
    long nextResumeTime=0;
    //boolean firstManchesterPart=true;
    //boolean breakConstantColorState=false;
    Rect zoomRect;
    public static final String mypreference = "LightExiltrationReceiverConfig";
    SharedPreferences sharedpreferences;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        public ArrayList<Integer> extractBytesZone(Bitmap bmp,int m1,int m2){
            int[] result;
            ArrayList<Integer> mylist = new ArrayList<Integer>();
            for (int x = 0; x < bmp.getWidth(); x++)
            {
                for (int y = 0; y < bmp.getHeight(); y++)
                {
                    double dx = x - m1;
                    double dy = y - m2;
                    double distanceSquared = dx * dx + dy * dy;

                    if (distanceSquared <= Math.pow(radius,2))
                    {
                        //Log.d(TAG, String.valueOf(Color.green(bmp.getPixel(x,y))));
                        mylist.add(bmp.getPixel(x,y));
                    }
                }
            }
            return mylist;
        }
        private int[] avgRgb(ArrayList<Integer> bytes) {
            int c;
            int avgR=0,avgG=0,avgB=0;
            int newAvgR=0,newAvgG=0,newAvgB=0;
            int t = bytes.size();
            //Log.d(TAG+"size", String.valueOf(t));
            float[] hsv=new float[3];
            int n=0;
            for (int i = 0; i < t; i++) {
                c = bytes.get(i);
                newAvgR = ((c & 0x00ff0000) >> 16);
                newAvgG = ((c & 0x0000ff00) >> 8);
                newAvgB = ((c & 0x000000ff));
                Color.RGBToHSV(newAvgR, newAvgG, newAvgB, hsv);
                if(hsv[2]>=0.1 && hsv[1]>=0.1 && hsv[2]<=0.9 && hsv[1]<=0.9){
                    avgR +=newAvgR;
                    avgG +=newAvgG;
                    avgB +=newAvgB;
                    n++;
                }
            }
            if(n>0) {
                avgR /= n;
                avgG /= n;
                avgB /= n;
            }else{
                avgR=0;
                avgG=0;
                avgB=0;
            }
            //Log.d(TAG,"size "+String.valueOf(avgR)+" "+String.valueOf(avgG)+" "+String.valueOf(avgB)+" "+n);
            return new int[]{avgR, avgG, avgB};
        }

        public int getIntFromColor(int Red, int Green, int Blue){
            Red = (Red << 16) & 0x00FF0000; //Shift red 16-bits and mask out other stuff
            Green = (Green << 8) & 0x0000FF00; //Shift Green 8-bits and mask out other stuff
            Blue = Blue & 0x000000FF; //Mask out anything not blue.

            return 0xFF000000 | Red | Green | Blue; //0xFF000000 for 100% Alpha. Bitwise OR everything together.
        }

        /*excludeFirstIndex : if true: skip capturing a color similar to the color registered in the index of colorRanges vector (used only when the record button is clicked to avoid capturing the initial color another time)*/
        public float[] calcMinHSDistance(float[] newHsv,boolean excludeFirstIndex){
            float[] minHSDistance= new float[]{-1, -1, 1, -1};
            for(int i=0;i<colorRanges.size();i++){
                Log.d(TAG,"colorRanges("+i+"): "+colorRanges.get(i)[0]+","+colorRanges.get(i)[1]+","+colorRanges.get(i)[2]);
                Log.d(TAG,"newHSV:"+newHsv[0]+","+newHsv[1]+","+newHsv[2]);
                // Example 1: (newHsv0=13 & colorRanges=5): colorRanges<newHsv0: 13-5=8
                boolean b1 = Math.abs(newHsv[0] - colorRanges.get(i)[0]) < 180;
                // Example 2 (newHsv0=359 & colorRanges=3): colorRanges<<newHsv0: 3-(359-360)=3-(-1)=4
                boolean b2 = Math.abs(newHsv[0] - colorRanges.get(i)[0]) >= 180 && newHsv[0]>colorRanges.get(i)[0];
                // Example 3 (newHsv0=3 & colorRanges=359): colorRanges>>newHsv0: abs((359-360)-3)=abs(-1-3)=4
                boolean b3 = Math.abs(newHsv[0] - colorRanges.get(i)[0]) >= 180 && newHsv[0]<colorRanges.get(i)[0];
                float hDistance=0;
                float sDistance=0;
                if(b1){
                    hDistance=Math.abs(newHsv[0] - colorRanges.get(i)[0]) % 360;
                }else if(b2){
                    hDistance=Math.abs(colorRanges.get(i)[0] - (newHsv[0] - 360)) % 360;
                }else if(b3){
                    hDistance=Math.abs((colorRanges.get(i)[0] - 360) - newHsv[0]) % 360;
                }
                sDistance=Math.abs(newHsv[1] - colorRanges.get(i)[1]);
                if(hDistance<((float)minHue/100)){
                    if(sDistance<(((float)minSaturation/100)/100)){
                        if(i==0 && excludeFirstIndex==true){
                            minHSDistance= new float[]{0, 0, 0, i};
                        }else{
                            minHSDistance= new float[]{0, 0, 1, i};
                        }
                        break;
                    }else if(sDistance>=(((float)minSaturation/100)/100)){
                        minHSDistance= new float[]{hDistance, sDistance, 1, i};
                    }
                }else{
                    if(minHSDistance[0]<0) {
                        minHSDistance = new float[]{hDistance, sDistance, 1, i};
                    }else{
                        if(hDistance<minHSDistance[0])minHSDistance = new float[]{hDistance, sDistance, 1, i};
                    }
                }
                Log.d(TAG, String.valueOf(hDistance)+","+sDistance);
                Log.d(TAG,"minHueDistance("+i+"): "+minHSDistance[0]+","+minHSDistance[1]+","+minHSDistance[2]+","+minHSDistance[3]);
            }
            return minHSDistance;
        }

        public float[] calcMinSDistance(float[] newHsv,boolean excludeFirstIndex){
            float[] minSDistance= new float[]{-1, 1, -1};
            for(int i=0;i<colorRanges.size();i++){
                Log.d(TAG,"colorRanges("+i+"): "+colorRanges.get(i)[0]+","+colorRanges.get(i)[1]);
                Log.d(TAG,"newHSV:"+newHsv[0]+","+newHsv[1]+","+newHsv[2]);
                float sDistance=0;
                sDistance=Math.abs(newHsv[1] - colorRanges.get(i)[1]);
                if(sDistance<(((float)minSaturation/100)/100) && colorRanges.size()>1){
                    if(i==0 && excludeFirstIndex==true) {
                        minSDistance = new float[]{0, 0, i};
                    }else{
                        minSDistance = new float[]{0, 1, i};
                    }
                    break;
                }else if(sDistance>=(((float)minSaturation/100)/100)){
                    if(minSDistance[0]<0) {
                        minSDistance= new float[]{sDistance, 1, i};
                    }else{
                        if(sDistance<minSDistance[0])minSDistance= new float[]{sDistance, 1, i};
                    }
                }
                Log.d(TAG, String.valueOf(sDistance));
                Log.d(TAG,"calcMinSDistance("+i+"): "+minSDistance[0]+","+minSDistance[1]+","+minSDistance[2]);
            }
            return minSDistance;
        }

        /*public void updateTimer(){
            timer=currentTime-previousTime;
            previousTime=currentTime;
            Log.d(TAG,"Timer:"+timer);
            currentTime=System.currentTimeMillis();
        }*/

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            //mCaptureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,1000);
            if(time<System.currentTimeMillis()) {
                Bitmap bmp= mTextureView.getBitmap();
                ArrayList<Integer> bytesZone=extractBytesZone(bmp,bmp.getWidth()/2,bmp.getHeight()/2);
                //Log.d(TAG,bmp.getWidth()/2+" , "+bmp.getHeight()/2+" ,r="+radius+","+bytesZone.size());
                //int tmpPixel=bmp.getPixel(bmp.getWidth()/2,bmp.getHeight()/2);
                int[] newPixel=new int[3];
                float[] newHsv=new float[3];
                //newPixel[0]=Color.red(tmpPixel);
                //newPixel[1]=Color.green(tmpPixel);
                //newPixel[2]=Color.blue(tmpPixel);
                newPixel=avgRgb(bytesZone);
                Color.RGBToHSV(newPixel[0], newPixel[1], newPixel[2], newHsv);
                tViewColor.setText("Color "+String.format("%.2f",newHsv[0])+" , "+String.format("%.2f",newHsv[1]));
                tViewColor.setTextColor(getIntFromColor(newPixel[0],newPixel[1],newPixel[2]));
                tViewStatus.setText("Status: "+status);
                if(mIsRecording || mIsTimelapse) {
                    if (basePixel.length == 0) {
                        Log.d(TAG, "Initiating baseHsv");
                        basePixel = newPixel;
                        baseHsv = newHsv;
                        statedHsv = newHsv;
                        Log.d(TAG, "baseHsv: "+String.valueOf(newHsv[0])+" "+String.valueOf(newHsv[1]));
                    }
                    if(hsv.length != 0)Log.d(TAG, "hsv: "+String.valueOf(hsv[0])+" "+String.valueOf(hsv[1]));
                    if(statedHsv.length != 0)Log.d(TAG, "statedHsv: "+String.valueOf(statedHsv[0])+" "+String.valueOf(statedHsv[1]));
                    Log.d(TAG, "newHsv: "+String.valueOf(newHsv[0])+" "+String.valueOf(newHsv[1]));
                    // Example 1: (newHsv0=13 & baseHsv0=5): baseHsv0<newHsv0: 13-5=8
                    boolean b1 = Math.abs(newHsv[0] - baseHsv[0]) < 180 && Math.abs(newHsv[0] - baseHsv[0]) % 360 < ((float)minHue/100);
                    // Example 2 (newHsv0=359 & baseHsv0=3): baseHsv0<<newHsv0: 3-(359-360)=3-(-1)=4
                    boolean b2 = Math.abs(newHsv[0] - baseHsv[0]) >= 180 && baseHsv[0]<newHsv[0] && Math.abs(baseHsv[0] - (newHsv[0] - 360)) % 360 < ((float)minHue/100);
                    // Example 3 (newHsv0=3 & baseHsv0=359): baseHsv0>>newHsv0: (359-360)-3=abs(-1-3)=4
                    boolean b3 = Math.abs(newHsv[0] - baseHsv[0]) >= 180 && baseHsv[0]>newHsv[0] && Math.abs((baseHsv[0] - 360) - newHsv[0]) % 360 < ((float)minHue/100);
                    Log.d(TAG, "newHsv[0] - baseHsv[0] <? "+((float)minHue/100)+" : "+b1+" & "+b2+" & "+b3);
                    // Example 1: (newHsv0=13 & baseHsv0=5): baseHsv0<newHsv0: 13-5=8
                    boolean b4 = statedHsv.length > 0 && Math.abs(newHsv[0] - statedHsv[0]) < 180 && Math.abs(newHsv[0] - statedHsv[0]) % 360 >= ((float)minHue/100);
                    // Example 2 (newHsv0=359 & baseHsv0=3): baseHsv0<<newHsv0: 3-(359-360)=3-(-1)=4
                    boolean b5 = statedHsv.length > 0 && Math.abs(newHsv[0] - statedHsv[0]) >= 180 && statedHsv[0]<newHsv[0] && Math.abs(statedHsv[0] - (newHsv[0] - 360)) % 360 >= ((float)minHue/100);
                    // Example 3 (newHsv0=3 & baseHsv0=359): baseHsv0>>newHsv0: (359-360)-3=abs(-1-3)=4
                    boolean b6 = statedHsv.length > 0 && Math.abs(newHsv[0] - statedHsv[0]) >= 180 && statedHsv[0]>newHsv[0] && Math.abs((statedHsv[0] - 360) - newHsv[0]) % 360 >= ((float)minHue/100);
                    Log.d(TAG, "newHsv[0] - statedHsv[0] >=? "+((float)minHue/100)+" : "+b4+" & "+b5+" & "+b6);
                    // Example 1: (newHsv0=13 & baseHsv0=5): baseHsv0<newHsv0: 13-5=8
                    boolean b7 = hsv.length > 0 && Math.abs(newHsv[0] - hsv[0]) < 180 && Math.abs(newHsv[0] - hsv[0]) % 360 < ((float)minHue/100);
                    // Example 2 (newHsv0=359 & baseHsv0=3): baseHsv0<<newHsv0: 3-(359-360)=3-(-1)=4
                    boolean b8 = hsv.length > 0 && Math.abs(newHsv[0] - hsv[0]) >= 180 && hsv[0]<newHsv[0] && Math.abs(hsv[0] - (newHsv[0] - 360)) % 360 < ((float)minHue/100);
                    // Example 3 (newHsv0=3 & baseHsv0=359): baseHsv0>>newHsv0: (359-360)-3=abs(-1-3)=4
                    boolean b9 = hsv.length > 0 && Math.abs(newHsv[0] - hsv[0]) >= 180 && hsv[0]>newHsv[0] && Math.abs((hsv[0] - 360) - newHsv[0]) % 360 < ((float)minHue/100);
                    Log.d(TAG, "newHsv[0] - hsv[0] >=? "+((float)minHue/100)+" : "+b7+" & "+b8+" & "+b9);
                    if(hsv.length > 0)Log.d(TAG,(Math.abs(newHsv[1] - hsv[1]) > ((float)minSaturation/100) / 100)+" "+ b7 +" "+ b8 +" "+ b9);
                    if(statedHsv.length >0 && (b1 || b2 || b3) && Math.abs(newHsv[1] - statedHsv[1]) > ((float)minSaturation/100) / 100 && Math.abs(newHsv[1] - baseHsv[1]) < ((float)minSaturation/100) / 100){
                        status="Ready (recording automatically stopped)";
                        Log.d(TAG,"Status: "+status);
                        tViewStatus.setText("Status: "+status);
                        mRecordImageButton.performClick();return;
                    }
                    if (hsv.length == 0 || ((Math.abs(newHsv[1] - statedHsv[1]) > ((float)minSaturation/100) / 100 || b4 || b5 || b6) && colorChanging==false) || (advancedMode==false && timer>0 && nextResumeTime<=System.currentTimeMillis())) {
                        Log.d(TAG, String.valueOf("Empty HSV:"+(hsv.length == 0)));
                        if(hsv.length > 0)Log.d(TAG, "Low saturation (newHsv[1] - statedHsv[1] <"+(((float)minSaturation/100) / 100)+")"+String.valueOf(Math.abs(newHsv[1] - statedHsv[1]) > ((float)minSaturation/100) / 100));
                        Log.d(TAG, "Expired time: "+String.valueOf((advancedMode==false && timer>0 && nextResumeTime<=System.currentTimeMillis())));
                        // when the first S variation starts in the reconnaissance phase we mark the previous time
                        //if((Math.abs(newHsv[1] - hsv[1]) > minSaturation / 100) && (previousTime==0))previousTime=System.currentTimeMillis();
                        /*else if(Math.abs(newHsv[1] - hsv[1]) > minSaturation / 100){
                            previousTime=currentTime;
                            currentTime=System.currentTimeMillis();
                        }*/
                        //long tempCurrentTime=System.currentTimeMillis();
                        if(hsv.length >0 && colorChanging==false){
                            Log.d(TAG, "ColorChanging : true");
                            //oldHsv=hsv;
                            colorChanging=true;
                        }
                        if(advancedMode==false && timer>0 && nextResumeTime<=System.currentTimeMillis()){
                            currentTime=System.currentTimeMillis();
                            nextResumeTime= (long) (currentTime+(timer*1.25));
                        }
                        if(advancedMode==false && hsv.length >0 && (Math.abs(newHsv[1] - statedHsv[1]) > ((float)minSaturation/100) / 100) && reconDone==true){
                            currentTime=System.currentTimeMillis();
                            nextResumeTime= (long) (currentTime+(timer*1.25));
                            previousTime=currentTime;
                        }
                        //if(oldHsv.length>0 && ((oldHsv[1]>hsv[1] && oldHsv[1]-hsv[1]<0.05)||(oldHsv[1]<hsv[1] && hsv[1]-oldHsv[1]<0.05))) {
                        //}
                    }else
                     //Stable color means the difference between the previous and the current colors is less than the minSaturation
                     if(colorChanging==true && (Math.abs(newHsv[1] - hsv[1]) < ((float)minSaturation/100) / 100 && (b7 || b8 || b9))){
                            Log.d(TAG,"Stable color");
                            Log.d(TAG, "reconDone: " + reconDone);
                            statedHsv=newHsv;
                            if (reconDone == false) {
                                //updateTimer();
                                if (colorRanges.size() == 0) {
                                    Log.d(TAG, "STARTTT");
                                    status="Recording - Colors reconnaissance";
                                    tViewStatus.setText("Status: "+status);
                                    colorRanges.add(newHsv);
                                    tViewColorsNumber.setText("Colors Number: "+colorRanges.size());
                                    previousTime=System.currentTimeMillis();
                                } else if (colorRanges.size() > 0) {
                                    float reconStatus;
                                    boolean minDistanceValid=false;
                                    if(advancedMode==true){
                                        //ciruclar colors (HS)
                                        float[] minHSDistance=calcMinHSDistance(newHsv, true);
                                        float minHValue=minHSDistance[0];
                                        float minSValue=minHSDistance[1];
                                        reconStatus=minHSDistance[2];
                                        Log.d(TAG,minHValue+" & "+minSValue+" "+(minHValue<((float)minHue/100))+" "+(minSValue<(((float)minSaturation/100)/100)));
                                        if(minHValue<((float)minHue/100) && minSValue<(((float)minSaturation/100)/100))minDistanceValid=true;
                                    }else{
                                        //Manchester coding (S)
                                        float[] minHDistance=calcMinSDistance(newHsv, true);
                                        float minSValue=minHDistance[0];
                                        reconStatus=minHDistance[1];
                                        if(minSValue<(((float)minSaturation/100)/100))minDistanceValid=true;
                                    }
                                    if (reconStatus == 0) {
                                        reconDone = true;
                                        status = "Recording - Receiving data";
                                        tViewStatus.setText("Status: " + status);
                                        Log.d(TAG, "END OF RECON " + colorRanges.size());
                                        currentTime = System.currentTimeMillis();
                                        if (advancedMode == false)
                                            timer = (currentTime - previousTime) / 2;
                                        else timer = (currentTime - previousTime) / 4;
                                        tViewTimeFrequency.setText("Time frequency: " + timer + "ms");
                                        Log.d(TAG, "Timer:" + timer);
                                        nextResumeTime = (long) (currentTime + (timer * 1.25));
                                        previousTime = currentTime;
                                    } else if(!minDistanceValid) { // In reconnaissance, the min distance should not be close to one of the recorded colorRanges values otherwise it will be be added to colorRanges
                                        colorRanges.add(newHsv);
                                        Log.d(TAG, "Added new color in the recon " + newHsv[0] + " " + newHsv[1]);
                                        tViewColorsNumber.setText("Colors Number: " + colorRanges.size());
                                    }else Log.d(TAG, "Duplicate color that will not be added " + newHsv[0] + " " + newHsv[1]);
                                }
                            } else {
                                //detectBitsByFixedRanges(newPixel,newHsv);
                                //if(advancedMode==true)detectBitsByDetectedHSV(newPixel, newHsv);
                                //else detectManchesterBitsByDetectedHSV(newPixel, newHsv);
                                detectBitsByDetectedHSV(newPixel, newHsv);
                            }
                            colorChanging=false;
                    }
                }
                /*if(newHsv.length>0 && baseHsv.length>0 && newPixel.length>0 && hsv.length>0){
                    Log.i(TAG, String.valueOf(newPixel[0]) + "," + String.valueOf(newPixel[1]) + "," + String.valueOf(newPixel[2]) + "\t" +
                        String.valueOf(newHsv[0]) + "," + String.valueOf(newHsv[1]) + "," + String.valueOf(newHsv[2]) + " ? " +
                        String.valueOf(baseHsv[0]) + "," + String.valueOf(baseHsv[1]) + "," + String.valueOf(baseHsv[2]));
                    Log.d(TAG, "HSV - len:" + String.valueOf(hsv.length) + " ,prev:" + String.valueOf(hsv[2]) + " ,new:" + String.valueOf(newHsv[2]));
                }*/
                time = System.currentTimeMillis() + 200;
                // These lines should be commented when using detectBitsByFixedRanges()
                hsv=newHsv;
                pixel = newPixel;
            }
        }
        public void detectBitsByDetectedHSV(int[] newPixel,float[] newHsv){
            //int maxPower = (int) Math.pow(2, bitsNumber), halfMaxPower = (int) Math.pow(2, bitsNumber - 1);
            //int i = 0;
            //[any:0->0]
            Log.i(TAG, "okkC");
            float[] minHueDistance;
            float nthBit;
            //boolean concatData=false;
            int bitsNumberPerByte=1;
            if(advancedMode==true){
                minHueDistance=calcMinHSDistance(newHsv, false);
                nthBit=minHueDistance[3]/2;
                //concatData=true;
                bitsNumberPerByte=8;
            }else{
                minHueDistance=calcMinSDistance(newHsv, false);
                nthBit=minHueDistance[2];
                bitsNumberPerByte=16;
                //Log.d(TAG,"firstManchesterPart"+firstManchesterPart);
                //if(firstManchesterPart==false){
                //    concatData=true;
                //    firstManchesterPart=true;
                //}else{
                //    concatData=false;
                //    firstManchesterPart=false;
                //}
            }
            newdata = newdata + String.format("%" + bitsNumber + "s", Integer.toBinaryString((int) (nthBit))).replace(' ', '0');
            tViewReceived.setText("Manchester encoded: "+newdata);
            Log.d(TAG,"actual newdata "+newdata);
            if (newdata.length()%bitsNumberPerByte == 0) {
                String finalNewData=extractPreDecodedData();
                if(finalNewData.substring(finalNewData.length()-8).contains("?"))finalDecodedNewData=finalDecodedNewData+"?";
                else{
                    int charCode = Integer.parseInt(finalNewData.substring(finalNewData.length()-8), 2);
                    finalDecodedNewData=finalDecodedNewData+new Character((char)charCode).toString();
                    tViewBits.setText("Decoded (Bits): "+finalNewData);
                }
                tViewDecoded.setText("Decoded (ASCII): "+finalDecodedNewData);
                Log.d(TAG, "DECODED: " + finalDecodedNewData);
            }
        }

        public String extractPreDecodedData(){
            String finalNewData="";
            if(advancedMode==true)finalNewData=newdata;
            else{
                for(int i=0;i<newdata.length();i+=2){
                    Log.d(TAG,"substring "+newdata.substring(i,i+2));
                    Log.d(TAG,String.valueOf(newdata.substring(i,i+2).getClass()));
                    if(newdata.substring(i,i+2).equals("01"))finalNewData=finalNewData+"1";
                    else if(newdata.substring(i,i+2).equals("10"))finalNewData=finalNewData+"0";
                    else finalNewData=finalNewData+"?";
                }
                Log.d(TAG,"substring "+finalNewData);
            }
            return finalNewData;
        }
    };

    private void drawCircleCanvas() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                Rect activeRect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                // Initialize a new Bitmap object
                Bitmap bitmap = Bitmap.createBitmap(
                        activeRect.width(), // Width
                        activeRect.height(), // Height
                        Bitmap.Config.ARGB_8888 // Config
                );

                // Initialize a new Canvas instance
                Canvas canvas = new Canvas(bitmap);
                // Initialize a new Paint instance to draw the Circle
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.RED);
                paint.setAntiAlias(true);
                paint.setStrokeWidth(2f);
                // Finally, draw the circle on the canvas
                canvas.drawCircle(
                        canvas.getWidth() / 2, // cx
                        canvas.getHeight() / 2, // cy
                        radius, // Radius
                        paint // Paint
                );
                paint.setColor(Color.GREEN);
                canvas.drawCircle(
                        canvas.getWidth() / 2, // cx
                        canvas.getHeight() / 2, // cy
                        radius+2, // Radius
                        paint // Paint
                );
                paint.setColor(Color.BLUE);
                canvas.drawCircle(
                        canvas.getWidth() / 2, // cx
                        canvas.getHeight() / 2, // cy
                        radius+4, // Radius
                        paint // Paint
                );
                paint.setColor(Color.BLACK);
                canvas.drawCircle(
                        canvas.getWidth() / 2, // cx
                        canvas.getHeight() / 2, // cy
                        radius+6, // Radius
                        paint // Paint
                );
                paint.setColor(Color.WHITE);
                canvas.drawCircle(
                        canvas.getWidth() / 2, // cx
                        canvas.getHeight() / 2, // cy
                        radius+8, // Radius
                        paint // Paint
                );
                // Display the newly created bitmap on app interface
                mImageView.setImageBitmap(bitmap);

            }
            return;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }
    }

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            mMediaRecorder = new MediaRecorder();
            if(mIsRecording) {
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                mMediaRecorder.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mChronometer.setBase(SystemClock.elapsedRealtime());
                        mChronometer.setVisibility(View.VISIBLE);
                        mChronometer.start();
                    }
                });
            } else {
                startPreview();
            }
            // Toast.makeText(getApplicationContext(),
            //         "Camera connection made!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mVideoSize;
    private Size mImageSize;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            mBackgroundHandler.post(new ImageSaver(image));
                            //!!!ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            //!!!Bitmap bitmap = fromByteBuffer(buffer);
                        }
                    } catch (Exception e) {
                        Log.w("STATESS", e.getMessage());
                    }
                    //image.close();
                }
                /*!!!Bitmap fromByteBuffer(ByteBuffer buffer) {
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes, 0, bytes.length);
                    //getColor(bytes);
                    Bitmap t=BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    Log.d("STATESS2", String.valueOf(t.getWidth()));
                    Log.d("STATESS2", String.valueOf(t.getHeight()));
                    Log.d("STATESS2", String.valueOf(mVideoSize.getHeight()));
                    Log.d("STATESS2", String.valueOf(mVideoSize.getWidth()));
                    int pixel=t.getPixel(mVideoSize.getWidth()/2,mVideoSize.getHeight()/2);
                    int red=Color.red(pixel);
                    int blue=Color.blue(pixel);
                    int green=Color.green(pixel);
                    Log.d("STATESS3", String.valueOf(red)+","+String.valueOf(blue)+","+String.valueOf(green));
                    return t;
                }*/
                /*public void getColor(byte[] data){
                    int frameHeight = mVideoSize.getHeight();
                    int frameWidth = mVideoSize.getWidth();
                    int rgb[] = new int[frameWidth * frameHeight];
                    Log.d("STATESS", String.valueOf(data.length));
                    Log.d("STATESS",String.valueOf(rgb.length));
                    decodeYUV420SP(data, rgb, frameWidth, frameHeight);
                    Bitmap bmp1 = Bitmap.createBitmap(rgb, frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
                    Log.d("STATESS3", String.valueOf(bmp1));
                    int pixel = bmp1.getPixel( bmp1.getWidth()/2,bmp1.getHeight()/2 );
                    int redValue1 = Color.red(pixel);
                    int blueValue1 = Color.blue(pixel);
                    int greenValue1 = Color.green(pixel);
                    int thiscolor1 = Color.rgb(redValue1, greenValue1, blueValue1);
                    Log.d("STATESS3", String.valueOf(thiscolor1)+String.valueOf(redValue1)+String.valueOf(blueValue1)+String.valueOf(greenValue1));
                }*/
            };
    private class ImageSaver implements Runnable {

        private final Image mImage;

        public ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(mImageFileName);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();

                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mImageFileName)));
                sendBroadcast(mediaStoreUpdateIntent);

                if(fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }
    private MediaRecorder mMediaRecorder;
    private Chronometer mChronometer;
    //private int mTotalRotation;
    private CameraCaptureSession mPreviewCaptureSession;
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {

                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            // Do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                Toast.makeText(getApplicationContext(), "AF Locked!", Toast.LENGTH_SHORT).show();
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    process(result);
                }
            };
    private CameraCaptureSession mRecordCaptureSession;
    private CameraCaptureSession.CaptureCallback mRecordCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {

                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            // Do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                Toast.makeText(getApplicationContext(), "AF Locked!", Toast.LENGTH_SHORT).show();
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    process(result);
                }
            };
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageButton mRecordImageButton;
    //private ImageButton mStillImageButton;
    private boolean mIsRecording = false;
    private boolean mIsTimelapse = false;

    private File mVideoFolder;
    private String mVideoFileName;
    private File mImageFolder;
    private String mImageFileName;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum( (long)(lhs.getWidth() * lhs.getHeight()) -
                    (long)(rhs.getWidth() * rhs.getHeight()));
        }
    }

    private Rect getZoomRect() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                Rect activeRect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if((zoom <= sBarZoom.getMax()) && (((float)zoom/10) > 1)) {
                    //int minW = (int) (activeRect.width()*zoom / sBarZoom.getMax());
                    //int minH = (int) (activeRect.height()*zoom / sBarZoom.getMax());
                    int cropW = (activeRect.width() - (int)((float)activeRect.width() / ((float)zoom/10))) / 2;
                    int cropH = (activeRect.height() - (int)((float)activeRect.height() / ((float)zoom/10))) / 2;
                    return new Rect(cropW, cropH, activeRect.width() - cropW, activeRect.height() - cropH);
                    //int limitW=(activeRect.width()/2)-(activeRect.width()/10);
                    //int limitH=(activeRect.height()/2)-(activeRect.height()/10);
                    //if(minW<limitW && minH<limitH)return new Rect(minW, minH, activeRect.width() - minW, activeRect.height() - minH);
                    //else return new Rect(limitW, limitH, activeRect.width() - limitW, activeRect.height() - limitH);
                } else if(zoom == 0){
                    return new Rect(0, 0, activeRect.width(), activeRect.height());
                }
            }
            return null;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_video_image);

        createVideoFolder();
        createImageFolder();

        sharedpreferences = getSharedPreferences(mypreference,Context.MODE_PRIVATE);

        mChronometer = (Chronometer) findViewById(R.id.chronometer);
        mTextureView = (TextureView) findViewById(R.id.textureView);
        /*frameCallback = new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {

                Log.d("STATESS","preview frame captured");
            }
        };*/
        /*
        mStillImageButton = (ImageButton) findViewById(R.id.cameraImageButton2);
        mStillImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!(mIsTimelapse || mIsRecording)) {
                    try {
                        checkWriteStoragePermission();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                lockFocus();
            }
        });
        */
        mRecordImageButton = (ImageButton) findViewById(R.id.videoOnlineImageButton);
        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                colorRanges=new ArrayList<float[]>();
                reconDone=reconDoneDefault;
                basePixel=new int[]{};
                baseHsv= new float[]{};
                previousTime=0;
                currentTime=0;
                nextResumeTime=0;
                Log.d(TAG, String.valueOf(mIsRecording));
                if (mIsRecording || mIsTimelapse) {
                    status="Ready";
                    tViewStatus.setText("Status: "+status);
                    //firstManchesterPart=true;
                    //breakConstantColorState=false;
                    mChronometer.stop();
                    mChronometer.setVisibility(View.INVISIBLE);
                    mIsRecording = false;
                    mIsTimelapse = false;
                    mRecordImageButton.setImageResource(R.mipmap.btn_video_online);

                    // Starting the preview prior to stopping recording which should hopefully
                    // resolve issues being seen in Samsung devices.
                    startPreview();
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();

                    Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mVideoFileName)));
                    sendBroadcast(mediaStoreUpdateIntent);

                } else {
                    status="Recording";
                    newdata="";
                    timer=0;
                    colorChanging=false;
                    tViewTimeFrequency.setText("Time frequency: ?ms");
                    //tViewTimeFrequency.setText("Time frequency: "+timer);
                    tViewStatus.setText("Status: "+status);
                    finalDecodedNewData="";
                    tViewReceived.setText("Manchester encoded: ");
                    tViewBits.setText("Decoded (Bits): ");
                    tViewDecoded.setText("Decoded (ASCII): ");
                    tViewColorsNumber.setText("Colors Number: 0");
                    hsv= new float[]{};
                    mIsRecording = true;
                    mRecordImageButton.setImageResource(R.mipmap.btn_video_busy);
                    try {
                        checkWriteStoragePermission();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        mRecordImageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mIsTimelapse =true;
                mRecordImageButton.setImageResource(R.mipmap.btn_timelapse);
                try {
                    checkWriteStoragePermission();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });

        sBarSensorSensitivity = (SeekBar) findViewById(R.id.sBarSensorSensitivity);
        tViewSensorSensitivity = (TextView) findViewById(R.id.tViewSensorSensitivity);
        tViewSensorSensitivity.setText("Sensitivity: "+sBarSensorSensitivity.getProgress() + "/" + sBarSensorSensitivity.getMax());
        sBarSensorSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sensorSensitivity = progress;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //write custom code to on start progress
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("SensorSensitivity", sensorSensitivity);
                editor.commit();
                tViewSensorSensitivity.setText("Sensitivity: "+sensorSensitivity + "/" + seekBar.getMax());
                startPreview();
            }
        });
        if (sharedpreferences.contains("SensorSensitivity")) {
            sensorSensitivity=sharedpreferences.getInt("SensorSensitivity", sensorSensitivity);
            sBarSensorSensitivity.setProgress(sensorSensitivity);
        }

        sBarExposureTime = (SeekBar) findViewById(R.id.sBarExposureTime);
        tViewExposureTime = (TextView) findViewById(R.id.tViewExposureTime);
        tViewExposureTime.setText("Exposure time: "+sBarExposureTime.getProgress() + "/" + sBarExposureTime.getMax());
        sBarExposureTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                exposureTime = progress;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //write custom code to on start progress
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("ExposureTime", exposureTime);
                editor.commit();
                tViewExposureTime.setText("Exposure time: "+exposureTime + "/" + seekBar.getMax());
                startPreview();
            }
        });
        if (sharedpreferences.contains("ExposureTime")) {
            exposureTime=sharedpreferences.getInt("ExposureTime", exposureTime);
            sBarExposureTime.setProgress(exposureTime);
            tViewExposureTime.setText("Exposure time: "+exposureTime + "/" + sBarExposureTime.getMax());
        }

        sBarZoom = (SeekBar) findViewById(R.id.sBarZoom);
        tViewZoom = (TextView) findViewById(R.id.tViewZoom);
        tViewZoom.setText("Zoom: "+((float)sBarZoom.getProgress()/10) + "/" + ((float)sBarZoom.getMax()/10));
        sBarZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                zoom = progress;
                zoomRect = getZoomRect();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //write custom code to on start progress
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("Zoom", zoom);
                editor.commit();
                tViewZoom.setText("Zoom: "+((float)zoom/10) + "/" + ((float)seekBar.getMax()/10));
                startPreview();
            }
        });
        if (sharedpreferences.contains("Zoom")) {
            zoom=sharedpreferences.getInt("Zoom", zoom);
            sBarZoom.setProgress(zoom);
            zoomRect = getZoomRect();
            tViewZoom.setText("Zoom: "+((float)zoom/10) + "/" + ((float)sBarZoom.getMax()/10));
            Log.d(TAG, "ZOOM"+zoom);
        }

        mImageView = (ImageView) findViewById(R.id.iv);

        sBarRadius = (SeekBar) findViewById(R.id.sBarRadius);
        tViewRadius = (TextView) findViewById(R.id.tViewRadius);
        tViewRadius.setText("Radius: "+sBarRadius.getProgress() + "/" + sBarRadius.getMax());
        drawCircleCanvas();
        sBarRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radius = progress;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //write custom code to on start progress
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("Radius", radius);
                editor.commit();
                tViewRadius.setText("Radius: "+radius + "/" + seekBar.getMax());
                drawCircleCanvas();
                startPreview();
            }
        });
        if (sharedpreferences.contains("Radius")) {
            radius=sharedpreferences.getInt("Radius", radius);
            sBarRadius.setProgress(radius);
            tViewRadius.setText("Radius: "+radius + "/" + sBarRadius.getMax());
            drawCircleCanvas();
        }

        sBarMinSaturation = (SeekBar) findViewById(R.id.sBarMinSaturation);
        tViewMinSaturation = (TextView) findViewById(R.id.tViewMinSaturation);
        tViewMinSaturation.setText("Saturation's variation's limit: "+((float)sBarMinSaturation.getProgress()/100) + "/" + ((float)sBarMinSaturation.getMax()/100));
        sBarMinSaturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                minSaturation = progress;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //write custom code to on start progress
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("MinSaturation", minSaturation);
                editor.commit();
                tViewMinSaturation.setText("Saturation's variation's limit: "+((float)minSaturation/100) + "/" + ((float)seekBar.getMax()/100));
                startPreview();
            }
        });
        if (sharedpreferences.contains("MinSaturation")) {
            minSaturation=sharedpreferences.getInt("MinSaturation", minSaturation);
            sBarMinSaturation.setProgress(minSaturation);
            tViewMinSaturation.setText("Saturation's variation's limit: "+((float)minSaturation/100) + "/" + ((float)sBarMinSaturation.getMax()/100));
        }

        sBarMinHue = (SeekBar) findViewById(R.id.sBarMinHue);
        tViewMinHue = (TextView) findViewById(R.id.tViewMinHue);
        tViewMinHue.setText("Hue's variation's limit: "+((float)sBarMinHue.getProgress()/100) + "/" + ((float)sBarMinHue.getMax()/100));
        sBarMinHue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                minHue = progress;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //write custom code to on start progress
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("MinHue", minHue);
                editor.commit();
                tViewMinHue.setText("Hue's variation's limit: "+((float)minHue/100) + "/" + ((float)seekBar.getMax()/100));
                startPreview();
            }
        });
        if (sharedpreferences.contains("MinHue")) {
            minHue=sharedpreferences.getInt("MinHue", minHue);
            sBarMinHue.setProgress(minHue);
            tViewMinHue.setText("Hue's variation's limit: "+((float)minHue/100) + "/" + ((float)sBarMinHue.getMax()/100));
        }

        spinnerDecodingMode = (Spinner) findViewById(R.id.decodingMode);
        String[] decodingModeItems = new String[] { "Circular Mode", "Manchester Mode" };
        ArrayAdapter<String> adapterDecodingMode = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, decodingModeItems);
        adapterDecodingMode.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDecodingMode.setAdapter(adapterDecodingMode);
        spinnerDecodingMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt("DecodingMode", position);
                editor.commit();
                if(position==0)advancedMode=true;
                else advancedMode=false;
                if(parent != null && (TextView)parent.getChildAt(0) != null)((TextView)parent.getChildAt(0)).setTextColor(Color.rgb(255, 255, 255));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        if (sharedpreferences.contains("DecodingMode")) {
            int decodingMode=0;
            if(advancedMode==true)decodingMode=0;else decodingMode=1;
            decodingMode=sharedpreferences.getInt("DecodingMode", decodingMode);
            spinnerDecodingMode.setSelection(decodingMode);
        }

        tViewColor = (TextView) findViewById(R.id.tViewColor);
        tViewStatus = (TextView) findViewById(R.id.tViewStatus);
        tViewTimeFrequency = (TextView) findViewById(R.id.tViewTimeFrequency);
        tViewTimeFrequency.setText("Time frequency : ?ms");
        tViewColorsNumber = (TextView) findViewById(R.id.tViewColorsNumber);
        tViewColorsNumber.setText("Colors Number : 0");
        tViewReceived = (TextView) findViewById(R.id.tViewReceived);
        tViewReceived.setText("Manchester encoded: ");
        tViewBits = (TextView) findViewById(R.id.tViewBits);
        tViewBits.setText("Decoded (Bits): ");
        tViewDecoded = (TextView) findViewById(R.id.tViewDecoded);
        tViewDecoded.setText("Decoded (ASCII): ");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if(mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if(grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
        if(requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if(mIsRecording || mIsTimelapse) {
                    mIsRecording = true;
                    mRecordImageButton.setImageResource(R.mipmap.btn_video_busy);
                }
                Toast.makeText(this,
                        "Permission successfully granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "App needs to save video to run", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();

        stopBackgroundThread();

        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas) {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if(hasFocas) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height) {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                //mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                // There is an issue with the device and the sensor orientations at the same time.
                //boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                /*if(swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }*/
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotatedWidth, rotatedHeight);
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotatedWidth, rotatedHeight);
                mImageReader = ImageReader.newInstance(mImageSize.getWidth(), mImageSize.getHeight(), ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this,
                                "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }

            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecord() {

        try {
            if(mIsRecording) {
                setupMediaRecorder();
            } else if(mIsTimelapse) {
                setupTimelapse();
            }
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);


            mCaptureRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            mCaptureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,sensorSensitivity);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long)exposureTime);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, (long)frameDuration);
            if(zoomRect!=null){mCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);Log.d(TAG, zoomRect.toString());}

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mRecordCaptureSession = session;
                            try {
                                mRecordCaptureSession.setRepeatingRequest(
                                        mCaptureRequestBuilder.build(), null, null
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startRecord");
                        }
                    }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void startPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            int sensitivityMin=cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE).getLower();
            int sensitivityMax=cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE).getUpper();
            if(sBarSensorSensitivity.getMin()!=sensitivityMin || sBarSensorSensitivity.getMax()!=sensitivityMax) {
                sBarSensorSensitivity.setMin(sensitivityMin);
                sBarSensorSensitivity.setMax(sensitivityMax);
                if(sensorSensitivity<0)sensorSensitivity=mCaptureRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY);
                tViewSensorSensitivity.setText("Sensitivity: "+sensorSensitivity + "/" + sBarSensorSensitivity.getMax());
                sBarSensorSensitivity.setProgress(sensorSensitivity);
            }

            int exposureTimeMin=cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).getLower().intValue();
            int exposureTimeMax=cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).getUpper().intValue();
            if(sBarExposureTime.getMin()!=exposureTimeMin || sBarExposureTime.getMax()!=(exposureTimeMax/5)) {
                sBarExposureTime.setMin(exposureTimeMin);
                sBarExposureTime.setMax(exposureTimeMax/5);
                if(exposureTime<0)exposureTime=mCaptureRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME).intValue();
                tViewExposureTime.setText("Exposure time: "+exposureTime + "/" + sBarExposureTime.getMax());
                sBarExposureTime.setProgress(exposureTime);
            }
            frameDuration=0;//cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION).intValue();

            int zoomMax=cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue()*10;
            if(sBarZoom.getMax()!=zoomMax) {
                Log.d(TAG,"MAXZOOM"+zoomMax);
                sBarZoom.setMin(10);
                sBarZoom.setMax(zoomMax);
                tViewZoom.setText("Zoom: "+((float)zoom/10) + "/" + ((float)sBarZoom.getMax()/10));
                sBarZoom.setProgress(zoom);
            }
            zoomRect=getZoomRect();
            if(sBarMinSaturation.getMax()!=10000) {
                sBarMinSaturation.setMin(0);
                sBarMinSaturation.setMax(10000);
                tViewMinSaturation.setText("Saturation's variation's limit: "+((float)minSaturation/100) + "/" + ((float)sBarMinSaturation.getMax()/100));
                sBarMinSaturation.setProgress(minSaturation);
            }
            if(sBarMinHue.getMax()!=36000) {
                sBarMinHue.setMin(0);
                sBarMinHue.setMax(36000);
                tViewMinHue.setText("Hue's variation's limit: "+((float)minHue/100) + "/" + ((float)sBarMinHue.getMax()/100));
                sBarMinHue.setProgress(minHue);
            }

            Log.d(TAG, String.valueOf(cameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)));
            mCaptureRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            mCaptureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,sensorSensitivity);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long)exposureTime);
            mCaptureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, (long)frameDuration);
            if(zoomRect!=null){mCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);Log.d(TAG, zoomRect.toString());}

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigured: startPreview");
                            mPreviewCaptureSession = session;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startPreview");

                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startStillCaptureRequest() {
        try {
            if(mIsRecording) {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            } else {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            //mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mTotalRotation);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);

                            try {
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };

            if(mIsRecording) {
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            } else {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if(mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("LightExfiltrationReceiver");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrienatation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrienatation + deviceOrientation + 270) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choices) {
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    private void createVideoFolder() {
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder = new File(movieFile, "LightExiltrationReceiver");
        if(!mVideoFolder.exists()) {
            mVideoFolder.mkdirs();
        }
    }

    private File createVideoFileName() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "VIDEO_" + timestamp + "_";
        File videoFile = File.createTempFile(prepend, ".mp4", mVideoFolder);
        mVideoFileName = videoFile.getAbsolutePath();
        return videoFile;
    }

    private void createImageFolder() {
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(imageFile, "LightExiltrationReceiver");
        if(!mImageFolder.exists()) {
            mImageFolder.mkdirs();
        }
    }

    private File createImageFileName() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "IMAGE_" + timestamp + "_";
        File imageFile = File.createTempFile(prepend, ".jpg", mImageFolder);
        mImageFileName = imageFile.getAbsolutePath();
        return imageFile;
    }

    private void checkWriteStoragePermission() throws IOException {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(mIsTimelapse || mIsRecording) {
                    startRecord();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mMediaRecorder.start();
                    //getColor();
                    mChronometer.setBase(SystemClock.elapsedRealtime());
                    mChronometer.setVisibility(View.VISIBLE);
                    mChronometer.start();
                }
            } else {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "app needs to be able to save videos", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        } else {
            try {
                createVideoFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(mIsRecording || mIsTimelapse) {
                startRecord();
                mMediaRecorder.start();
                mChronometer.setBase(SystemClock.elapsedRealtime());
                mChronometer.setVisibility(View.VISIBLE);
                mChronometer.start();
            }
        }
    }

    private void setupMediaRecorder() throws IOException {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(1000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }

    private void setupTimelapse() throws IOException {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH));
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setCaptureRate(2);
        //mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }

    private void lockFocus() {
        mCaptureState = STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            if(mIsRecording) {
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(), mRecordCaptureCallback, mBackgroundHandler);
            } else {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}