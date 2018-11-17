package com.ai.tools.audiohog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.*;
import android.os.Process;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.util.Log;
import android.widget.RemoteViews;

import java.io.IOException;

public class AudioHogService extends Service {

    public static final String TAG = "AudioHogService";


    private static final int NOTIFICATION_ID = R.string.hog_notification_id;
    private static final int NOTIFICATION_PLAYING = R.string.hog_notification_play;
    private static final int NOTIFICATION_PAUSING = R.string.hog_notification_pause;
    private static final int NOTIFICATION_TAKEN = R.string.hog_notification_take;//...But what I do have are a very particular set of skills, skills I have acquired over a very long init process.  Skills that make me a nightmare for application components like you. If you release audio focus now, that'll be the end of it.  I will not look for you, I will not pursue you.  But if you don't, I will look for you, I will find you, and I will kill you. --The OOM Killer
    private static final int NOTIFICATION_RELEASED = R.string.hog_notification_release;
    /**
     * Channel ID for the custom notification channel
     */
    private static final String NOTIFICATION_CHANNEL_ID = "AudioHog Notification Channel";

    public final String ACTION_PAUSE_AUDIO = "com.ai.tools.AudioHog.pause_audio";
    public final String ACTION_PLAY_AUDIO = "com.ai.tools.AudioHog.play_audio";
    public final String ACTION_TAKE_AUDIO_FOCUS = "com.ai.tools.AudioHog.take_audio_focus";
    public final String ACTION_RELEASE_AUDIO_FOCUS = "com.ai.tools.AudioHog.release_audio_focus";

    private HogAudioFocusChangeListener mv_rAudioFocusChangeListener = new HogAudioFocusChangeListener();

    private volatile boolean mv_bAudioFocusHeld = false;
    private AudioManager mAudioManager;
    private AssetManager mAssetManager;
    private StatelyMediaPlayer mStatelyMediaPlayer;
    private volatile int mv_iAudioStreamID = AudioManager.STREAM_ALARM;
    private volatile int mv_iAudioFocusDuration = AudioManager.AUDIOFOCUS_GAIN;
    private NotificationManagerCompat mNM;
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
        //synchronized only because I used aidl directly instead of a bound service w/ messenger for RPC/IPC
        @Override
        public synchronized void setAudioStream(int streamCode) throws RemoteException {
            modifyAudioStream(streamCode);
        }

        @Override
        public synchronized void setAudioFocusDuration(int focusDuration) throws RemoteException {
            modifyAudioFocusDuration(focusDuration);
        }

        @Override
        public void stopAudioHogService() throws RemoteException {
            stopSelf();
        }

        @Override
        public void startAudioHogService() throws RemoteException {
            // the notice argument to buildNotification is currently unused
            // (the two state machines, audio playing and focus held, are binary
            // and the mediaplayer's current state is checked to determine what
            // the icon should change to in order to indicate what commanding
            // a change will move the state value to)
            startForeground(NOTIFICATION_ID,buildNotification(NOTIFICATION_PLAYING));
        }

        @Override
        public synchronized void playAudio() throws RemoteException {
            playInterferingAudio();
        }

        @Override
        public synchronized void pauseAudio() throws RemoteException {
            pauseInterferingAudio();
        }

        @Override
        public synchronized boolean takeAudioFocus() throws RemoteException {
            boolean bRes = requestAudioFocus(mv_rAudioFocusChangeListener, mv_iAudioStreamID,mv_iAudioFocusDuration);
            if(bRes) {
                mv_bAudioFocusHeld = true;
            }
            else{
                Log.e(TAG, "the hog's request to take audio focus over stream " + resolveAudioStream(mv_iAudioStreamID) + " for duration " + resolveAudioFocusState(mv_iAudioFocusDuration) + " failed!");
            }
            return bRes;
        }

