package net.rehacktive.crc;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import com.google.gson.GsonBuilder;

import net.rehacktive.crc.internal.CacheElement;
import net.rehacktive.crc.internal.RestClient;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * CachedRestClient 
 * simply cache REST content to sharedPreferences
 * @author stefano di francisca
 * @version 1.0
 *
 * USAGE DETAILS:
Getting an instance:

CachedRestClient cache = new CachedRestClient("mycache", getApplicationContext());

for a specific cache (in this way you can use more caches)

Getting content:

int ttl = 30000; // cache expires in 30 seconds
boolean forceReload = false; // we don't want to force reload
CacheElement ret = cache.getContent("http://www.rehacktive.net", ttl, forceReload);

or the async method with listener

cache.getContent("http://www.rehacktive.net",ttl,forceReload,new OnCachedCallDone() {

@Override
public void onSuccess(CacheElement ret) {
....
}
});

The CacheElement object contains:
-timestamp of the last call
-statusCode (the HTTP status)
-data retrieved;

Invalidate a cache content:

cache.invalidateCache("http://www.rehacktive.net");

Clear all cache:

cache.clearAllCache();


Note:
the only default accepted status is 200: only calls with accepted status will be cached
(we don't wanna cache errors!)
 */

public class CachedRestClient {

    private static SharedPreferences mPrefs;

    private static String PREFS_NAME;

    private static String TAG = "CachedRestClient";

    private static Context ctx;

    private boolean cacheErrors = false;
    private List<Integer> acceptedStatus;

    private ArrayList <NameValuePair> headers;

    private static boolean logEnabled = true;

    public CachedRestClient(String cacheName, Context context) {
        ctx = context;
        PREFS_NAME = cacheName;
        // by default, it accepts only "200" http status as okay
        setAcceptedStatus(new ArrayList<Integer>(Arrays.asList(200)));
    }

    public static boolean isLogEnabled() {
        return logEnabled;
    }

    public static void setLogEnabled(boolean logEnabled) {
        CachedRestClient.logEnabled = logEnabled;
    }

    // PUBLIC METHODS

//	public CacheElement getBestContent(String url) throws Exception {
//		// if connection available, try to get fresh data
////		if(isOnline()) {
////			Utils.log(TAG,"is online");
////			try {
////				Utils.log(TAG,"retrive content from network");
////				return getContent(url, -1, true); // if online, retrieve fresh content
////			}
////			catch(Exception e) {
////				Utils.log(TAG,"error on network - retrieve content from cache");
////				return getContent(url, -1, false); // if error, retrieve cache content
////			}
////		}
////		else {
////			// otherwise try to get cache content
////			Utils.log(TAG,"is offline - retrieve content from cache");
////			return getContent(url, -1, false); // if not online, retrieve cache content
////		}
//        return getContent(url, -1, isOnline()); // DOES THIS MAKE SENSE? :D
//    }
    /**
     * @param url - the url to call (from network or cache)
     * @param ttl - the time to live for this cache element (-1 for infinite)
     * @param forceReload - reload the content from network forced
     * @param listener - the listener
     * @return the content
     * @throws Exception
     */
    public void getContent(final String url, final int ttl, final boolean forceReload, final OnCachedCallDone listener) {
        new AsyncTask<String, Void, String>() {

            CacheElement ret = null;

            @Override
            protected String doInBackground(String... params) {

                try {
                    ret = getContent(url, ttl, forceReload);
                }
                catch(Exception e) {
                    if(listener!=null) listener.onError();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                if(listener!=null) listener.onSuccess(ret);
            }
        }.execute();
    }

    /**
     * @param url - the url to call (from network or cache)
     * @param lastUpdated
     * @return the content
     * @throws Exception
     */
    public CacheElement getContent(String url, Date lastUpdated) throws Exception {
        CacheElement cacheElement = getCacheElement(url);
        if(cacheElement==null)
            return getContent(url, -1, true);

        boolean isExpired = lastUpdated.after(new Date(cacheElement.getTimestamp()));

        Utils.log(TAG,"lastUpdated: "+lastUpdated);
        Utils.log(TAG,"cache timestamp: "+new Date(cacheElement.getTimestamp()));
        Utils.log(TAG,"isExpired: "+isExpired);

        if(isExpired)
            return getContent(url, -1, isOnline()); // update from network only if online
            // or should it be always true? in this case if the network is not available, the content (expired) is not available
        else
            return cacheElement;
    }

    /**
     * @param url - the url to call (from network or cache)
     * @param ttl - the time to live for this cache element (-1 for infinite)
     * @param forceReload - reload the content from network forced
     * @return the content
     * @throws Exception
     */
    public CacheElement getContent(String url, int ttl, boolean forceReload) throws Exception {

        CacheElement cacheElement = getCacheElement(url);

        boolean isFresh = cacheElement!=null && ((System.currentTimeMillis()-cacheElement.getTimestamp())<ttl);

        if(cacheElement==null || (ttl!=-1 && !isFresh) || forceReload) {

            // cache hit - check for "freshness"
            if(cacheElement!=null) {
                Utils.log(TAG,"cache timestamp:"+cacheElement.getTimestamp());
                Utils.log(TAG,"current timestamp:"+System.currentTimeMillis());
                Utils.log(TAG,"difference:"+(System.currentTimeMillis()-cacheElement.getTimestamp()));
                Utils.log(TAG,"reccomended ttl:"+ttl);
            }

            // check for connection
            if(!isOnline()) {
                Utils.log(TAG,"NO CONNECTION FOUND");
                throw new NetworkException("no connection found");
            }
            // we need some fresh data
            // call service
            CacheElement c = makeCall(url,cacheElement);
            // return new data
            Utils.log(TAG,"content retrieved from network");
            return c;
        }
        else {
            // yes, it's fresh!
            Utils.log(TAG,"content retrieved from cache");
            return cacheElement;
        }

    }

    // invalidate cache
    /**
     * @param url - the url to invalidate
     * @throws java.security.NoSuchAlgorithmException
     */
    public void invalidateCache(String url) throws NoSuchAlgorithmException {
        if(url!=null) {
            CacheElement c = getCacheElement(url);
            if(c!=null) {
                c.setTimestamp((long) 0);
                Utils.log(TAG,"invalidate cache for url: "+url);
                try {
                    storeValue(HashKey(url),c);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            else {
                Utils.log(TAG,"no cache found for url: "+url);
            }
        }
    }

    // clear all cache
    public void clearAllCache() {
        mPrefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        Editor e = mPrefs.edit();
        e.clear();
        e.commit();
        Utils.log(TAG, "all cache cleared");
    }


    public void AddHeader(String name, String value)
    {
        if(headers==null) headers = new ArrayList<NameValuePair>();
        headers.add(new BasicNameValuePair(name, value));
    }

    // END OF PUBLIC METHODS

    private boolean isFresh(int ttl) {
        return false;
    }

    private boolean isFresh(Date d) {
        return false;
    }

    // make the call to the real service and store content to cache too
    private CacheElement makeCall(String url, CacheElement cacheElement) throws Exception {
        try {
            // call service
            Utils.log(TAG,"call:"+url);
            RestClient client = new RestClient(url);

            if(headers!=null) {
                for(NameValuePair h : headers)
                {
                    client.AddHeader(h.getName(), h.getValue());
                }
            }


            client.Execute(RestClient.RequestMethod.GET);

            String response = client.getResponse();
            int status = client.getStatus();


            Utils.log(TAG,"call status:"+status);
            Utils.log(TAG,"call response:"+response);


            // create a new CacheElement
            CacheElement c = new CacheElement(response,status);

            // cache the errors only if requested
            if(isCacheErrors() || getAcceptedStatus().contains(status)) {
                // store it
                storeValue(HashKey(url),c);
                Utils.log(TAG,"content cached");
            }
            else {
                Utils.log(TAG,"content not cached");
            }

            return c;

        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // return cacheelement from shared preferences
    private CacheElement getCacheElement(String uri) {
        try {
            String key = HashKey(uri);
            String value = getValue(key);
            CacheElement c = new GsonBuilder().create().fromJson(value, CacheElement.class);
            return c;

        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // get value from shared preferences
    private String getValue(String key) {
        mPrefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        String ret = mPrefs.getString(key, null);
        return ret;
    }

    // get all <key,value> stored
    private Map<String, ?> getAllData() {
        mPrefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        return mPrefs.getAll();
    }

    // store key/value to shared preferences
    private void storeValue(String key, Object r) {
        mPrefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        String data = new GsonBuilder().create().toJson(r);
        Editor e = mPrefs.edit();
        e.putString(key, data);
        e.commit();
        Utils.log(TAG, "stored data:"+data);
        Utils.log(TAG, "by key:"+key);
    }

    // calculate md5 of a url
    private String HashKey(String string) throws NoSuchAlgorithmException {
        final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.reset();
        messageDigest.update((string).getBytes());
        final byte[] resultByte = messageDigest.digest();
        final String result = new String(toHexString(resultByte));
        Utils.log(TAG,"md5 value: "+result);
        return result;
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }

    public static String toHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v/16];
            hexChars[j*2 + 1] = hexArray[v%16];
        }
        return new String(hexChars);
    }

    public boolean isCacheErrors() {
        return cacheErrors;
    }

    public void setCacheErrors(boolean cacheErrors) {
        this.cacheErrors = cacheErrors;
    }

    public List<Integer> getAcceptedStatus() {
        return acceptedStatus;
    }

    public void setAcceptedStatus(List<Integer> acceptedStatus) {
        this.acceptedStatus = acceptedStatus;
    }

}