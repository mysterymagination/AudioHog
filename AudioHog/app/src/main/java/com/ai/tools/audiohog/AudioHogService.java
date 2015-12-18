package com.ai.tools.audiohog;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.*;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.IOException;

public class AudioHogService extends Service {

    public static final String TAG = "AudioHogService";


    private static final int NOTIFICATION = R.string.hog_notification;
    private static final int NOTIFICATION_PLAY = R.string.hog_notification_play;
    private static final int NOTIFICATION_PAUSE = R.string.hog_notification_pause;
    private static final int NOTIFICATION_TAKE = R.string.hog_notification_take;
    private static final int NOTIFICATION_RELEASE = R.string.hog_notification_release;

    public final String ACTION_PAUSE_AUDIO = "com.ai.tools.AudioHog.pause_audio";
    public final String ACTION_PLAY_AUDIO = "com.ai.tools.AudioHog.play_audio";
    public final String ACTION_TAKE_AUDIO_FOCUS = "com.ai.tools.AudioHog.take_audio_focus";
    public final String ACTION_RELEASE_AUDIO_FOCUS = "com.ai.tools.AudioHog.release_audio_focus";

    private HogAudioFocusChangeListener mv_rAudioFocusChangeListener = new HogAudioFocusChangeListener();

    private boolean mv_bAudioFocusHeld = false;
    private AudioManager mAudioManager;
    private AssetManager mAssetManager;
    private StatelyMediaPlayer mStatelyMediaPlayer;
    private int iAudioStreamID = AudioManager.STREAM_ALARM;
    private int mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN;
    private NotificationManager mNM;
    private NotificationCompat.Builder mNotifyBuilder;
    private IAudioHog.Stub mv_rStub = new IAudioHog.Stub(){

        @Override
        public int getPid() throws RemoteException {
            return Process.myPid();
        }

        @Override
        public int getUid() throws RemoteException {
            return Process.myUid();
        }

        @Override
        public void setAudioStream(int streamCode) throws RemoteException {
            modifyAudioStream(streamCode);
        }

        @Override
        public void setAudioFocusDuration(int focusDuration) throws RemoteException {
            modifyAudioFocusDuration(focusDuration);
        }


    };



    public AudioHogService() {
    }

    @Override
    public void onCreate(){
        super.onCreate();

        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mStatelyMediaPlayer = new StatelyMediaPlayer();
        mAssetManager = this.getAssets();
        IntentFilter playPauseFilter = new IntentFilter();
        playPauseFilter.addAction(ACTION_PLAY_AUDIO);
        playPauseFilter.addAction(ACTION_PAUSE_AUDIO);
        registerReceiver(mPlayPauseReceiver,playPauseFilter);
        IntentFilter takeReleaseFocusFilter = new IntentFilter();
        takeReleaseFocusFilter.addAction(ACTION_RELEASE_AUDIO_FOCUS);
        takeReleaseFocusFilter.addAction(ACTION_TAKE_AUDIO_FOCUS);
        registerReceiver(mTakeReleaseFocusReceiver,takeReleaseFocusFilter);
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        updateNotification(NOTIFICATION_PLAY);

    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        mNM.cancel(NOTIFICATION);

        if(mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()){
            if(mStatelyMediaPlayer.isInStarted()){
                mStatelyMediaPlayer.pause();
            }
            mStatelyMediaPlayer.stop();
        }

        mStatelyMediaPlayer.release();
        unregisterReceiver(mPlayPauseReceiver);
        abandonAudioFocus(mv_rAudioFocusChangeListener);
    }


    //TODO: in addition to being a useful way to provide audio interruptions with another
    //app totally in the foreground, if we have a service manage the ongoing notif we
    //can handle the recent tasks swipe-out thingy gracefully (currently the notif sticks
    // around forever -- not abundantly clear why)


    @Override
    public IBinder onBind(Intent intent) {
        // TODOx: Return the communication channel to the service.
        return mv_rStub;
    }


