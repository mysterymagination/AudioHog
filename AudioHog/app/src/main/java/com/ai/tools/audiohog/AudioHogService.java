package com.ai.tools.audiohog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.*;
import android.os.Process;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


import android.widget.RemoteViews;

import java.io.IOException;

import timber.log.Timber;

public class AudioHogService extends Service {

    public static final String TAG = "AudioHogService";

    private static final String NOTIFICATION_TAG = "AudioHog Notif Tag";
    private static final int NOTIFICATION_ID = 1001;
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
    private boolean mIsDestroyed = false;

    private IAudioHog.Stub mv_rStub = new IAudioHog.Stub() {
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
                Timber.e("the hog's request to take audio focus over stream " + resolveAudioStream(mv_iAudioStreamID) + " for duration " + resolveAudioFocusState(mv_iAudioFocusDuration) + " failed!");
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
                Timber.e("the hog's request to abandon audio focus over audio focus change listener "+mv_rAudioFocusChangeListener+" failed!");
            }
            return bRes;
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mStatelyMediaPlayer = new StatelyMediaPlayer();
        mAssetManager = this.getAssets();
        IntentFilter playPauseFilter = new IntentFilter();
        playPauseFilter.addAction(ACTION_PLAY_AUDIO);
        playPauseFilter.addAction(ACTION_PAUSE_AUDIO);
        registerReceiver(mPlayPauseReceiver, playPauseFilter);
        IntentFilter takeReleaseFocusFilter = new IntentFilter();
        takeReleaseFocusFilter.addAction(ACTION_RELEASE_AUDIO_FOCUS);
        takeReleaseFocusFilter.addAction(ACTION_TAKE_AUDIO_FOCUS);
        registerReceiver(mTakeReleaseFocusReceiver, takeReleaseFocusFilter);
        mNM = NotificationManagerCompat.from(this);
        mNotifyBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        startForeground(NOTIFICATION_ID, buildNotification(NOTIFICATION_PLAYING));
        Timber.d("audio hog service -- in onCreate");

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsDestroyed = true;
        if (mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()) {
            if (mStatelyMediaPlayer.isInStarted()) {
                mStatelyMediaPlayer.pause();
            }
            mStatelyMediaPlayer.stop();
        }

        mStatelyMediaPlayer.release();
        unregisterReceiver(mPlayPauseReceiver);
        unregisterReceiver(mTakeReleaseFocusReceiver);
        abandonAudioFocus(mv_rAudioFocusChangeListener);

        // cancel the notif last because abandonAudioFocus above
        // will call updateNotif
        Timber.d("audio hog service -- in onDestroy; about to cancel the notif");
        mNM.cancel(NOTIFICATION_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mv_rStub;
    }


    //stately mediaplayer stuff
    public void modifyAudioStream(int newStream) {
        mv_iAudioStreamID = newStream;

        //stop the player if already past prepared state
        if (mStatelyMediaPlayer.isReady()) {
            mStatelyMediaPlayer.stop();
        }

        //in order for the given stream to be in effect, must be set prior to the current
        //mediaplayer's prepare/prepareAsync calls
        initStatelyMediaPlayer();
    }

    public void modifyAudioFocusDuration(int duration) {
        mv_iAudioFocusDuration = duration;
    }

