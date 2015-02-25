package net.rehacktive.crc.internal;

public class CacheElement {

    private Long timestamp;
    private int statusCode;
    private String data;

    public CacheElement() {
    }

    public CacheElement(String d, int s) {
        timestamp = System.currentTimeMillis();
        data = d;
        statusCode = s;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }


}
