package net.rehacktive.crc.internal;

import android.content.Context;
import android.content.res.AssetManager;

import net.rehacktive.crc.CachedRestClient;
import net.rehacktive.crc.Utils;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/*
 * a sample REST client 
 * it uses EasySSL socket factory for SSL sockets (no cert configuration required)
 * and it's ready for JSON - need to be modified for XML
 * 
 * used by CacheManager
 */
public class RestClient {

    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";
	
	private ArrayList <NameValuePair> params;
	private ArrayList <NameValuePair> headers;

	private String body;

	private String url;

	private int responseCode;

	private String cookie;

	private String response;
	private int status;

	private String methodType;

	public String getResponse() {
		return response;
	}

	public int getStatus() {
		return status;
	}

	public int getResponseCode() {
		return responseCode;
	}

	public RestClient(String url)
	{
		this.url = url;
		params = new ArrayList<NameValuePair>();
		headers = new ArrayList<NameValuePair>();
		body = null;
	}

    public void enableLog(boolean enable) {
        CachedRestClient.setLogEnabled(enable);
    }
    
	public void setCookie(String cookie) {
		this.cookie = cookie;
	}

	public void AddParam(String name, String value)
	{
		params.add(new BasicNameValuePair(name, value));
	}

	public void AddHeader(String name, String value)
	{
		headers.add(new BasicNameValuePair(name, value));
	}

	public void setBody(String b) {
		body = b;
	}

	public void Execute(RequestMethod method) throws Exception
	{
		switch(method) {
		case GET:
		{
			methodType = "GET";
			//add parameters
			String combinedParams = "";
			if(!params.isEmpty()){
				combinedParams += "?";
				for(NameValuePair p : params)
				{
					String paramString = p.getName() + "=" + URLEncoder.encode(p.getValue(),"UTF-8");
					if(combinedParams.length() > 1)
					{
						combinedParams  +=  "&" + paramString;
					}
					else
					{
						combinedParams += paramString;
					}
				}
			}

			HttpGet request = new HttpGet(url + combinedParams);

			//add headers
			for(NameValuePair h : headers)
			{
				request.addHeader(h.getName(), h.getValue());

			}

			executeRequest(request, url);
			break;
		}
		case DELETE:
		{
			methodType = "DELETE";
			HttpDelete request = new HttpDelete(url);

			//add headers
			for(NameValuePair h : headers)
			{
				request.addHeader(h.getName(), h.getValue());
			}

			executeRequest(request, url);
			break;
		}			
		case POST:
		{
			methodType = "POST";
			HttpPost request = new HttpPost(url);

			//add headers
			for(NameValuePair h : headers)
			{
				request.addHeader(h.getName(), h.getValue());
			}
			request.addHeader("Accept","application/json");

			if(!params.isEmpty()){
				request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
			}


			if(body!=null) {
				request.setEntity(new StringEntity(body,HTTP.UTF_8));

			}

			executeRequest(request, url);
			break;
		}
		case PUT:
		{
			methodType = "PUT";
			HttpPut request = new HttpPut(url);

			//add headers
			for(NameValuePair h : headers)
			{
				request.addHeader(h.getName(), h.getValue());
			}
			request.addHeader("Content-type","application/json");

			if(!params.isEmpty()){
				request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
			}

			if(body!=null) {
				request.setEntity(new StringEntity(body));

			}

			executeRequest(request, url);
			break;
		}
            case PATCH:
            {
                methodType = "PATCH";
                HttpPatch request = new HttpPatch(url);

                //add headers
                for(NameValuePair h : headers)
                {
                    request.addHeader(h.getName(), h.getValue());
                }
                request.addHeader("Content-type","application/json");

                if(!params.isEmpty()){
                    request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
                }

                if(body!=null) {
                    request.setEntity(new StringEntity(body));

                }

                executeRequest(request, url);
                break;
            }


		}
	}

    private SSLSocketFactory sslSocketFactory = null;

    public void constructSSLSocketFactory(Context context, String storeFile, String storePassword) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream keyStoreInputStream = assetManager.open(storeFile);
            KeyStore trustStore = KeyStore.getInstance("BKS");

            trustStore.load(keyStoreInputStream, storePassword.toCharArray());

            sslSocketFactory = new SSLSocketFactory(trustStore);
            sslSocketFactory.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

	public void executeRequest(HttpUriRequest getRequest,String url) throws Exception {

		// uncomment if you need an extra log "curl-like" for debugging

		String curl = "curl --request "+methodType+" -i ";
		for(Header h : getRequest.getAllHeaders()) {
			curl += " --header \""+h.getName()+":"+h.getValue()+"\"";
		}
		if(getRequest instanceof HttpPut || getRequest instanceof HttpPost)
		{
			try {
				String getEntityStr = convertStreamToString(((HttpPost) getRequest).getEntity().getContent()) ; 
				//System.out.println("ENTITY:"+getEntityStr);
				curl += " -d \""+getEntityStr.trim()+"\"";
			}
			catch(Exception e) {}
		}

		curl += " "+url;
		Utils.log("curlString", curl);


		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        if(sslSocketFactory!=null)
            schemeRegistry.register(new Scheme("https",sslSocketFactory, 443));
        else
            schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));

		HttpParams params = new BasicHttpParams();
		int timeoutConnection = 60000;
		HttpConnectionParams.setConnectionTimeout(params, timeoutConnection);
		int timeoutSocket = 60000;
		HttpConnectionParams.setSoTimeout(params, timeoutSocket);
		params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
		params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
		params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

		DefaultHttpClient client = new DefaultHttpClient(cm,params);
        client.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(HttpRequest request, HttpContext context) {
                // Add header to accept gzip content
                if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
                    request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
                }
            }
        });

        client.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(HttpResponse response, HttpContext context) {
                // Inflate any responses compressed with gzip
                final HttpEntity entity = response.getEntity();
                if(entity != null) {
                    final Header encoding = entity.getContentEncoding();
                    if (encoding != null) {
                        for (HeaderElement element : encoding.getElements()) {
                            if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
                                response.setEntity(new InflatingEntity(response.getEntity()));
                                break;
                            }
                        }
                    }
                }
            }
        });
		//client.getCookieStore().addCookie(new BasicClientCookie("Cookie", cookie));

		status= 0;

		try {
			HttpResponse getResponse = client.execute(getRequest);

			status = getResponse.getStatusLine().getStatusCode();
			Utils.log("DEBUG","STATUS CODE:"+status);

			HttpEntity getResponseEntity = getResponse.getEntity();
			if(getResponseEntity!=null)
                response = convertStreamToString(getResponseEntity.getContent());
            else
                response = "";
			Utils.log("DEBUG/RESPONSE",response);
		} 
		catch (IOException e) {
			getRequest.abort();
			Utils.log("DEBUG", "IO Error for URL " + url);
			e.printStackTrace();
			throw e;

		} catch(Exception e) {
			getRequest.abort();
			e.printStackTrace();
			Utils.log("DEBUG", status+":General Error for URL " + url);
			throw e;
		}
	}

	private static String convertStreamToString(InputStream is) {

		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}

	public enum RequestMethod
	{
		GET,
		POST,
		DELETE,
		PUT,
        PATCH
	}

    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }
}