    public void initStatelyMediaPlayer() {

        if (mStatelyMediaPlayer.isInStarted()) {
            mStatelyMediaPlayer.stop();
            mStatelyMediaPlayer.reset();
        } else if (mStatelyMediaPlayer.isInStopped()) {
            mStatelyMediaPlayer.reset();
        }

        try {
            AssetFileDescriptor afd = mAssetManager.openFd("winter.mp3");
            try {
                Timber.i( "audio hog -- initStatelyMediaPlayer; asset file descriptor returned for winter.mp3 is %s", afd);
                mStatelyMediaPlayer.setDataSource(afd.getFileDescriptor());


                Timber.i( "initStatelyMediaPlayer() called with mv_iAudioStreamID of %s", mv_iAudioStreamID);

                //int audioReqState = mAudioManager.requestAudioFocus(this, mv_iAudioStreamID, AudioManager.AUDIOFOCUS_GAIN);
                //if(audioReqState == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                //Timber.i( "audio focus was granted to the hog");
                mStatelyMediaPlayer.setAudioStream(mv_iAudioStreamID);
                try {
                    mStatelyMediaPlayer.prepare();
                } catch (IllegalStateException e) {
                    Timber.e(e, "audio hog -- illegalstate ex thrown while preparing the stately mediaplayer");

                } catch (IOException e) {
                    Timber.e(e, "audio hog -- io ex thrown while preparing the stately mediaplayer");
                }
                mStatelyMediaPlayer.setLooping(true);
                Timber.d("audio successfully acquired, initialized, and prepared");

            } catch (IOException e) {
                Timber.e(e, "audio hog -- io exception thrown while trying to set mediaplayer data source");
            } catch (IllegalArgumentException e) {
                Timber.e(e, "audio hog -- illegalargument ex thrown while trying to set mediaplayer data source");
            } catch (IllegalStateException e) {
                Timber.e(e, "audio hog -- illegalstate ex thrown while trying to set mediaplayer data source");

            }
        } catch (IOException e) {
            Timber.e(e, "audio hog -- io ex thrown while initializing the stately mediaplayer. failed to open fd to audio asset");
        }

    }

    /**
     * Starts playing audio from the audio asset file descriptor, initializing the mediaplayer if necessary
     *
     *
     */
    public boolean playInterferingAudio() {
        boolean bSuccess = false;
        if (!mStatelyMediaPlayer.isReady()) {//mediaplayer needs to be initialized
            try {
                AssetFileDescriptor afd = mAssetManager.openFd("winter.mp3");
                initStatelyMediaPlayer();
                mStatelyMediaPlayer.start();
                Timber.d("audio should be playing now");

                updateNotification(NOTIFICATION_PLAYING);
                bSuccess = true;
            } catch (IOException e) {

                Timber.e(e, "audio hog -- in playInterferingAudio; io ex thrown");
            } catch (IllegalStateException e) {
                Timber.e(e, "audio hog -- in playInterferingAudio; illstate ex thrown by start()");
            }
        } else {//mediaplayer is already init
            try {
                mStatelyMediaPlayer.start();
                Timber.d("audio should be playing now");

                updateNotification(NOTIFICATION_PLAYING);
                bSuccess = true;

            } catch (IllegalStateException e) {
                Timber.e(e, "audio hog -- in playInterferingAudio; illstate ex thrown by start()");
            }
        }


        return bSuccess;

    }

    /**
     * Pauses playback of audio from the audio asset file descriptor
     *
     *
     */
    public boolean pauseInterferingAudio() {
        boolean bSuccess = false;
        try {
            mStatelyMediaPlayer.pause();
            updateNotification(NOTIFICATION_PAUSING);
            bSuccess = true;
        } catch (IllegalStateException e) {
            Timber.e(e, "audio hog -- in pauseInterferingAudio; illstateex thrown while trying to call statelymediaplayer::pause");
        }

        return bSuccess;

    }


