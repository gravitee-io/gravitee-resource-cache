package io.gravitee.resource.cache.rediscache;

import io.gravitee.common.http.HttpHeaders;

import java.io.Serializable;

public class Response implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = -6722335011068865181L;

    public Response () {};

    public Response(int status, HttpHeaders headers, int timeToLive) {
        this.status = status;
        this.headers = headers;
        this.timeToLive = timeToLive;
    }

    private int status;

    private int timeToLive;

    private HttpHeaders headers;

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(int TTL) {
        this.timeToLive = TTL;
    }
}

