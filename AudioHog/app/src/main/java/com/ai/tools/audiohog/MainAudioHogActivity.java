package com.ai.tools.audiohog;

import java.io.IOException;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;


import android.media.AudioManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

public class MainAudioHogActivity extends Activity {

    private static final String TAG = "AudioHog";




    //private AssetFileDescriptor afd = null;
    private AudioManager.OnAudioFocusChangeListener mv_rAudioFocusListener;
    private Analytics mv_rAnalytics;


    //UI elements
    private Button mPauseAudio_B;
    private Button mStartAudio_B;
    private Button mRequestAudioFocus_B;
    private Button mAbandonAudioFocus_B;
    private Spinner mAudioStream_SP;
    private StreamAdapter mStreamAdapter;

    //constants
    private final int POS_STREAM_ALARM = 0;
    private final int POS_STREAM_MUSIC = 1;
    private final int POS_STREAM_NOTIFICATION = 2;
    private final int POS_STREAM_DTMF = 3;
    private final int POS_STREAM_RING = 4;
    private final int POS_STREAM_SYSTEM = 5;
    private final int POS_STREAM_VOICE_CALL = 6;
    private final int POS_STREAM_DEFAULT = 7;


    private final int POS_DUR_GAIN = 0;
    private final int POS_DUR_GAIN_TRANSIENT = 1;
    private final int POS_DUR_GAIN_TRANSIENT_MAY_DUCK = 2;
    private final int POS_DUR_LOSS = 3;
    private final int POS_DUR_LOSS_TRANSIENT = 4;
    private final int POS_DUR_LOSS_TRANSIENT_CAN_DUCK = 5;

    private Spinner mAudioFocusDuration_SP;
    private boolean mv_bActivityDestroying = false;

    //audiohog service handle



