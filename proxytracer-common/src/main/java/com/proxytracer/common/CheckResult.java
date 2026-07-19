package com.proxytracer.common;

public final class CheckResult {
    private final String ip;
    private final boolean proxy;

    public CheckResult(String ip, boolean proxy) {
        this.ip = ip;
        this.proxy = proxy;
    }

    public String getIp() {
        return ip;
    }

    public boolean isProxy() {
        return proxy;
    }
}