    //Notification stuff
    private BroadcastReceiver mPlayPauseReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.v("playpause rec -- received a play/pause command from notif");
            if (intent.getAction().equals(ACTION_PLAY_AUDIO)) {
                Timber.v("playpause rec -- received a play command from notif");
                playInterferingAudio();

                /*//this is taken care of in playInterferingAudio
                updateNotification(NOTIFICATION_PAUSING);
                */
            } else if (intent.getAction().equals(ACTION_PAUSE_AUDIO)) {
                Timber.v("playpause rec -- received a pause command from notif");
                pauseInterferingAudio();
                /*//this is taken care of in pauseInterferingAudio
                updateNotification(NOTIFICATION_PLAYING);
                */
            }

        }

    };
    private BroadcastReceiver mTakeReleaseFocusReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.v("take release rec -- received a take/release command from notif");
            if (intent.getAction().equals(ACTION_RELEASE_AUDIO_FOCUS)) {
                Timber.v("take release rec -- received a release command from notif");
                if (abandonAudioFocus(mv_rAudioFocusChangeListener)) {
                    mv_bAudioFocusHeld = false;
                } else {
                    Timber.e("the hog's request to abandon audio focus over audio focus change listener " + mv_rAudioFocusChangeListener + " failed!");
                }
                /*//this is taken care of in abandonAudioFocus above
                updateNotification(NOTIFICATION_RELEASED);
                */
            } else if (intent.getAction().equals(ACTION_TAKE_AUDIO_FOCUS)) {
                Timber.v("take release rec -- received a take command from notif");
                if (requestAudioFocus(mv_rAudioFocusChangeListener, mv_iAudioStreamID, mv_iAudioFocusDuration)) {
                    mv_bAudioFocusHeld = true;
                } else {
                    Timber.e("the hog's request to take audio focus over stream " + resolveAudioStream(mv_iAudioStreamID) + " for duration " + resolveAudioFocusState(mv_iAudioFocusDuration) + " failed!");
                }
                /*//this is taken care of in requestAudioFocus above
                updateNotification(NOTIFICATION_TAKEN);
                */
            }

        }

    };

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
        mNotifyBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        // todo: using the same builder instance did not prevent repeat alerts
        Timber.d("buildNotification; builder is " + mNotifyBuilder);

        // Start of a loop that processes data and then notifies the user
        mNotifyBuilder.setContentText(text);
        // Because the ID remains unchanged, the existing notification is
        // updated.
        Notification notification = mNotifyBuilder.build();//.getNotification();//.build();
        configureNotification(notification);
        return notification;

    }

    /**
     * Builds a new notification and calls {@link NotificationManagerCompat#notify(int, Notification)}
     * using it with {@link #NOTIFICATION_ID}
     * @param notice this param can modify the specific behavior of the no
     */
    private void updateNotification(int notice) {
        Notification notification = buildNotification(notice);

        // Send the newly created notification --
        // this will be accompanied by an alert!
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mNM.notify(NOTIFICATION_ID, notification);
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
        if(mStatelyMediaPlayer.isInStarted()){
            Timber.v("playpause rec -- in buildNotification; mStatelyMediaPlayer.isInStarted() is true, so setting playpause to pause gfx");
            playPauseIntent.setAction(ACTION_PAUSE_AUDIO);
            contentView.setImageViewResource(R.id.notif_btn_playpause, R.drawable.ic_media_pause);
            contentView.setContentDescription(R.id.notif_btn_playpause, getString(R.string.pause_audio));
        }
        else{
            Timber.v("playpause rec -- in buildNotification; mStatelyMediaPlayer.isInStarted() is false, so setting playpause to play gfx");
            playPauseIntent.setAction(ACTION_PLAY_AUDIO);
            contentView.setImageViewResource(R.id.notif_btn_playpause, R.drawable.ic_media_play);
            contentView.setContentDescription(R.id.notif_btn_playpause, getString(R.string.play_audio));
        }

        // Set a pending intent for the take/release audio focus button
        Intent takeReleaseFocusIntent = new Intent(ACTION_RELEASE_AUDIO_FOCUS);
        if(mv_bAudioFocusHeld){
            Timber.v("take release focus -- in buildNotification; focus is held, so setting take/release to release txt");
            takeReleaseFocusIntent.setAction(ACTION_RELEASE_AUDIO_FOCUS);
            contentView.setTextViewText(R.id.notif_btn_take_rel_focus, getString(R.string.release_audio_focus));
        }
        else{
            Timber.v("take release focus -- in buildNotification; focus is not held, so setting take/release to take txt");
            takeReleaseFocusIntent.setAction(ACTION_TAKE_AUDIO_FOCUS);
            contentView.setTextViewText(R.id.notif_btn_take_rel_focus, getString(R.string.take_audio_focus));
        }

        // attach the expanded content view to the notification
        notification.contentView = contentView;

        // play/pause music and take/release audio focus notif buttons
        PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent takeReleasePendingIntent = PendingIntent.getBroadcast(this, 0, takeReleaseFocusIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        contentView.setOnClickPendingIntent(R.id.notif_btn_playpause, playPausePendingIntent);
        contentView.setOnClickPendingIntent(R.id.notif_btn_take_rel_focus, takeReleasePendingIntent);

        notification.flags = Notification.FLAG_ONGOING_EVENT;
    }
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_LOW;
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
     * Requests audio focus for AudioHogg over the given stream for duration durationType
     * @param stream -- the audio stream to req focus over
     * @param durationType -- the duration hint to pass to audiomanager
     * @return true if req granted, false otherwise
     */
    public boolean requestAudioFocus(AudioManager.OnAudioFocusChangeListener listener,int stream,int durationType){
        int iRet = mAudioManager.requestAudioFocus(listener, stream, durationType);
        Timber.i("audio focus request results in "+Util.resolveAudioFocusRequestResult(iRet));
        if(iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mv_bAudioFocusHeld = true;
            updateNotification(NOTIFICATION_TAKEN);
        }
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    /**
     * Abandons audio focus and updates the take/release state of the notif
     * @return true if abandoned successfully (request_granted), false otherwise
     */
    public boolean abandonAudioFocus(AudioManager.OnAudioFocusChangeListener listener){
        int iRet = mAudioManager.abandonAudioFocus(listener);
        Timber.d("audio focus abandon results in %s", Util.resolveAudioFocusRequestResult(iRet));
        if(iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            mv_bAudioFocusHeld = false;
            if (!mIsDestroyed) {
                updateNotification(NOTIFICATION_RELEASED);
            }
        }
        return iRet == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    //TODO: update the focus held state and notifications as necessary for all focus state changes
    private class HogAudioFocusChangeListener implements AudioManager.OnAudioFocusChangeListener{

        @Override
        public void onAudioFocusChange(int focusChange) {

            Timber.i( "The audio hog service has just received an onAudioFocusChange callback, with focus state %s", resolveAudioFocusState(focusChange));

            switch(focusChange){
                case AudioManager.AUDIOFOCUS_GAIN:{

                    Timber.i( "audio focus gained");
                    mv_bAudioFocusHeld = true;
                    updateNotification(NOTIFICATION_TAKEN);

                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:{

                    Timber.i( "audio focus gained transiently");
                    mv_bAudioFocusHeld = true;
                    updateNotification(NOTIFICATION_TAKEN);

                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:{

                    Timber.i( "audio focus gained transiently but also exclusively");
                    mv_bAudioFocusHeld = true;
                    updateNotification(NOTIFICATION_TAKEN);


                    break;
                }
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:{

                    Timber.i( "audio focus gained transiently, you may have a duck");
                    mv_bAudioFocusHeld = true;
                    updateNotification(NOTIFICATION_TAKEN);


                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS:{
                    Timber.i( "audio focus lost");
                    if(mStatelyMediaPlayer.isInStarted() || mStatelyMediaPlayer.isInPaused()){
                        try {
                            if (mStatelyMediaPlayer.isInStarted()) {
                                mStatelyMediaPlayer.pause();
                            }
                            mStatelyMediaPlayer.stop();

                        }catch(IllegalStateException e){
                            Timber.e(e, "audio hog -- illegal state ex thrown while pausing/stopping the mediaplayer in response to audio focus loss");
                        }
                    }

                    mv_bAudioFocusHeld = false;
                    updateNotification(NOTIFICATION_RELEASED);

                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:{
                    Timber.i( "audio focus lost transiently");

                    mv_bAudioFocusHeld = false;
                    updateNotification(NOTIFICATION_RELEASED);

                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:{
                    Timber.i( "audio focus lost transiently, and you can have a duck");

                    mv_bAudioFocusHeld = false;
                    updateNotification(NOTIFICATION_RELEASED);

                    break;
                }
            } // end switch(focusChange)
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
