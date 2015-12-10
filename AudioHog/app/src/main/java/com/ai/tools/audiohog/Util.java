package com.ai.tools.audiohog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.app.Activity;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;


import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;



public class Util {

	public static final String TAG = "Util";
	public static final String AD_UNIT_ID = "";//fill this in with your own ad unit ID, preferably loaded from values/strings.xml
	
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
	
	/**
	 * Inserts an ad into the adview of an activity
	 * <br /><br />
	 * See <a href=https://developers.google.com/mobile-ads-sdk/docs/admob/android/quick-start>ads documentation</a>
	 * 
	 * @param activity
	 *
	 * @return
	 */
	static boolean insertAd(Activity activity) {
		
		

		Log.d(TAG, "google ads -- in insertAd() from activity "+activity);
		



			
			try {
                String android_id = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
                String deviceId = md5(android_id).toUpperCase();
                AdView tempAdView = (AdView) activity.findViewById(R.id.ad_view);
                if(tempAdView != null) {
                    DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();

                    float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
                    float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
                    AdSize customAdSize = new AdSize((int)dpWidth, 50);

					AdRequest adRequest = new AdRequest.Builder()
                            .addTestDevice(PublisherAdRequest.DEVICE_ID_EMULATOR)
                            //.addTestDevice(deviceId)
                            .build();
                    //tempAdView.setAdSize(customAdSize);//you have to specify an adsize in xml, and you aren't allowed to set adsize more than once, so... let's hope banner works nicely
                    tempAdView.setAdListener(mv_rAdListener);
                    tempAdView.setAdUnitId(AD_UNIT_ID);
                    tempAdView.loadAd(adRequest);

                    return true;
                }
                else{
                    Log.e(TAG,"google ads -- publisher adview came back null");
                    return false;
                }
			} catch (RuntimeException e) {
				Log.w(TAG, "google ads -- AdMob threw an exception with message "+e.getMessage());
				
				e.printStackTrace();
				return false;
			}


	}
	
	/**
	 * Pauses the ad processing
	 * Should be called in onPause for all activities using adviews
	 * @param activity
	 *
	 */
	public static void pauseAdView(Activity activity){
		
		Log.d(TAG, "google ads -- in pauseAdView called by activity "+activity);
        AdView tempAdView = (AdView) activity.findViewById(R.id.ad_view);
        if(tempAdView != null) {
            tempAdView.pause();
        }
		
	}
	
	
	/**
	 * Resumes the ad processing
	 * Should be called in onResume for all activities using adviews
	 * @param activity
	 *
	 */
	
	public static void resumeAdView(Activity activity){
		
		Log.d(TAG, "google ads -- in resumeAdView called by activity "+activity);
        AdView tempAdView = (AdView) activity.findViewById(R.id.ad_view);
        if(tempAdView != null) {
            tempAdView.resume();
        }
		
	}
	

	/**
	 * Attempts to remove the google.ads AdView object from
	 * the view hierarchy of the referenced activity and shut down the adview
	 * so that no further web requests are sent
	 * 
	 * 
	 * 
	 * @param activity -- activity whose adview is to be destroyed
	 *
	 */
	
	public static void destroyAdView(Activity activity) {
		
		Log.d(TAG, "google ads -- in destroyAdView called by activity "+activity);
        AdView tempAdView = (AdView) activity.findViewById(R.id.ad_view);
        if(tempAdView != null) {
            tempAdView.destroy();
        }
		
		
	}
	
	

    public static String resolveAdError(int errCode){
        switch(errCode){
            case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                return "ERROR_CODE_INTERNAL_ERROR";
            case AdRequest.ERROR_CODE_INVALID_REQUEST:
                return "ERROR_CODE_INVALID_REQUEST";
            case AdRequest.ERROR_CODE_NETWORK_ERROR:
                return "ERROR_CODE_NETWORK_ERROR";
            case AdRequest.ERROR_CODE_NO_FILL:
                return "ERROR_CODE_NO_FILL";
            default:
                return "unknown ad error "+errCode;
        }
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

    private static AdListener mv_rAdListener = new AdListener() {
        @Override
        public void onAdClosed() {
            super.onAdClosed();
            Log.i(TAG,"ad closed");
        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            super.onAdFailedToLoad(errorCode);
            Log.w(TAG, "ad failed to load with error "+resolveAdError(errorCode));
        }

        @Override
        public void onAdLeftApplication() {
            super.onAdLeftApplication();
            Log.i(TAG, "ad left application");
        }

        @Override
        public void onAdOpened() {
            super.onAdOpened();
            Log.i(TAG, "ad opened");
        }

        @Override
        public void onAdLoaded() {
            super.onAdLoaded();
            Log.i(TAG, "ad loaded");
        }
    };
	
	
}
