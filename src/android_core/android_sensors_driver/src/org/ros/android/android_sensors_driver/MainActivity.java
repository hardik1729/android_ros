/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.android_sensors_driver;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.ros.address.InetAddressFactory;
import org.ros.android.RosActivity;
import org.ros.android.view.RosTextView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuInflater;
import android.widget.ToggleButton;

import java.lang.reflect.Array;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author chadrockey@gmail.com (Chad Rockey)
 * @author axelfurlan@gmail.com (Axel Furlan)
 */


public class MainActivity extends RosActivity
{
//    static {
//        if(!OpenCVLoader.initDebug());
//            Log.d("OpenCVLoader","unable to load opencv");
//    }
    public static final int VIEW_MODE_RGBA = 0;
    public static final int VIEW_MODE_GRAY = 1;
    public static final int VIEW_MODE_CANNY = 2;
    public static final int IMAGE_TRANSPORT_COMPRESSION_PNG = 1;
    public static final int IMAGE_TRANSPORT_COMPRESSION_JPEG = 2;

    public static int viewMode = VIEW_MODE_RGBA;
    public static int imageCompression = IMAGE_TRANSPORT_COMPRESSION_JPEG;

    public static int imageJPEGCompressionQuality = 100;
    public static int imagePNGCompressionQuality = 1;

    private int currentapiVersion = android.os.Build.VERSION.SDK_INT;
    private int sensorDelay = 20000; // 20,000 us == 50 Hz for Android 3.1 and above

    public static int mCameraId1 = 0;
    public static int mCameraId2 = 1;
    private NavSatFixPublisher fix_pub;
    private ImuPublisher imu_pub;
    private MagneticFieldPublisher magnetic_field_pub;
    private FluidPressurePublisher fluid_pressure_pub;
    private IlluminancePublisher illuminance_pub;
    private TemperaturePublisher temperature_pub;
    private CameraPublisher cam_pub = new CameraPublisher();
    private CameraPublisher cam_pub2 = new CameraPublisher();
    private CameraBridgeViewBase mOpenCvCameraView;
    private CameraBridgeViewBase mOpenCvCameraView2;

    private LocationManager mLocationManager;
    private SensorManager mSensorManager;
    CameraManager manager;

