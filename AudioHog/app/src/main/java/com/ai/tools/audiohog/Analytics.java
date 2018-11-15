package com.ai.tools.audiohog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import android.content.Context;
import android.util.Log;



public class Analytics {
	public static final String TAG = "Analytics";
	private Context mContext;
	
	public Analytics(Context context){
		mContext = context;
	}
	

	
	/**
	 * Ensure the timestamp comes back accurate and in format YYYY-MM-DDThh:mm:ss
	 * @return
	 */
	public boolean testTimeStamp(){
		boolean bRes = false;
		Calendar time = Calendar.getInstance();
		//Should yield YYYY-MM-DDThh:mm:ss
		String sTimeStamp = time.get(Calendar.YEAR)+"-"
				+time.get(Calendar.MONTH)+"-"
				+time.get(Calendar.DAY_OF_MONTH)+"T"
				+time.get(Calendar.HOUR_OF_DAY)+":"
				+time.get(Calendar.MINUTE)+":"
				+time.get(Calendar.SECOND);
		
		String[] timeElements = sTimeStamp.split(":");
		sTimeStamp = "";
		for(int sCount=0;sCount<timeElements.length;sCount++){
			bRes = timeElements[sCount].length() == 2;
			if(!bRes){
				timeElements[sCount] = "0"+timeElements[sCount];
			}
			sTimeStamp += timeElements[sCount];
			if(sCount < 2){
				sTimeStamp += ":";
			}
			
		}
		Log.d(TAG, "analytics -- timestamp given as "+sTimeStamp);
		return bRes;
	}
	
	/**
	 * Returns the current time in format YYYY-MM-DDThh:mm:ss
	 * @return
	 */
	public static String getTimeStamp(){
		Calendar time = Calendar.getInstance();
		String sTimeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time.getTime());
		sTimeStamp = sTimeStamp.replace(' ', 'T');
		/*
		String sTimeStamp = time.get(Calendar.HOUR_OF_DAY)+":"+time.get(Calendar.MINUTE)+":"+time.get(Calendar.SECOND);
		
		String[] timeElements = sTimeStamp.split(":");
		sTimeStamp = "";
		for(int sCount=0;sCount<timeElements.length;sCount++){
			
			if(timeElements[sCount].length() == 1){
				timeElements[sCount] = "0"+timeElements[sCount];
			}
			sTimeStamp += timeElements[sCount];
			if(sCount < 2){
				sTimeStamp += ":";
			}
			
		}
		*/
		return sTimeStamp;
	}
	

	


}
