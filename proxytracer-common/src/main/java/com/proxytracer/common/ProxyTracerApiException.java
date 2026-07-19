package com.proxytracer.common;

public final class ProxyTracerApiException extends Exception {
    public ProxyTracerApiException(String message) {
        super(message);
    }

    public ProxyTracerApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