    private String[] camID;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("Finally", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    public MainActivity()
    {
        super("ROS Sensors Driver", "ROS Sensors Driver");
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        if(mOpenCvCameraView2 != null)
            mOpenCvCameraView2.disableView();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_DENIED
                || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_DENIED
                || checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_DENIED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        manager=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            camID=manager.getCameraIdList();
            for(int i=0;i<camID.length;i++){
                float[] a=manager.getCameraCharacteristics(camID[i]).get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                for(int j=0;j<a.length;j++)
                    Log.d("hola",a[j]+"x"+camID[i]);
            }
            TextView rosCamNo=findViewById(R.id.text);
            rosCamNo.setText("Number Of Cams : "+camID.length);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(cam_pub);

        mOpenCvCameraView2 = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView2);
        mOpenCvCameraView2.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView2.setCvCameraViewListener(cam_pub2);

        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        mSensorManager = (SensorManager)this.getSystemService(SENSOR_SERVICE);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("finally", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d("finally", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        if (mOpenCvCameraView2 != null)
            mOpenCvCameraView2.disableView();
    }

    @Override
    protected void init(final NodeMainExecutor nodeMainExecutor)
    {
        final URI masterURI = getMasterUri();
        //masterURI = URI.create("http://192.168.15.247:11311/");
        //masterURI = URI.create("http://10.0.1.157:11311/");
        final MainActivity mainActivity=this;

        if(currentapiVersion <= android.os.Build.VERSION_CODES.HONEYCOMB){
            sensorDelay = SensorManager.SENSOR_DELAY_UI; // 16.7Hz for older devices.  They only support enum values, not the microsecond version.
        }

        final CheckBox camera1Box=findViewById(R.id.camera1_box);
        final EditText camera1Text=findViewById(R.id.camera1_text);
        final EditText camID1=findViewById(R.id.camID1);
        camera1Box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(camera1Box.isChecked()){
                    camera1Text.setFocusable(false);
                    camID1.setFocusable(false);
                    if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD) {
                        mOpenCvCameraView.enableView();
                        NodeConfiguration nodeConfiguration7 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
                        nodeConfiguration7.setMasterUri(masterURI);
                        cam_pub.mainActivity = mainActivity;
                        if(!camera1Text.getText().toString().isEmpty())
                            cam_pub.topic = camera1Text.getText().toString();
                        else
                            cam_pub.topic="android/camera1";
                        if(!camID1.getText().toString().isEmpty() && Arrays.asList(camID).contains(camID1.getText().toString()))
                            cam_pub.cameraID = Arrays.asList(camID).indexOf(camID1.getText().toString());
                        else
                            cam_pub.cameraID=mCameraId1;
                        nodeConfiguration7.setNodeName("android_sensors_driver_camera1"+cam_pub.cameraID);
                        mOpenCvCameraView.setCameraIndex(cam_pub.cameraID);
                        if(Arrays.asList(camID).contains(cam_pub.cameraID+"")){
                            mOpenCvCameraView.setCameraPermissionGranted();
                            nodeMainExecutor.execute(cam_pub, nodeConfiguration7);
                        }
                    }
                }
                else{
                    camera1Text.setFocusableInTouchMode(true);
                    camID1.setFocusableInTouchMode(true);
                    nodeMainExecutor.shutdownNodeMain(cam_pub);
                    mOpenCvCameraView.disableView();
                }
            }
        });

        final CheckBox camera2Box=findViewById(R.id.camera2_box);
        final EditText camera2Text=findViewById(R.id.camera2_text);
        final EditText camID2=findViewById(R.id.camID2);
        camera2Box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(camera2Box.isChecked()){
                    camera2Text.setFocusable(false);
                    camID2.setFocusable(false);
                    if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD) {
                        mOpenCvCameraView2.enableView();
                        NodeConfiguration nodeConfiguration7 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
                        nodeConfiguration7.setMasterUri(masterURI);
                        cam_pub2.mainActivity = mainActivity;
                        if(!camera2Text.getText().toString().isEmpty())
                            cam_pub2.topic = camera2Text.getText().toString();
                        else
                            cam_pub2.topic="android/camera2";
                        if(!camID2.getText().toString().isEmpty() && Arrays.asList(camID).contains(camID2.getText().toString()))
                            cam_pub2.cameraID = Arrays.asList(camID).indexOf(camID2.getText().toString());
                        else
                            cam_pub2.cameraID=mCameraId2;
                        nodeConfiguration7.setNodeName("android_sensors_driver_camera2"+cam_pub2.cameraID);
                        mOpenCvCameraView2.setCameraIndex(cam_pub2.cameraID);
                        if(Arrays.asList(camID).contains(cam_pub2.cameraID+"")){
                            mOpenCvCameraView2.setCameraPermissionGranted();
                            nodeMainExecutor.execute(cam_pub2, nodeConfiguration7);
                        }
                    }
                }
                else{
                    camera2Text.setFocusableInTouchMode(true);
                    camID2.setFocusableInTouchMode(true);
                    nodeMainExecutor.shutdownNodeMain(cam_pub2);
                    mOpenCvCameraView2.disableView();
                }
            }
        });

        final CheckBox imuBox=findViewById(R.id.imu_box);
        final EditText imuText=findViewById(R.id.imu_text);
        imuBox.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if(imuBox.isChecked()){
                    imuText.setFocusable(false);
                    if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD) {
                        NodeConfiguration nodeConfiguration3 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
                        nodeConfiguration3.setMasterUri(masterURI);
                        nodeConfiguration3.setNodeName("android_sensors_driver_imu");
                        imu_pub = new ImuPublisher(mSensorManager, sensorDelay);
                        if (!imuText.getText().toString().isEmpty())
                            imu_pub.topic = imuText.getText().toString();
                        else
                            imu_pub.topic = "android/imu";
                        nodeMainExecutor.execute(imu_pub, nodeConfiguration3);
                    }
                }
                else{
                    imuText.setFocusableInTouchMode(true);
                    nodeMainExecutor.shutdownNodeMain(imu_pub);
                }
            }
        });

        final CheckBox fixBox=findViewById(R.id.gps_box);
        final EditText fixText=findViewById(R.id.gps_text);
        fixBox.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if(fixBox.isChecked()){
                    fixText.setFocusable(false);
                    if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD) {
                        NodeConfiguration nodeConfiguration2 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
                        nodeConfiguration2.setMasterUri(masterURI);
                        nodeConfiguration2.setNodeName("android_sensors_driver_nav_sat_fix");
                        fix_pub = new NavSatFixPublisher(mLocationManager);
                        fix_pub.mainActivity = mainActivity;
                        if (!fixText.getText().toString().isEmpty())
                            fix_pub.topic = fixText.getText().toString();
                        else
                            fix_pub.topic = "android/fix";
                        nodeMainExecutor.execute(fix_pub, nodeConfiguration2);
                    }
                }
                else{
                    fixText.setFocusableInTouchMode(true);
                    nodeMainExecutor.shutdownNodeMain(fix_pub);
                }
            }
        });

        findViewById(R.id.shutdown).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nodeMainExecutor.shutdown();
            }
        });

        /*@SuppressWarnings("deprecation")
        int tempSensor = Sensor.TYPE_TEMPERATURE; // Older temperature
        if(currentapiVersion <= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH){
            tempSensor = Sensor.TYPE_AMBIENT_TEMPERATURE; // Use newer temperature if possible
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration.setMasterUri(masterURI);
            nodeConfiguration.setNodeName("android_sensors_driver_magnetic_field");
            this.magnetic_field_pub = new MagneticFieldPublisher(mSensorManager, sensorDelay);
            magnetic_field_pub.topic="android/magnetic_field";
            nodeMainExecutor.execute(this.magnetic_field_pub, nodeConfiguration);
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            NodeConfiguration nodeConfiguration2 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration2.setMasterUri(masterURI);
            nodeConfiguration2.setNodeName("android_sensors_driver_nav_sat_fix");
            this.fix_pub = new NavSatFixPublisher(mLocationManager);
            fix_pub.mainActivity=this;
            fix_pub.topic="android/fix";
            nodeMainExecutor.execute(this.fix_pub, nodeConfiguration2);
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            NodeConfiguration nodeConfiguration3 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration3.setMasterUri(masterURI);
            nodeConfiguration3.setNodeName("android_sensors_driver_imu");
            this.imu_pub = new ImuPublisher(mSensorManager, sensorDelay);
            imu_pub.topic="android/imu";
            nodeMainExecutor.execute(this.imu_pub, nodeConfiguration3);
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            NodeConfiguration nodeConfiguration4 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration4.setMasterUri(masterURI);
            nodeConfiguration4.setNodeName("android_sensors_driver_pressure");
            this.fluid_pressure_pub = new FluidPressurePublisher(mSensorManager, sensorDelay);
            nodeMainExecutor.execute(this.fluid_pressure_pub, nodeConfiguration4);
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            NodeConfiguration nodeConfiguration5 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration5.setMasterUri(masterURI);
            nodeConfiguration5.setNodeName("android_sensors_driver_illuminance");
            this.illuminance_pub = new IlluminancePublisher(mSensorManager, sensorDelay);
            nodeMainExecutor.execute(this.illuminance_pub, nodeConfiguration5);
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            NodeConfiguration nodeConfiguration6 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration6.setMasterUri(masterURI);
            nodeConfiguration6.setNodeName("android_sensors_driver_temperature");
            this.temperature_pub = new TemperaturePublisher(mSensorManager, sensorDelay, tempSensor);
            nodeMainExecutor.execute(this.temperature_pub, nodeConfiguration6);
        }*/

        /*if(currentapiVersion >= android.os.Build.VERSION_CODES.GINGERBREAD){
            mOpenCvCameraView.setCameraIndex(mCameraId1);
            mOpenCvCameraView.setCameraPermissionGranted();
            NodeConfiguration nodeConfiguration7 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration7.setMasterUri(masterURI);
            nodeConfiguration7.setNodeName("android_sensors_driver_camera");
            cam_pub.mainActivity = this;
            cam_pub.topic="android/camera1";
            cam_pub.cameraID=mCameraId1;
            nodeMainExecutor.execute(this.cam_pub, nodeConfiguration7);

            mOpenCvCameraView2.setCameraIndex(mCameraId2);
            mOpenCvCameraView2.setCameraPermissionGranted();
            NodeConfiguration nodeConfiguration8 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration8.setMasterUri(masterURI);
            nodeConfiguration8.setNodeName("android_sensors_driver_camera2");
            cam_pub2.mainActivity = this;
            cam_pub2.topic="android/camera2";
            cam_pub2.cameraID=mCameraId2;
            nodeMainExecutor.execute(this.cam_pub2, nodeConfiguration8);
        }*/
    }

    public enum eScreenOrientation
    {
        PORTRAIT (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        LANDSCAPE (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        PORTRAIT_REVERSE (ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
        LANDSCAPE_REVERSE (ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
        UNSPECIFIED_ORIENTATION (ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        public final int activityInfoValue;

        eScreenOrientation ( int orientation )
        {
            activityInfoValue = orientation;
        }
    }

    public  eScreenOrientation getCurrentScreenOrientation()
    {
        final int rotation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

        final int orientation = getResources().getConfiguration().orientation;
        switch ( orientation )
        {
            case Configuration.ORIENTATION_PORTRAIT:
                if ( rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90 )
                    return eScreenOrientation.PORTRAIT;
                else
                    return eScreenOrientation.PORTRAIT_REVERSE;
            case Configuration.ORIENTATION_LANDSCAPE:
                if ( rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90 )
                    return eScreenOrientation.LANDSCAPE;
                else
                    return eScreenOrientation.LANDSCAPE_REVERSE;
            default:
                return eScreenOrientation.UNSPECIFIED_ORIENTATION;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_help) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(true);
                builder.setTitle(getResources().getString(R.string.help_title));
                builder.setMessage(getResources().getString(R.string.help_message));
                builder.setInverseBackgroundForced(true);
                builder.setNegativeButton(getResources().getString(R.string.help_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                builder.setNeutralButton(getResources().getString(R.string.help_wiki),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                Uri u = Uri.parse("http://www.ros.org/wiki/android_sensors_driver");
                                try {
                                    // Start the activity
                                    i.setData(u);
                                    startActivity(i);
                                } catch (ActivityNotFoundException e) {
                                    // Raise on activity not found
                                    Toast toast = Toast.makeText(MainActivity.this, "Browser not found.", Toast.LENGTH_SHORT);
                                    toast.show();
                                }
                            }
                        });
                builder.setPositiveButton(getResources().getString(R.string.help_report),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                Uri u = Uri.parse("https://github.com/ros-android/android_sensors_driver/issues/new");
                                try {
                                    // Start the activity
                                    i.setData(u);
                                    startActivity(i);
                                } catch (ActivityNotFoundException e) {
                                    // Raise on activity not found
                                    Toast toast = Toast.makeText(MainActivity.this, "Browser not found.", Toast.LENGTH_SHORT);
                                    toast.show();
                                }
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();

        }
        return super.onOptionsItemSelected(item);
    }
}

