// AudioHogInterface.aidl
package com.ai.tools.audiohog;

// Declare any non-default types here with import statements

interface IAudioHog {

    /** Request the process ID of this service */
    int getPid();

    /** Request the user ID of this service */
    int getUid();

    /**
    * Sets the audio stream that audio focus will be taken from, and that the sample audio
    * will play on
    */
    void setAudioStream(int streamCode);
    /**
    * Sets the audio focus type, which must be one of:<br />
    * {@link AudioManager#AUDIOFOCUS_GAIN}<br />
    * {@link AudioManager#AUDIOFOCUS_GAIN_TRANSIENT}<br />
    * {@link AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK}<br />
    * {@link AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)} as of API 19<br />
    * {@link AudioManager#AUDIOFOCUS_LOSS}<br />
    * {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT}<br />
    * {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}<br />
    * <br /><br />
    * Note that only the GAIN* types can be passed into {@link AudioManager#requestAudioFocus},
    * and {@link AudioManager#abandonAudioFocus} does not require a focus type argument.  The LOSS*
    * focus types are only passed to the registered {@link AudioManager#AudioFoucsChangeListener}
    * of the component currently losing audio focus to indicate that focus of the corresponding duration has now been lost.
    * e.g. GAIN -> LOSS passed to {@link AudioManager#OnAudioFocusChangeListener#onAudioFocusChange}
    *      GAIN_TRANSIENT -> LOSS_TRANSIENT passed to {@link AudioFocusChangeListener#onAudioFocusChange}
    *      GAIN_TRANSIENT_EXCLUSIVE -> ?
    *      GAIN_TRANSIENT_MAY_DUCK -> LOSS_TRANSIENT_CAN_DUCK passed to {@link AudioFocusChangeListener#onAudioFocusChange}
    */
    void setAudioFocusDuration(int focusDuration);

    /**
    * Shuts down the service, releasing its resources
    */
    void exit();

    void playAudio();

    void pauseAudio();

    void takeAudioFocus();

    void releaseAudioFocus();



}
