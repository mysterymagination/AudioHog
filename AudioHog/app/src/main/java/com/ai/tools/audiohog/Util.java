package com.ai.tools.audiohog;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import android.media.AudioManager;
import android.util.Log;

public class Util {

	public static final String TAG = "Util";

	/**
	 * Returns the MD5 hash string of the given string s.
	 * @param s
	 * @return
	 */
	public static final String md5(final String s) {
	    try {
	        // Create MD5 Hash
	        MessageDigest digest = java.security.MessageDigest
	                .getInstance("MD5");
	        digest.update(s.getBytes());
	        byte messageDigest[] = digest.digest();

	        // Create Hex String
	        StringBuffer hexString = new StringBuffer();
	        for (int i = 0; i < messageDigest.length; i++) {
	            String h = Integer.toHexString(0xFF & messageDigest[i]);
	            while (h.length() < 2)
	                h = "0" + h;
	            hexString.append(h);
	        }
	        return hexString.toString();

	    } catch (NoSuchAlgorithmException e) {
	        Log.e(TAG, "An error occurred while generating the MD5 hash: " + e.getMessage(), e);
	    }
	    return "";
	}

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
