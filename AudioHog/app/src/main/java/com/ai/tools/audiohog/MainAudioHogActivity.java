package com.ai.tools.audiohog;

import java.io.IOException;

import android.os.Handler;


import android.media.AudioManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
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
	private Button mPlayMP3_B;
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
	//private final int POS_STREAM_MTK_FM = 7;
	private final int POS_STREAM_DEFAULT = 7;//8;
	
	private final int AUDIO_STREAM_MTK_FM = 10;//the audio stream for FM radio on many MediaTek devices, at least prior to Lollipop
	

	private final int POS_DUR_GAIN = 0;
	private final int POS_DUR_GAIN_TRANSIENT = 1;
	private final int POS_DUR_GAIN_TRANSIENT_MAY_DUCK = 2;
	private final int POS_DUR_LOSS = 3;
	private final int POS_DUR_LOSS_TRANSIENT = 4;
	private final int POS_DUR_LOSS_TRANSIENT_CAN_DUCK = 5;

	private Spinner mAudioFocusDuration_SP;
    private boolean mv_bActivityDestroying = false;


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
                //TODO: exit service
                return true;
        }


        return super.onOptionsItemSelected(item);
    }

	

	
	public void initUI(){
		String versionName = "";
		try {
			versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		mAudioStream_SP = (Spinner)findViewById(R.id.widget_activity_main_stream_SP);
		mStreamAdapter = new StreamAdapter(this,R.layout.simple_spinner,this.getResources().getStringArray(R.array.audio_stream_type_array));
		mStreamAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
		mAudioStream_SP.setAdapter(mStreamAdapter);
		
		
		mAudioStream_SP.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int pos, long arg3) {
				switch(pos){
				case POS_STREAM_MUSIC:
					iAudioStreamID = AudioManager.STREAM_MUSIC;
					
					break;
				case POS_STREAM_ALARM:
					iAudioStreamID = AudioManager.STREAM_ALARM;
					
					
					break;
				case POS_STREAM_DTMF:
					iAudioStreamID = AudioManager.STREAM_DTMF;
					
					
					break;
				case POS_STREAM_NOTIFICATION:
					iAudioStreamID = AudioManager.STREAM_NOTIFICATION;
					
					
					break;
				case POS_STREAM_RING:
					iAudioStreamID = AudioManager.STREAM_RING;
					
					
					break;
				case POS_STREAM_SYSTEM:
					iAudioStreamID = AudioManager.STREAM_SYSTEM;
					
					break;
				case POS_STREAM_VOICE_CALL:
					iAudioStreamID = AudioManager.STREAM_VOICE_CALL;
					

				case POS_STREAM_DEFAULT:
					iAudioStreamID = AudioManager.USE_DEFAULT_STREAM_TYPE;
					
					break;
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
				switch(pos){
				case POS_DUR_GAIN:
					mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN;
					break;
				case POS_DUR_GAIN_TRANSIENT:
					mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
					break;
				case POS_DUR_GAIN_TRANSIENT_MAY_DUCK:
					mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
					break;
				case POS_DUR_LOSS:
					mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS;
					break;
				case POS_DUR_LOSS_TRANSIENT:
					mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
					break;
				case POS_DUR_LOSS_TRANSIENT_CAN_DUCK:
					mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
					break;
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
				pauseAudio();
				
				
				/*
				try {
					afd = mAssetManager.openFd("Bubbl1_0.mid");
					initStatelyMediaPlayer();
				} catch (IOException e1) {
					
					e1.printStackTrace();
				}
				*/
				
			}
			
		});
		
		mStartAudio_B = (Button)findViewById(R.id.widget_activity_main_startAudio_B);
		mStartAudio_B.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				playAudio();
				
				
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
