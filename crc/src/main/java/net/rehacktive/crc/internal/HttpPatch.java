package net.rehacktive.crc.internal;

import org.apache.http.client.methods.HttpPost;

/**
 * Created by stefano on 19/01/2015.
 */
public class HttpPatch extends HttpPost {
    public static final String METHOD_PATCH = "PATCH";

    public HttpPatch(final String url) {
        super(url);
    }

    @Override
    public String getMethod() {
        return METHOD_PATCH;
    }
}
