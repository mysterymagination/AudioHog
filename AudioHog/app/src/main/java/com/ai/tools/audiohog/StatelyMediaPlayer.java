package com.ai.tools.audiohog;

import java.io.FileDescriptor;
import java.io.IOException;

import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.util.Log;

/**
 * A subclass of android.media.MediaPlayer which provides methods for
 * state-management, data-source management, etc.
 *
 * @author originally inspired by  ww w  .  j  a  v  a 2 s  .  c o  m [http://www.java2s.com/Open-Source/Android_Free_Code/Sound/stream/com_speakingcode_android_media_mediaplayerStatefulMediaPlayer_java.htm], adapted by jeff creswell
 * 
 */
public class StatelyMediaPlayer extends android.media.MediaPlayer {
	
	private final static String TAG = "StatelyMediaPlayer";

	
    /**
     * Set of states for StatelyMediaPlayer:
     * These correspond to all possible states in the MediaPlayer FSM, and allow for precision
     * handling of audio streams
     * 
     * IDLE,INITIALIZED,PREPARING,PREPARED,STARTED,PAUSED,STOPPED,PLAYBACKCOMPLETED,ERROR,END
     * 
     */
    public enum MPStates {
       
    	IDLE,INITIALIZED,PREPARING,PREPARED,STARTED,PAUSED,PLAYBACKCOMPLETED,STOPPED,ERROR,END
    }
 
    private MPStates mState;
    private int mv_iAudioStream = AudioManager.STREAM_MUSIC;
 
  
 
    /**
     * Instantiates a StatelyMediaPlayer object.
     */
    public StatelyMediaPlayer() {
        super();
        setState(MPStates.IDLE);
        
    }
 
    
    public StatelyMediaPlayer(AssetFileDescriptor afd) {
        super();
        this.setAudioStreamType(mv_iAudioStream);//assume stream music
       
        
        try {
        	
        	
            setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            
            setState(MPStates.INITIALIZED);
        }
        catch (Exception e) {
            Log.e(TAG, "setDataSource("+afd.getFileDescriptor().toString()+") Failed");
            setState(MPStates.ERROR);
        }
    }
 
    @Override
    public void reset() {
        super.reset();
        this.mState = MPStates.IDLE;
    }
 
    @Override
    public void start() {
        super.start();
        setState(MPStates.STARTED);
    }
 
    @Override
    public void pause() {
 
        super.pause();
        setState(MPStates.PAUSED);
 
    }
 
    @Override
    public void stop() {
        super.stop();
        setState(MPStates.STOPPED);
    }
 
    @Override
    public void release() {
        super.release();
        setState(MPStates.END);
    }
 
    @Override
    public void prepare() throws IOException, IllegalStateException {
        super.prepare();
        setState(MPStates.PREPARED);
    }
 
    @Override
    public void prepareAsync() throws IllegalStateException {
        super.prepareAsync();
        setState(MPStates.PREPARING);
    }
 
    public MPStates getState() {
        return mState;
    }
    
    public void setAudioStream(int streamID){
    	mv_iAudioStream = streamID;
    	super.setAudioStreamType(mv_iAudioStream);
    }
    public int getAudioStream(){
    	return mv_iAudioStream;
    }
 
    /**
     * NOTE: You should never use this method directly (outside of this class) except for
     * debugging or you will break the state machine and crash your
     * app!
     * 
     * @param state the state to set
     */
    public void setState(MPStates state) {
        this.mState = state;
    }
    
 
    public boolean isInInitialized() {
        return (mState == MPStates.INITIALIZED);
    }
    /**
     * If the audio source is set and prepared, and start() can be called safely, returns true.  Else, returns false
     * @return
     */
    public boolean isReady() {
        return (mState.ordinal() >= MPStates.PREPARED.ordinal() && mState.ordinal() < MPStates.STOPPED.ordinal());
    }
 
    public boolean isInIdle() {
        return (mState == MPStates.IDLE);
    }
 
    public boolean isInStopped() {
        return (mState == MPStates.STOPPED);
    }
 
    public boolean isInStarted() {
    	boolean tempStartState = mState == MPStates.STARTED;
    	boolean tempIsPlaying = this.isPlaying();
    	
    	if(tempStartState && !tempIsPlaying || !tempStartState && tempIsPlaying){
    		//Something's odd with the FSM -- when playing we should be in the 
    		//STARTED state
    		Log.w(TAG, "Somehow our FSM is in an odd state, as our isInStartState returns "+tempStartState+" and our mediaplayer.isPlaying() returns "+tempIsPlaying);
    	}
        return tempStartState;//(mState == MPStates.STARTED || this.isPlaying());
    }
 
    public boolean isInPaused() {
        return (mState == MPStates.PAUSED);
    }
 
    public boolean isInPrepared() {
        return (mState == MPStates.PREPARED);
    }
    public boolean isInPreparing() {
        return (mState == MPStates.PREPARING);
    }
    public boolean isInEnd() {
        return (mState == MPStates.END);
    }
    public boolean isInError() {
        return (mState == MPStates.ERROR);
    }
    public boolean isInPlaybackCompleted() {
        return (mState == MPStates.PLAYBACKCOMPLETED);
    }
    
    //TODOx: add the remaining state checks like template above
    
}