    //stately mediaplayer stuff
    public void modifyAudioStream(int newStream){
        iAudioStreamID = newStream;

        //stop the player if already past prepared state
        if(mStatelyMediaPlayer.isReady()){
            mStatelyMediaPlayer.stop();
        }

        //in order for the given stream to be in effect, must be set prior to the current
        //mediaplayer's prepare/prepareAsync calls
        initStatelyMediaPlayer();
    }
    public void modifyAudioFocusDuration(int duration){
        mv_iAudioFocusDuration = duration;
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
                Log.e(TAG, "audio hog -- illegalstate ex thrown while preparing the stately mediaplayer", e);

            } catch (IOException e) {
                Log.e(TAG, "audio hog -- io ex thrown while preparing the stately mediaplayer", e);

            }
            mStatelyMediaPlayer.setLooping(true);
            Log.d(TAG,"audio successfully prepared");
        } catch (IllegalArgumentException e1) {
            Log.e(TAG,"audio hog -- illegalargument ex thrown while initializing the stately mediaplayer",e1);
        } catch (IllegalStateException e1) {
            Log.e(TAG, "audio hog -- illegalstate ex thrown while initializing the stately mediaplayer", e1);

        } catch (IOException e1) {
            Log.e(TAG, "audio hog -- io ex thrown while initializing the stately mediaplayer", e1);

        }




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


    //Notification stuff
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
    private BroadcastReceiver mTakeReleaseFocusReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "take release rec -- received a take/release command from notif");
            if(intent.getAction().equals(ACTION_RELEASE_AUDIO_FOCUS)){
                Log.v(TAG, "take release rec -- received a release command from notif");
                if(abandonAudioFocus(mv_rAudioFocusChangeListener)){
                    mv_bAudioFocusHeld = false;
                }
                else{
                    Log.e(TAG,"the hog's request to abandon audio focus over audio focus change listener "+mv_rAudioFocusChangeListener+" failed!");
                }
                updateNotification(NOTIFICATION_RELEASE);
            }
            else if(intent.getAction().equals(ACTION_TAKE_AUDIO_FOCUS)){
                Log.v(TAG, "take release rec -- received a take command from notif");
                if(requestAudioFocus(mv_rAudioFocusChangeListener,iAudioStreamID,mv_iAudioFocusDuration)){
                    mv_bAudioFocusHeld = true;
                }
                else{
                    Log.e(TAG,"the hog's request to take audio focus over stream "+resolveAudioStream(iAudioStreamID)+" for duration "+resolveAudioFocusState(mv_iAudioFocusDuration)+" failed!");
                }
                updateNotification(NOTIFICATION_TAKE);
            }

        }

    };
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
        //contentView.setTextViewText(R.id.notif_txt_line1, text);


        // Set a pending intent for the playpause button
        Intent playPauseIntent = new Intent(ACTION_PAUSE_AUDIO);

        //TODOx: uncomment when remoteserviceex is resolved
        //UPDATE: switching from button to imagebutton seems to have done the trick... not clear why
        if(mStatelyMediaPlayer.isInStarted()){
            Log.v(TAG, "playpause rec -- in buildNotification; mStatelyMediaPlayer.isInStarted() is true, so setting playpause to pause gfx");
            playPauseIntent.setAction(ACTION_PAUSE_AUDIO);
            contentView.setImageViewResource(R.id.notif_btn_playpause, R.drawable.ic_media_pause);
        }
        else{
            Log.v(TAG, "playpause rec -- in buildNotification; mStatelyMediaPlayer.isInStarted() is false, so setting playpause to play gfx");

            playPauseIntent.setAction(ACTION_PLAY_AUDIO);
            contentView.setImageViewResource(R.id.notif_btn_playpause, R.drawable.ic_media_play);
        }

        // Set a pending intent for the take/release audio focus button
        Intent takeReleaseFocusIntent = new Intent(ACTION_RELEASE_AUDIO_FOCUS);
        if(mv_bAudioFocusHeld){
            Log.v(TAG, "take release focus -- in buildNotification; focus is held, so setting take/release to release gfx");
            takeReleaseFocusIntent.setAction(ACTION_RELEASE_AUDIO_FOCUS);
            contentView.setImageViewResource(R.id.notif_btn_take_rel_focus, R.drawable.open);
        }
        else{
            Log.v(TAG, "take release focus -- in buildNotification; focus is not held, so setting take/release to take gfx");
            takeReleaseFocusIntent.setAction(ACTION_TAKE_AUDIO_FOCUS);
            contentView.setImageViewResource(R.id.notif_btn_take_rel_focus, R.drawable.closed);
        }


        // attach the expanded content view to the notification
        notification.contentView = contentView;


        PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent takeReleasePendingIntent = PendingIntent.getBroadcast(this, 0, takeReleaseFocusIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        contentView.setOnClickPendingIntent(R.id.notif_btn_playpause, playPausePendingIntent);
        contentView.setOnClickPendingIntent(R.id.notif_btn_take_rel_focus, takeReleasePendingIntent);
		/*
		// The PendingIntent to launch our activity if the user selects this notification itself
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainAudioHogActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);
		*/
        //notification.contentIntent = playPauseIntent;//contentIntent;
        notification.flags = Notification.FLAG_ONGOING_EVENT;


        return notification;

    }

    /**
     * Requests audio focus for AudioHogg over the given stream for duration durationType
     * @param stream -- the audio stream to req focus over
     * @param durationType -- the duration hint to pass to audiomanager
     * @return true if req granted, false otherwise
     */
    public boolean requestAudioFocus(AudioManager.OnAudioFocusChangeListener listener,int stream,int durationType){
        int iRet = mAudioManager.requestAudioFocus(listener, stream, durationType);
        Log.d(TAG,"audio focus request results in "+Util.resolveAudioFocusRequestResult(iRet));
        if(iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mv_bAudioFocusHeld = true;
            updateNotification(NOTIFICATION_TAKE);
        }
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    /**
     * Abandons audio focus
     * @return true if abandoned successfully (request_granted), false otherwise
     */
    public boolean abandonAudioFocus(AudioManager.OnAudioFocusChangeListener listener){
        int iRet = mAudioManager.abandonAudioFocus(listener);
        Log.d(TAG,"audio focus abandon results in "+Util.resolveAudioFocusRequestResult(iRet));
        if(iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mv_bAudioFocusHeld = false;
            updateNotification(NOTIFICATION_RELEASE);
        }
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }


    private class HogAudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener{

        @Override
        public void onAudioFocusChange(int focusChange) {

            Log.i(TAG,"The audio hog service has just received an onAudioFocusChange callback, with focus state "+resolveAudioFocusState(focusChange));

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
    }

    /**
     * Resolves the given integer audiofocus state into a human readable string which
     * will give the focus type (gain vs. loss) and focus duration (nothing, which implies
     * forever, transient, or transient may/can duck)
     * @param focus
     * @return
     */
    public static String resolveAudioFocusState(int focus){
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
                return "unknown audio focus state "+focus;
        }
    }
    public static String resolveAudioFocusRequestResult(int result){
        switch(result){
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                return "AUDIOFOCUS_REQUEST_GRANTED";
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                return "AUDIOFOCUS_REQUEST_FAILED";

            default:
                return "unknown audio focus request result code "+result;
        }
    }
    public static String resolveAudioStream(int stream){
        switch(stream){
            case AudioManager.STREAM_ALARM:
                return "STREAM_ALARM";
            case AudioManager.STREAM_DTMF:
                return "STREAM_DTMF";
            case AudioManager.STREAM_MUSIC:
                return "STREAM_MUSIC";
            case AudioManager.STREAM_RING:
                return "STREAM_RING";
            case AudioManager.STREAM_NOTIFICATION:
                return "STREAM_NOTIFICATION";
            case AudioManager.STREAM_SYSTEM:
                return "STREAM_SYSTEM";
            case AudioManager.STREAM_VOICE_CALL:
                return "STREAM_VOICE_CALL";
            case AudioManager.USE_DEFAULT_STREAM_TYPE:
                return "USE_DEFAULT_STREAM_TYPE";
            default:
                return "unknown stream "+stream;
        }
    }


}
