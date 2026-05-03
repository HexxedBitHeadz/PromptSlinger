package com.promptslinger.burp;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

public class EndpointSlot {

    public String name;
    public String url;
    public String method;
    public String fieldName;

    public transient HttpRequest         request;
    public transient HttpRequestResponse proxyItem;
    public transient String              sessionId;

    public EndpointSlot() {}

    public EndpointSlot(String name, String url, String method, String fieldName,
                        HttpRequest request, HttpRequestResponse proxyItem, String sessionId) {
        this.name      = name;
        this.url       = url;
        this.method    = method;
        this.fieldName = fieldName;
        this.request   = request;
        this.proxyItem = proxyItem;
        this.sessionId = sessionId;
    }

    public boolean hasRequest() { return request != null; }

    @Override public String toString() {
        return name != null ? name : (url != null ? url : "(empty)");
    }
}
