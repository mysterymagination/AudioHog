package com.ai.tools.audiohog;

import android.content.ComponentName;
import android.content.ServiceConnection;


import android.media.AudioManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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


    private final int POS_DUR_GAIN = 0;//causes LOSS to be sent to interrupted focus holder
    private final int POS_DUR_GAIN_TRANSIENT = 1;//causes LOSS_TRANSIENT to be sent to interrupted focus holder
    private final int POS_DUR_GAIN_TRANSIENT_MAY_DUCK = 2;//causes LOSS_TRANSIENT_CAN_DUCK to be sent to interrupted focus holder
    private final int POS_DUR_GAIN_TRANSIENT_EXCLUSIVE = 3;//causes LOSS_TRANSIENT to be sent to interrupted focus holder
    /*//the losses are served to listeners by the system in response to gains; you can't request a LOSS* duration
    private final int POS_DUR_LOSS = 4;
    private final int POS_DUR_LOSS_TRANSIENT = 5;
    private final int POS_DUR_LOSS_TRANSIENT_CAN_DUCK = 6;
    */

    private Spinner mAudioFocusDuration_SP;
    private boolean mv_bActivityDestroying = false;

    //audiohog service handle



    private FocusAdapter mFocusAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_audio_hog);
        initUI();

        //bind to service
        Intent serviceIntent = new Intent(this,AudioHogService.class);
        startService(serviceIntent);//start it so that it can persist if the activity unbinds
        bindService(serviceIntent,mv_rServiceConnection,Context.BIND_AUTO_CREATE);

        //since the menu tries to init part of the service, we need to only set it up after the
        //service is bound
        //initUI();

    }//end onCreate()

    @Override
    protected void onDestroy(){
        Log.d(TAG, "take release -- onDestroy()");
        super.onDestroy();
        mv_bActivityDestroying = true;
        // unbind the service (it will persist if it has not been stopped via serviceinterface::stopAudioHogService())
        unbindService(mv_rServiceConnection);

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
            case R.id.action_dismiss_activity:
                finish();
                return true;
            case R.id.action_stop_service:
                try {
                    mv_rServiceInterface.stopAudioHogService();
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- attempting to stopAudioHogService the service threw remote ex",e);
                }
                return true;
            case R.id.action_start_service:
                try {
                    mv_rServiceInterface.startAudioHogService();
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- attempting to startAudioHogService the service threw remote ex",e);
                }
                return true;
            case R.id.action_exit:
                // 1. stop the service
                try {
                    mv_rServiceInterface.stopAudioHogService();
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- attempting to stopAudioHogService the service threw remote ex",e);
                }
                // 2. close activity, which also unbinds from service
                finish();
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
        mAudioStream_SP.setSelection(0, false);//keeps the implicit onItemSelected call from occurring when the onitemselectedlistener is added prior to layout.  See http://stackoverflow.com/questions/2562248/how-to-keep-onitemselected-from-firing-off-on-a-newly-instantiated-spinner#answer-17336944


        mAudioStream_SP.setOnItemSelectedListener(new OnItemSelectedListener() {

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
                try {
                    if (iChosenAudioStream != -1) {
                        if(mv_rServiceInterface != null){
                            mv_rServiceInterface.setAudioStream(iChosenAudioStream);
                        }
                    }

                } catch (RemoteException e) {
                    Log.e(TAG, "audio hog -- remote exception thrown while setting audio stream to " + AudioHogService.resolveAudioStream(iChosenAudioStream));
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
        mAudioFocusDuration_SP.setSelection(0, false);//keeps the implicit onItemSelected call from occurring when the onitemselectedlistener is added prior to layout. See http://stackoverflow.com/questions/2562248/how-to-keep-onitemselected-from-firing-off-on-a-newly-instantiated-spinner#answer-17336944


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
                    case POS_DUR_GAIN_TRANSIENT_EXCLUSIVE:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
                        break;
                    /*//no reason to include the loss states on the user-control side
                    case POS_DUR_LOSS:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS;
                        break;
                    case POS_DUR_LOSS_TRANSIENT:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                        break;
                    case POS_DUR_LOSS_TRANSIENT_CAN_DUCK:
                        iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                        break;
                    */
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
                try {
                    mv_rServiceInterface.pauseAudio();
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- remote exception thrown while pausing audio",e);
                }


            }

        });

        mStartAudio_B = (Button)findViewById(R.id.widget_activity_main_startAudio_B);
        mStartAudio_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                try{
                    mv_rServiceInterface.playAudio();
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- remote exception thrown while playing audio",e);
                }


            }

        });
        mRequestAudioFocus_B = (Button)findViewById(R.id.widget_activity_main_reqAudioFocus_B);
        mRequestAudioFocus_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                try {
                    boolean bRes = mv_rServiceInterface.takeAudioFocus();
                    if (!bRes) {

                        Log.e(TAG, "the hog's request to take audio focus failed!");
                    }
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- in requestaudiofocus onclick; remote ex thrown while trying to take audio focus",e);
                }

            }

        });
        mAbandonAudioFocus_B = (Button)findViewById(R.id.widget_activity_main_abandonAudioFocus_B);
        mAbandonAudioFocus_B.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                try {
                    boolean bRes = mv_rServiceInterface.releaseAudioFocus();
                    if (!bRes) {

                        Log.e(TAG, "the hog's request to release audio focus failed!");
                    }
                }catch(RemoteException e){
                    Log.e(TAG,"audio hog -- in abandonaudiofocus onclick; remote ex thrown while trying to release audio focus",e);
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
