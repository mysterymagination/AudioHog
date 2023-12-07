package com.ai.tools.audiohog;

import android.media.AudioManager;

public class Util {
	public static String resolveAudioFocusRequestResult(int result){
        switch(result){
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                return "AUDIOFOCUS_REQUEST_GRANTED";
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                return "AUDIOFOCUS_REQUEST_FAILED";
            default:
                return "unknown req result "+result;
        }
    }
}