    private FocusAdapter mFocusAdapter;
    private Handler mv_rHandler = new Handler();
    private Runnable mv_rAdRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if(!mv_bActivityDestroying) {
                //fire up a new ad
                Util.insertAd(MainAudioHogActivity.this);
            /* and here comes the "trick" */
                mv_rHandler.postDelayed(this, 60000);
            }
        }
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_audio_hog);
        initUI();
        Util.insertAd(this);

        //start the ad refresh loop
        mv_rHandler.postDelayed(mv_rAdRefreshRunnable, 60000);

        //bind to service
        Intent serviceIntent = new Intent(this,AudioHogService.class);
        bindService(serviceIntent,mv_rServiceConnection,Context.BIND_AUTO_CREATE);

    }//end onCreate()

    @Override
    protected void onResume(){
        super.onResume();

        Util.resumeAdView(this);

    }

    @Override
    protected void onPause(){
        super.onPause();

        Util.pauseAdView(this);

    }

    @Override
    protected void onDestroy(){
        Log.d(TAG, "take release -- onDestroy()");
        super.onDestroy();
        mv_bActivityDestroying = true;


        Util.destroyAdView(this);

        //kill the ad refresh loop
        //mv_rHandler.getLooper().quit();//can't do this with a handler that is using the main thread's looper!

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_audio_hog, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch(id){
            case R.id.action_quit:
                finish();
                return true;
            case R.id.action_exit_service:
                mv_rServiceInterface.exit();
                return true;
            case R.id.action_bind_service:
                Intent serviceIntent = new Intent(this,AudioHogService.class);
                bindService(serviceIntent,mv_rServiceConnection,Context.BIND_AUTO_CREATE);

                return true;
        }


        return super.onOptionsItemSelected(item);
    }

    private IAudioHog mv_rServiceInterface;
    private ServiceConnection mv_rServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.i(TAG,"audio hog service connected");
            mv_rServiceInterface = IAudioHog.Stub.asInterface(iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG,"audio hog service disconnected");
            mv_rServiceInterface = null;
        }
    };



    public void initUI(){



        mAudioStream_SP = (Spinner)findViewById(R.id.widget_activity_main_stream_SP);
        mStreamAdapter = new StreamAdapter(this,R.layout.simple_spinner,this.getResources().getStringArray(R.array.audio_stream_type_array));
        mStreamAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        mAudioStream_SP.setAdapter(mStreamAdapter);


        mAudioStream_SP.setOnItemSelectedListener(new OnItemSelectedListener(){

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int pos, long arg3) {
                int iChosenAudioStream = -1;


                switch (pos) {
                    case POS_STREAM_MUSIC:
                        iChosenAudioStream = AudioManager.STREAM_MUSIC;
                        break;
                    case POS_STREAM_ALARM:
                        iChosenAudioStream = AudioManager.STREAM_ALARM;
                        break;
                    case POS_STREAM_DTMF:
                        iChosenAudioStream = AudioManager.STREAM_DTMF;
                        break;
                    case POS_STREAM_NOTIFICATION:
                        iChosenAudioStream = AudioManager.STREAM_NOTIFICATION;
                        break;
                    case POS_STREAM_RING:
                        iChosenAudioStream = AudioManager.STREAM_RING;
                        break;
                    case POS_STREAM_SYSTEM:
                        iChosenAudioStream = AudioManager.STREAM_SYSTEM;
                        break;
                    case POS_STREAM_VOICE_CALL:
                        iChosenAudioStream = AudioManager.STREAM_VOICE_CALL;
                        break;
                    case POS_STREAM_DEFAULT:
                        iChosenAudioStream = AudioManager.USE_DEFAULT_STREAM_TYPE;
                        break;
                }
                try{
                    if(iChosenAudioStream != -1) {
                        mv_rServiceInterface.setAudioStream(iChosenAudioStream);
                    }

                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- remote exception thrown while setting audio stream to "+AudioHogService.resolveAudioStream(iChosenAudioStream));
                }


            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }

        });
        mAudioFocusDuration_SP = (Spinner)findViewById(R.id.widget_activity_main_duration_SP);
        mFocusAdapter = new FocusAdapter(this,R.layout.simple_spinner,this.getResources().getStringArray(R.array.audio_focus_type_array));
        mFocusAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        mAudioFocusDuration_SP.setAdapter(mFocusAdapter);


        mAudioFocusDuration_SP.setOnItemSelectedListener(new OnItemSelectedListener(){



            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int pos, long arg3) {
                int iAudioFocusDuration = -377;
                switch(pos){
                    case POS_DUR_GAIN:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN;

                        break;
                    case POS_DUR_GAIN_TRANSIENT:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
                        break;
                    case POS_DUR_GAIN_TRANSIENT_MAY_DUCK:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
                        break;
                    case POS_DUR_LOSS:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS;
                        break;
                    case POS_DUR_LOSS_TRANSIENT:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                        break;
                    case POS_DUR_LOSS_TRANSIENT_CAN_DUCK:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                        break;
                }

                try {
                    if(iAudioFocusDuration != -377) {
                        mv_rServiceInterface.setAudioFocusDuration(iAudioFocusDuration);
                    }
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- remote exception thrown while setting audio focus duration to "+AudioHogService.resolveAudioFocusState(iAudioFocusDuration));

                }


            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }

        });



        mPauseAudio_B = (Button)findViewById(R.id.widget_activity_main_pauseAudio_B);
        mPauseAudio_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                mv_rServiceInterface.pauseAudio();


            }

        });

        mStartAudio_B = (Button)findViewById(R.id.widget_activity_main_startAudio_B);
        mStartAudio_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                mv_rServiceInterface.playAudio();


            }

        });
        mRequestAudioFocus_B = (Button)findViewById(R.id.widget_activity_main_reqAudioFocus_B);
        mRequestAudioFocus_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                if(requestAudioFocus(mv_rAudioFocusListener,iAudioStreamID,mv_iAudioFocusDuration)){
                    mv_bAudioFocusHeld = true;
                }
                else{
                    Log.e(TAG,"the hog's request to take audio focus over stream "+resolveAudioStream(iAudioStreamID)+" for duration "+resolveAudioFocusState(mv_iAudioFocusDuration)+" failed!");
                }

            }

        });
        mAbandonAudioFocus_B = (Button)findViewById(R.id.widget_activity_main_abandonAudioFocus_B);
        mAbandonAudioFocus_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                if(abandonAudioFocus(mv_rAudioFocusListener)){
                    mv_bAudioFocusHeld = false;
                }
                else{
                    Log.e(TAG,"the hog's request to abandon audio focus over audio focus change listener "+mv_rAudioFocusListener+" failed!");
                }

            }

        });

    }






    private class StreamAdapter extends ArrayAdapter{

        public StreamAdapter(Context context, int resource, String[] strings) {
            super(context, resource, strings);

        }

    }
    private class FocusAdapter extends ArrayAdapter{

        public FocusAdapter(Context context, int resource, String[] strings) {
            super(context, resource, strings);

        }

    }

}
