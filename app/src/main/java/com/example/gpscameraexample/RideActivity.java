package com.example.gpscameraexample;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class RideActivity extends AppCompatActivity implements SurfaceHolder.Callback {


    Camera camera;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    boolean previewing = false;
    Button btnRide;
    long mTimerTime =18000000;
    TextView txtTimer;
    int timesec,timemin,timehr;
    final Handler handler = new Handler();
    Timer timer = new Timer();
    Thread timerthread;
    double lat,lng;
    String fname;
    int RideNo;
    SharedPreferences mPref;
    SharedPreferences.Editor mEditor;
    String locationAddress;
    TimerTask doAsynchronousTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //initialize the shared preferences
        mPref = getApplicationContext().getSharedPreferences("MyPref", 0);
        mEditor =mPref.edit();


        //to set the application Screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        txtTimer = (TextView)findViewById(R.id.timerText);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        btnRide = (Button)findViewById(R.id.btn_startRide);

        txtTimer.setText("Welcome To RideIT!\nTap on START TRIP to start your trip");

        //on start/stop ride btn click
        btnRide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //resetting the time
                timehr = 0;
                timemin = 0;
                timesec = 0;

                if (btnRide.getText().toString().toUpperCase().equals("START RIDE")) {
                  //for every Ride started It will get a RideNo
                    RideNo = mPref.getInt("RideNo", 0);
                    RideNo++;
                    btnRide.setText("STOP RIDE");
                    mEditor.putInt("RideNo", RideNo);
                    mEditor.commit();
                    takepic();
                    //taking picture every 5 minutes
                    doAsynchronousTask =  new TimerTask() {
                        @Override
                        public void run() {
                            handler.post(new Runnable() {
                                @SuppressWarnings("unchecked")
                                public void run() {
                                    try {
                                        takepic();
                                    }
                                    catch (Exception e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    };
                    timer.schedule(doAsynchronousTask,0,mTimerTime);
                    //timer for total travel time
                    timerthread = new RideTimer();
                    timerthread.start();

                } else {
                    btnRide.setText("START RIDE");
                    //after clicking on stop ride, need to cancel auto picture taking timer and ride timer
                    doAsynchronousTask.cancel();
                    timerthread.interrupt();
                    takepic();
                    txtTimer.setText("Thank you for a wonderful trip with us");
                    mEditor.putInt("RideNo", RideNo);
                    mEditor.commit();
                }

            }
        });


    }

//calculate the address from given lat lng
    private class GeocoderHandler extends Handler {
        @Override
        public void handleMessage(Message message) {

            switch (message.what) {
                case 1:
                    Bundle bundle = message.getData();
                    locationAddress = bundle.getString("address");
                    break;
                default:
                    locationAddress = null;
            }
            //replace if there is any null value
            locationAddress = locationAddress.replaceAll("null", "");

        }
    }

    //updating surface view
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(previewing){
            camera.stopPreview();
            //now we will know that camera preview is not available
            previewing = false;
        }

        if (camera != null){
            try {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
                //preview started
                previewing = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void takepic(){
        Camera.PictureCallback mCall = new Camera.PictureCallback()
        {

            public void onPictureTaken(byte[] data, Camera mcamera)
            {
                //decode the data obtained by the camera into a Bitmap
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);

                SaveImage(bmp);

                GPSTracker gps = new GPSTracker(RideActivity.this);
                // check if GPS enabled
                if (gps.canGetLocation()) {

                    lat = gps.getLatitude();
                    lng = gps.getLongitude();

                } else {
                    // can't get location
                    // GPS or Network is not enabled
                    // Ask user to enable GPS/network in settings
                    gps.showSettingsAlert();
                }
                AddressCalculation locationAdd = new AddressCalculation();
                locationAdd.getAddressFromLocation(lat, lng,
                        getApplicationContext(), new GeocoderHandler());


                createFile(locationAddress, fname);
                camera.startPreview();
            }
        };

        //taking picture
        camera.takePicture(null, null, mCall);
    }

    private void SaveImage(Bitmap finalBitmap) {


        Display d = getWindowManager().getDefaultDisplay();
        int x = d.getWidth();
        int y = d.getHeight();
        // scale it to fit the screen, x and y swapped because my image is wider than it is tall
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(finalBitmap, y, x, true);

        // create a matrix object
        Matrix matrix = new Matrix();
        matrix.postRotate(-90); // anti-clockwise by 90 degrees

        // create a new bitmap from the original using the matrix to transform the result
         scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);



        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/RideImages");
        myDir.mkdirs();
        fname = System.currentTimeMillis()+".jpg";

        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            //Save image in Directory
            FileOutputStream out = new FileOutputStream(file);
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
             out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        //takepic();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
        camera = null;
        previewing = false;
    }

    public void createFile(String location,String filename)  {

       FileWriter fWriter;
        try{
            fWriter = new FileWriter("/sdcard/RideImages/RideDetailsNo"+RideNo+".txt",true);
            fWriter.append("\n\nImageFileName:"+filename+"\n"+location);
            fWriter.append("\n\n********************************************");
            fWriter.flush();
            fWriter.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
//    class PictureTimerTask extends TimerTask{
//        public void run()
//        {
//            handler.post(new Runnable() {
//                @SuppressWarnings("unchecked")
//                public void run() {
//                    try {
//                        Toast.makeText(RideActivity.this,"I am Here",Toast.LENGTH_SHORT).show();
//                        takepic();
//                    } catch (Exception e) {
//                        // TODO Auto-generated catch block
//                        e.printStackTrace();
//                    }
//                }
//            });
//
//            timer.schedule(doAsynchronousTask, 4000);
//        }
//    }
    class RideTimer extends Thread{


        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    Thread.sleep(1000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //change time for textView
                            timesec++;
                            if (timesec==60)
                            {
                                timemin++;
                                timesec=0;
                            }
                            if (timemin==60){
                                timehr++;
                                timemin=0;
                            }
                            txtTimer.setText(timehr+":"+timemin+":"+timesec);
                        }
                    });
                }
            } catch (InterruptedException e) {
            }

        }

        @Override
        public void interrupt() {
            super.interrupt();
        }
    }
}