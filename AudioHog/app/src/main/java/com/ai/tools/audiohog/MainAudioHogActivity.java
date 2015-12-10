package com.ai.tools.audiohog;

import java.io.IOException;
import java.util.List;
import android.os.Handler;
import java.util.logging.LogRecord;


import android.media.AudioManager;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainAudioHogActivity extends Activity implements AudioManager.OnAudioFocusChangeListener{

	private static final String TAG = "AudioHog";

	private static final int NOTIFICATION = R.string.hog_notification;
	private static final int NOTIFICATION_PLAY = R.string.hog_notification_play;
	private static final int NOTIFICATION_PAUSE = R.string.hog_notification_pause;
	
	private AudioManager mAudioManager;
	private AssetManager mAssetManager;
	private StatelyMediaPlayer mStatelyMediaPlayer;
	private int iAudioStreamID = AudioManager.STREAM_ALARM;
	private int mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN;
	//private AssetFileDescriptor afd = null;
	private AudioManager.OnAudioFocusChangeListener mv_rAudioFocusListener;
	private Analytics mv_rAnalytics;
	private NotificationManager mNM;
	private NotificationCompat.Builder mNotifyBuilder;
	
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
	
	public final String ACTION_PAUSE_AUDIO = "com.ai.tools.AudioHog.pause_audio";
	public final String ACTION_PLAY_AUDIO = "com.ai.tools.AudioHog.play_audio";
	
	private final int POS_DUR_GAIN = 0;
	private final int POS_DUR_GAIN_TRANSIENT = 1;
	private final int POS_DUR_GAIN_TRANSIENT_MAY_DUCK = 2;
	private final int POS_DUR_LOSS = 3;
	private final int POS_DUR_LOSS_TRANSIENT = 4;
	private final int POS_DUR_LOSS_TRANSIENT_CAN_DUCK = 5;

	private Spinner mAudioFocusDuration_SP;
    private boolean mv_bDestroying = false;

	private FocusAdapter mFocusAdapter;
    private Handler mv_rHandler = new Handler();
    private Runnable mv_rAdRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if(!mv_bDestroying) {
                //fire up a new ad
                Util.insertAd(MainAudioHogActivity.this);
            /* and here comes the "trick" */
                mv_rHandler.postDelayed(this, 60000);
            }
        }
    };
	
	
	
	public static String resolveAudioFocus(int focus){
		switch(focus){
		case AudioManager.AUDIOFOCUS_GAIN:
			return "GAIN";
		case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
			return "GAIN_TRANSIENT";
		case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
			return "GAIN_TRANSIENT_MAY_DUCK";
		case AudioManager.AUDIOFOCUS_LOSS:
			return "LOSS";
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			return "LOSS_TRANSIENT";
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			return "LOSS_TRANSIENT_MAY_DUCK";
		default:
			return "unknown";
		}
	}
	/**
	 * Requests audio focus for AudioHogg over the given stream for duration durationType
	 * @param stream -- the audio stream to req focus over
	 * @param durationType -- the duration hint to pass to audiomanager
	 * @return true if req granted, false otherwise
	 */
	public boolean requestAudioFocus(AudioManager.OnAudioFocusChangeListener listener,int stream,int durationType){
		int iRet = mAudioManager.requestAudioFocus(listener, stream,durationType);
		Log.d(TAG,"audio focus request results in "+Util.resolveAudioFocusRequestResult(iRet));
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}
	/**
	 * Abandons audio focus
	 * @return true if abandoned successfully (request_granted), false otherwise
	 */
	public boolean abandonAudioFocus(AudioManager.OnAudioFocusChangeListener listener){
		int iRet = mAudioManager.abandonAudioFocus(listener);
        Log.d(TAG,"audio focus abandon results in "+Util.resolveAudioFocusRequestResult(iRet));

        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		/*
		mv_rAnalytics = new Analytics(this);
			 

		*/
		
		setContentView(R.layout.activity_main_audio_hog);
		
		initUI();
		
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mv_rAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
			
			@Override
			public void onAudioFocusChange(int focusChange) {
				Log.v(TAG, "audiofocus changed: "+resolveAudioFocus(focusChange));
				
			}
		};
		
		mStatelyMediaPlayer = new StatelyMediaPlayer();
		mAssetManager = this.getAssets();
		IntentFilter playPauseFilter = new IntentFilter();
		playPauseFilter.addAction(ACTION_PLAY_AUDIO);
		playPauseFilter.addAction(ACTION_PAUSE_AUDIO);
		registerReceiver(mPlayPauseReceiver,playPauseFilter);
		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		updateNotification(NOTIFICATION_PLAY);
		
		
		
		

		/*
		try {
			mStatelyMediaPlayer.setDataSource("/assets/Bubbl1_0.mid");
			mStatelyMediaPlayer.prepare();
			mStatelyMediaPlayer.start();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		
		Util.insertAd(this);

        //start the ad refresh loop
        mv_rHandler.postDelayed(mv_rAdRefreshRunnable, 60000);
		
	}//end onCreate()
	
	@Override
	protected void onResume(){
		super.onResume();
		
		Util.resumeAdView(this);
		
		//String apkFilePath = getApplicationInfo().sourceDir;
		//String apkNativeLibsPath = getApplicationInfo().nativeLibraryDir;
		//Log.d("AudioHog", "audiohog is running from "+apkFilePath+". Its native libs dir path is "+apkNativeLibsPath);
		
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		
		Util.pauseAdView(this);
		
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		mv_bDestroying = true;
		mNM.cancel(NOTIFICATION);
		
		if(mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()){
			if(mStatelyMediaPlayer.isInStarted()){
				mStatelyMediaPlayer.pause();
			}
			mStatelyMediaPlayer.stop();
		}
		
		mStatelyMediaPlayer.release();
		unregisterReceiver(mPlayPauseReceiver);
		
		abandonAudioFocus(mv_rAudioFocusListener);
		
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

        if(id==R.id.action_quit){
        	finish();
        	return true;
        }

        return super.onOptionsItemSelected(item);
    }
    

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch(focusChange){
		case AudioManager.AUDIOFOCUS_GAIN:{
			
			Log.i(TAG, "audio focus gained");
			
			
			break;
		}
		case AudioManager.AUDIOFOCUS_LOSS:{
			Log.i(TAG, "audio focus lost");
			if(mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()){
				if(mStatelyMediaPlayer.isInStarted()){
					mStatelyMediaPlayer.pause();
				}
				mStatelyMediaPlayer.stop();
			}
			
			break;
		}
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:{
			
			break;
		}
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:{
			
			break;
		}
		}//end switch(focusChange)
		
	}
	
	private void updateNotification(int notice) {

		
		Notification notification = buildNotification(notice);

		// Send the notification.
		mNM.notify(NOTIFICATION, notification);
		
		
	}
	


	/**
	 * Builds the service notifcation that lives in the action bar
	 *
	 * 
	 * @param notice -- the code for the particular notification mode we are displaying in
	 * @return -- the completed Notification to be displayed
	 */
	private Notification buildNotification(int notice) {
		
		
		String text = "Hog Audio";
		
		
		// Sets an ID for the notification, so it can be updated
		mNotifyBuilder = new NotificationCompat.Builder(this)
		    //.setContentTitle(text)
		    .setSmallIcon(R.mipmap.ic_launcher);
			
		// Start of a loop that processes data and then notifies the user
		mNotifyBuilder.setContentText(text);
		// Because the ID remains unchanged, the existing notification is
		// updated.
		Notification notification = mNotifyBuilder.build();//.getNotification();//.build();

		
		// Set the expanded content
		RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_standard);
		contentView.setTextViewText(R.id.notif_txt_line1, text);
		
		
		// Set a pending intent for the playpause button
		Intent intent = new Intent(ACTION_PAUSE_AUDIO);
		
		//TODOx: uncomment when remoteserviceex is resolved
		//UPDATE: switching from button to imagebutton seems to have done the trick... not clear why
		if(mStatelyMediaPlayer.isInStarted()){
			Log.v(TAG, "playpause rec -- in buildNotification; mStatelyMediaPlayer.isInStarted() is true, so setting playpause to pause gfx");
			intent.setAction(ACTION_PAUSE_AUDIO);
			contentView.setImageViewResource(R.id.notif_btn_playpause, R.drawable.ic_media_pause);
		}
		else{
			Log.v(TAG, "playpause rec -- in buildNotification; mStatelyMediaPlayer.isInStarted() is false, so setting playpause to play gfx");
			
			intent.setAction(ACTION_PLAY_AUDIO);
			contentView.setImageViewResource(R.id.notif_btn_playpause, R.drawable.ic_media_play);
		}
		
		
		// attach the expanded content view to the notification
		notification.contentView = contentView;
		
		
		PendingIntent playPauseIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		contentView.setOnClickPendingIntent(R.id.notif_btn_playpause, playPauseIntent);
		/*
		// The PendingIntent to launch our activity if the user selects this notification itself
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainAudioHogActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);
		*/
		notification.contentIntent = playPauseIntent;//contentIntent;
		notification.flags = Notification.FLAG_ONGOING_EVENT;	
		

		return notification;
		
	}
	
	

    
	
	public void initStatelyMediaPlayer(){
		
		if(mStatelyMediaPlayer.isInStarted()){
			mStatelyMediaPlayer.stop();
			mStatelyMediaPlayer.reset();
		}
		else if(mStatelyMediaPlayer.isInStopped()){
			mStatelyMediaPlayer.reset();
		}
		
		

            try {
                AssetFileDescriptor afd = mAssetManager.openFd("winter.mp3");
                mStatelyMediaPlayer.setDataSource(afd.getFileDescriptor());
                Log.i(TAG, "initStatelyMediaPlayer() called with iAudioStreamID of " + iAudioStreamID);

                //int audioReqState = mAudioManager.requestAudioFocus(this, iAudioStreamID, AudioManager.AUDIOFOCUS_GAIN);
                //if(audioReqState == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                Log.i(TAG, "audio focus was granted to the hog");
                mStatelyMediaPlayer.setAudioStream(iAudioStreamID);
                try {
                    mStatelyMediaPlayer.prepare();
                } catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                mStatelyMediaPlayer.setLooping(true);
				Log.d(TAG,"audio successfully prepared");
            } catch (IllegalArgumentException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (IllegalStateException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            /*
            Log.i(TAG, "initStatelyMediaPlayer() called with iAudioStreamID of " + iAudioStreamID);

            //int audioReqState = mAudioManager.requestAudioFocus(this, iAudioStreamID, AudioManager.AUDIOFOCUS_GAIN);
            //if(audioReqState == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            Log.i(TAG, "audio focus was granted to the hog");
            mStatelyMediaPlayer.setAudioStream(iAudioStreamID);
            try {
                mStatelyMediaPlayer.prepare();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mStatelyMediaPlayer.setLooping(true);
            //mStatelyMediaPlayer.start();
            //}
            //else{
            //Log.i(TAG, "audio focus was denied to the hog");

            //}
            */


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
				
				//stop the player if already past prepared state
				if(mStatelyMediaPlayer.isReady()){
					mStatelyMediaPlayer.stop();
				}
				
				//in order for the given stream to be in effect, must be set prior to the current
				//mediaplayer's prepare/prepareAsync calls
				initStatelyMediaPlayer();
				
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
				requestAudioFocus(mv_rAudioFocusListener,iAudioStreamID,mv_iAudioFocusDuration);
				
			}
			
		});
		mAbandonAudioFocus_B = (Button)findViewById(R.id.widget_activity_main_abandonAudioFocus_B);
		mAbandonAudioFocus_B.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				abandonAudioFocus(mv_rAudioFocusListener);
				
			}
			
		});
		
	}

    /**
     * Starts playing audio from the audio asset file descriptor
     */
	public void playAudio(){
		if(!mStatelyMediaPlayer.isReady()){
			try {
				AssetFileDescriptor afd = mAssetManager.openFd("winter.mp3");
				initStatelyMediaPlayer();
			} catch (IOException e1) {

				e1.printStackTrace();
			}
		}
		
		mStatelyMediaPlayer.start();
        Log.d(TAG,"audio should be playing now");
		
		updateNotification(NOTIFICATION_PLAY);
		
	}
	public void pauseAudio(){
		mStatelyMediaPlayer.pause();
		updateNotification(NOTIFICATION_PAUSE);
	}
	
	private BroadcastReceiver mPlayPauseReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "playpause rec -- received a play/pause command from notif");
			if(intent.getAction().equals(ACTION_PLAY_AUDIO)){
				Log.v(TAG, "playpause rec -- received a play command from notif");
				playAudio();
				updateNotification(NOTIFICATION_PAUSE);
			}
			else if(intent.getAction().equals(ACTION_PAUSE_AUDIO)){
				Log.v(TAG, "playpause rec -- received a pause command from notif");
				pauseAudio();
				updateNotification(NOTIFICATION_PLAY);
			}
			
		}
		
	};
	
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