        @Override
        public synchronized boolean releaseAudioFocus() throws RemoteException {
            boolean bRes = abandonAudioFocus(mv_rAudioFocusChangeListener);
            if(bRes) {
                mv_bAudioFocusHeld = false;
            }
            else{
                Log.e(TAG,"the hog's request to abandon audio focus over audio focus change listener "+mv_rAudioFocusChangeListener+" failed!");
            }
            return bRes;
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
        registerReceiver(mTakeReleaseFocusReceiver, takeReleaseFocusFilter);
        // todo: using notifmancompat did not fix the insanity alerts problem
        mNM = NotificationManagerCompat.from(this);
        replaceNotification(NOTIFICATION_PLAYING);
        Log.d(TAG,"audio hog service -- in onCreate");

    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        //mNM.cancel(NOTIFICATION_ID);

        if(mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()){
            if(mStatelyMediaPlayer.isInStarted()){
                mStatelyMediaPlayer.pause();
            }
            mStatelyMediaPlayer.stop();
        }

        mStatelyMediaPlayer.release();
        unregisterReceiver(mPlayPauseReceiver);
        unregisterReceiver(mTakeReleaseFocusReceiver);
        abandonAudioFocus(mv_rAudioFocusChangeListener);

        //cancel the notif last because abandonAudioFocus above
        //will call updateNotif
        Log.d(TAG,"audio hog service -- in onDestroy; about to cancel the notif");
        mNM.cancel(NOTIFICATION_ID);
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
        mv_iAudioStreamID = newStream;

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
            try{
                Log.i(TAG,"audio hog -- initStatelyMediaPlayer; asset file descriptor returned for winter.mp3 is "+afd);
                mStatelyMediaPlayer.setDataSource(afd.getFileDescriptor());


                Log.i(TAG, "initStatelyMediaPlayer() called with mv_iAudioStreamID of " + mv_iAudioStreamID);

                //int audioReqState = mAudioManager.requestAudioFocus(this, mv_iAudioStreamID, AudioManager.AUDIOFOCUS_GAIN);
                //if(audioReqState == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                //Log.i(TAG, "audio focus was granted to the hog");
                mStatelyMediaPlayer.setAudioStream(mv_iAudioStreamID);
                try {
                    mStatelyMediaPlayer.prepare();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "audio hog -- illegalstate ex thrown while preparing the stately mediaplayer", e);

                } catch (IOException e) {
                    Log.e(TAG, "audio hog -- io ex thrown while preparing the stately mediaplayer", e);
                }
                mStatelyMediaPlayer.setLooping(true);
                Log.d(TAG,"audio successfully acquired, initialized, and prepared");

            }catch(IOException e){
                Log.e(TAG,"audio hog -- io exception thrown while trying to set mediaplayer data source",e);
            } catch (IllegalArgumentException e1) {
                Log.e(TAG,"audio hog -- illegalargument ex thrown while trying to set mediaplayer data source",e1);
            } catch (IllegalStateException e1) {
                Log.e(TAG, "audio hog -- illegalstate ex thrown while trying to set mediaplayer data source", e1);

            }
        }catch(IOException e){
            Log.e(TAG,"audio hog -- io ex thrown while initializing the stately mediaplayer. failed to open fd to audio asset",e);
        }

    }

    /**
     * Starts playing audio from the audio asset file descriptor, initializing the mediaplayer if necessary
     *
     *
     */
    public boolean playInterferingAudio(){
        boolean bSuccess = false;
        if(!mStatelyMediaPlayer.isReady()){//mediaplayer needs to be initialized
            try {
                AssetFileDescriptor afd = mAssetManager.openFd("winter.mp3");
                initStatelyMediaPlayer();
                mStatelyMediaPlayer.start();
                Log.d(TAG, "audio should be playing now");

                processNotificationAction(NOTIFICATION_PLAYING);
                bSuccess = true;
            } catch (IOException e1) {

                Log.e(TAG,"audio hog -- in playInterferingAudio; io ex thrown",e1);
            } catch(IllegalStateException e){
                Log.e(TAG,"audio hog -- in playInterferingAudio; illstate ex thrown by start()",e);
            }
        }
        else{//mediaplayer is already init
            try{
                mStatelyMediaPlayer.start();
                Log.d(TAG, "audio should be playing now");

                processNotificationAction(NOTIFICATION_PLAYING);
                bSuccess = true;

            } catch(IllegalStateException e){
                Log.e(TAG,"audio hog -- in playInterferingAudio; illstate ex thrown by start()",e);
            }
        }


        return bSuccess;

    }
    /**
     * Pauses playback of audio from the audio asset file descriptor
     *
     *
     */
    public boolean pauseInterferingAudio(){
        boolean bSuccess = false;
        try {
            mStatelyMediaPlayer.pause();
            processNotificationAction(NOTIFICATION_PAUSING);
            bSuccess = true;
        }catch(IllegalStateException e){
            Log.e(TAG,"audio hog -- in pauseInterferingAudio; illstateex thrown while trying to call statelymediaplayer::pause",e);
        }

        return bSuccess;

    }


    //Notification stuff
    private BroadcastReceiver mPlayPauseReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "playpause rec -- received a play/pause command from notif");
            if(intent.getAction().equals(ACTION_PLAY_AUDIO)){
                Log.v(TAG, "playpause rec -- received a play command from notif");
                playInterferingAudio();

                /*//this is taken care of in playInterferingAudio
                replaceNotification(NOTIFICATION_PAUSING);
                */
            }
            else if(intent.getAction().equals(ACTION_PAUSE_AUDIO)){
                Log.v(TAG, "playpause rec -- received a pause command from notif");
                pauseInterferingAudio();
                /*//this is taken care of in pauseInterferingAudio
                replaceNotification(NOTIFICATION_PLAYING);
                */
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
                /*//this is taken care of in abandonAudioFocus above
                replaceNotification(NOTIFICATION_RELEASED);
                */
            }
            else if(intent.getAction().equals(ACTION_TAKE_AUDIO_FOCUS)){
                Log.v(TAG, "take release rec -- received a take command from notif");
                if(requestAudioFocus(mv_rAudioFocusChangeListener, mv_iAudioStreamID,mv_iAudioFocusDuration)){
                    mv_bAudioFocusHeld = true;
                }
                else{
                    Log.e(TAG,"the hog's request to take audio focus over stream "+resolveAudioStream(mv_iAudioStreamID)+" for duration "+resolveAudioFocusState(mv_iAudioFocusDuration)+" failed!");
                }
                /*//this is taken care of in requestAudioFocus above
                replaceNotification(NOTIFICATION_TAKEN);
                */
            }

        }

    };
    private void replaceNotification(int notice) {


        Notification notification = buildNotification(notice);

        // Send the newly created notification --
        // this will be accompanied by an alert!
        mNM.notify(NOTIFICATION_ID, notification);


    }
    private void processNotificationAction(int notice){
        /*
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG,"processNotificationAction; platform API level is M+");
            // grab a handle to the current notif object
            // and configure it
            Notification notification = mNM.getActiveNotifications()[0].getNotification();
            configureNotification(notification);
            // todo: hrm... apparently calling notify triggers the annoying alert
            // even if we re-use the notification object.  So how does one
            // update a notification without driving a user insane on Pie?
            // Check out other music playing apps to see if they display
            // play/pause buttons in their notification on Pie
            mNM.notify(NOTIFICATION_ID,notification);
        }else{
            // on older platforms the thing to do for updating notifications
            // seems to have been simply tossing a new one up
            replaceNotification(notice);
        }
        */

        replaceNotification(notice);
    }
    /**
     * Sets and updates the properties of the given notification for
     * Audio Hog actions
     * @param notification the existing notification object whose properties should be set/updated
     */
    private void configureNotification(Notification notification){
        // Set the expanded content
        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_standard);

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

        PendingIntent launchMainActivityIntent = PendingIntent.getActivity(
                this,
                0,
                Intent.makeMainActivity(new ComponentName("com.ai.tools.audiohog","MainAudioHogActivity")),
                0);
        notification.contentIntent = launchMainActivityIntent;
		/*
		// The PendingIntent to launch our activity if the user selects this notification itself
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainAudioHogActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);
		*/
        //notification.contentIntent = playPauseIntent;//contentIntent;
        notification.flags = Notification.FLAG_ONGOING_EVENT;
    }
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
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

        //create the custom notification channel iff needed
        createNotificationChannel();

        // Sets an ID for the notification, so it can be updated
        // Only create one builder, which seems to be important for
        // avoiding repeat alerts
        if(mNotifyBuilder == null) {
            mNotifyBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    //.setContentTitle(text)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
            ;
        }
        // todo: using the same builder instance did not prevent repeat alerts
        Log.d(TAG,"buildNotification; builder is "+mNotifyBuilder);

        // Start of a loop that processes data and then notifies the user
        mNotifyBuilder.setContentText(text);
        // Because the ID remains unchanged, the existing notification is
        // updated.
        Notification notification = mNotifyBuilder.build();//.getNotification();//.build();
        configureNotification(notification);
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
        Log.i(TAG,"audio focus request results in "+Util.resolveAudioFocusRequestResult(iRet));
        if(iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mv_bAudioFocusHeld = true;
            processNotificationAction(NOTIFICATION_TAKEN);
        }
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    /**
     * Abandons audio focus and updates the take/release state of the notif
     * @return true if abandoned successfully (request_granted), false otherwise
     */
    public boolean abandonAudioFocus(AudioManager.OnAudioFocusChangeListener listener){
        int iRet = mAudioManager.abandonAudioFocus(listener);
        Log.d(TAG,"audio focus abandon results in "+Util.resolveAudioFocusRequestResult(iRet));
        if(iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mv_bAudioFocusHeld = false;
            processNotificationAction(NOTIFICATION_RELEASED);
        }
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    //TODO: update the focus held state and notifications as necessary for all focus state changes
    private class HogAudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener{

        @Override
        public void onAudioFocusChange(int focusChange) {

            Log.i(TAG, "The audio hog service has just received an onAudioFocusChange callback, with focus state " + resolveAudioFocusState(focusChange));

            switch(focusChange){
                case AudioManager.AUDIOFOCUS_GAIN:{

                    Log.i(TAG, "audio focus gained");
                    mv_bAudioFocusHeld = true;
                    processNotificationAction(NOTIFICATION_TAKEN);

                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:{

                    Log.i(TAG, "audio focus gained transiently");
                    mv_bAudioFocusHeld = true;
                    processNotificationAction(NOTIFICATION_TAKEN);

                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:{

                    Log.i(TAG, "audio focus gained transiently but also exclusively");
                    mv_bAudioFocusHeld = true;
                    processNotificationAction(NOTIFICATION_TAKEN);


                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:{

                    Log.i(TAG, "audio focus gained transiently, you may have a duck");
                    mv_bAudioFocusHeld = true;
                    processNotificationAction(NOTIFICATION_TAKEN);


                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS:{
                    Log.i(TAG, "audio focus lost");
                    if(mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()){
                        try {
                            if (mStatelyMediaPlayer.isInStarted()) {
                                mStatelyMediaPlayer.pause();
                            }
                            mStatelyMediaPlayer.stop();

                        }catch(IllegalStateException e){
                            Log.e(TAG,"audio hog -- illegal state ex thrown while pausing/stopping the mediaplayer in response to audio focus loss",e);
                        }
                    }

                    mv_bAudioFocusHeld = false;
                    processNotificationAction(NOTIFICATION_RELEASED);

                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:{
                    Log.i(TAG, "audio focus lost transiently");

                    mv_bAudioFocusHeld = false;
                    processNotificationAction(NOTIFICATION_RELEASED);

                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:{
                    Log.i(TAG, "audio focus lost transiently, and you can have a duck");

                    mv_bAudioFocusHeld = false;
                    processNotificationAction(NOTIFICATION_RELEASED);

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
