package net.rehacktive.crc;

import android.util.Log;


public class Utils {
	
	public static void log(String tag, String content) {
		if(CachedRestClient.isLogEnabled()) Log.d(tag,content);
	}

}
