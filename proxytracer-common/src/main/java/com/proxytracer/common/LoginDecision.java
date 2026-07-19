package com.proxytracer.common;

/**
 * Result of a login IP check for platform adapters.
 */
public final class LoginDecision {
    public enum Outcome {
        ALLOW,
        DENY,
        WARN
    }

    private final Outcome outcome;
    private final String ip;
    private final String playerName;
    private final boolean proxy;
    private final boolean fromCache;
    private final String reason;
    private final String kickMessage;
    private final boolean apiError;

    private LoginDecision(
            Outcome outcome,
            String ip,
            String playerName,
            boolean proxy,
            boolean fromCache,
            String reason,
            String kickMessage,
            boolean apiError
    ) {
        this.outcome = outcome;
        this.ip = ip;
        this.playerName = playerName;
        this.proxy = proxy;
        this.fromCache = fromCache;
        this.reason = reason;
        this.kickMessage = kickMessage;
        this.apiError = apiError;
    }

    public static LoginDecision allow(String ip, String player, String reason, boolean proxy, boolean fromCache) {
        return new LoginDecision(Outcome.ALLOW, ip, player, proxy, fromCache, reason, null, false);
    }

    public static LoginDecision deny(String ip, String player, String reason, String kickMessage, boolean proxy, boolean fromCache, boolean apiError) {
        return new LoginDecision(Outcome.DENY, ip, player, proxy, fromCache, reason, kickMessage, apiError);
    }

    public static LoginDecision warn(String ip, String player, String reason, boolean proxy, boolean fromCache, boolean apiError) {
        return new LoginDecision(Outcome.WARN, ip, player, proxy, fromCache, reason, null, apiError);
    }

    public boolean shouldDeny() {
        return outcome == Outcome.DENY;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getIp() {
        return ip;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isProxy() {
        return proxy;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public String getReason() {
        return reason;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public boolean isApiError() {
        return apiError;
    }
}
