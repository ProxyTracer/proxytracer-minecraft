package com.proxytracer.common;

import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Platform-agnostic join check: whitelist → cache → API → mode / on-error.
 */
public final class CheckService {
    private final ApiClient apiClient;
    private final IpCache cache;
    private final WhitelistStore whitelist;
    private final Consumer<String> infoLog;
    private final Consumer<String> warnLog;

    public CheckService(
            ApiClient apiClient,
            IpCache cache,
            WhitelistStore whitelist,
            Consumer<String> infoLog,
            Consumer<String> warnLog
    ) {
        this.apiClient = apiClient;
        this.cache = cache;
        this.whitelist = whitelist;
        this.infoLog = infoLog == null ? s -> {
        } : infoLog;
        this.warnLog = warnLog == null ? s -> {
        } : warnLog;
    }

    public String resolveLoginIp(InetAddress address, ProxyTracerConfig config) {
        if (config.isDebugEnabled() && !config.getDebugOverrideLoginIp().isEmpty()) {
            return WhitelistStore.normalizeIp(config.getDebugOverrideLoginIp());
        }
        if (address == null) {
            return "unknown";
        }
        return WhitelistStore.normalizeIp(address.getHostAddress());
    }

    /**
     * @param bypassed true if the platform already determined permission bypass
     */
    public LoginDecision checkLogin(
            String playerName,
            String ip,
            boolean bypassed,
            ProxyTracerConfig config
    ) {
        String safePlayer = playerName == null ? "unknown" : playerName;
        String safeIp = ip == null ? "unknown" : ip;

        if (bypassed) {
            infoLog.accept("Allowed " + safePlayer + " from " + safeIp + ": permission bypass");
            return LoginDecision.allow(safeIp, safePlayer, "bypass", false, false);
        }

        if (whitelist.isNameWhitelisted(safePlayer)) {
            infoLog.accept("Allowed " + safePlayer + " from " + safeIp + ": name whitelist");
            return LoginDecision.allow(safeIp, safePlayer, "whitelist-name", false, false);
        }

        if (whitelist.isIpWhitelisted(safeIp)) {
            infoLog.accept("Allowed " + safePlayer + " from " + safeIp + ": IP whitelist");
            return LoginDecision.allow(safeIp, safePlayer, "whitelist-ip", false, false);
        }

        try {
            CheckLookup lookup = lookup(safeIp, config);
            return handleProxyResult(safePlayer, safeIp, lookup.result, lookup.fromCache, config);
        } catch (ProxyTracerApiException e) {
            return handleApiError(safePlayer, safeIp, config, e);
        }
    }

    public CheckResult checkIpOnly(String ip, ProxyTracerConfig config) throws ProxyTracerApiException {
        return lookup(ip, config).result;
    }

    private CheckLookup lookup(String ip, ProxyTracerConfig config) throws ProxyTracerApiException {
        if (config.isCacheEnabled()) {
            Optional<CheckResult> cached = cache.get(ip, config.getCacheTtl());
            if (cached.isPresent()) {
                infoLog.accept("Cache hit for " + ip + ": proxy=" + cached.get().isProxy());
                return new CheckLookup(cached.get(), true);
            }
        }

        CheckResult result = apiClient.check(ip, config);
        if (config.isCacheEnabled()) {
            cache.put(ip, result, config.getCacheMaxEntries());
        }
        return new CheckLookup(result, false);
    }

    private LoginDecision handleProxyResult(
            String playerName,
            String ip,
            CheckResult result,
            boolean fromCache,
            ProxyTracerConfig config
    ) {
        if (!result.isProxy()) {
            infoLog.accept("Allowed " + playerName + " from " + ip + ": proxy=false");
            return LoginDecision.allow(ip, playerName, "clean", false, fromCache);
        }

        ActionMode mode = config.getMode();
        if (mode == ActionMode.BLOCK) {
            String kick = config.formatKickMessage(ip, playerName);
            infoLog.accept("Blocked " + playerName + " from " + ip + ": proxy=true");
            return LoginDecision.deny(ip, playerName, "proxy", kick, true, fromCache, false);
        }

        if (mode == ActionMode.WARN) {
            warnLog.accept("Warned for " + playerName + " from " + ip + ": proxy=true");
            return LoginDecision.warn(ip, playerName, "proxy", true, fromCache, false);
        }

        // LOG
        infoLog.accept("Logged " + playerName + " from " + ip + ": proxy=true");
        return LoginDecision.allow(ip, playerName, "proxy-log", true, fromCache);
    }

    private LoginDecision handleApiError(
            String playerName,
            String ip,
            ProxyTracerConfig config,
            ProxyTracerApiException exception
    ) {
        ActionMode action = config.getOnApiError();
        warnLog.accept("API error for " + playerName + " from " + ip + ": " + exception.getMessage()
                + " (on-error=" + action.name().toLowerCase() + ")");

        if (action == ActionMode.BLOCK) {
            String kick = config.formatKickMessage(ip, playerName);
            return LoginDecision.deny(ip, playerName, "api-error", kick, false, false, true);
        }
        if (action == ActionMode.WARN) {
            return LoginDecision.warn(ip, playerName, "api-error", false, false, true);
        }
        return LoginDecision.allow(ip, playerName, "api-error-allow", false, false);
    }

    private static final class CheckLookup {
        private final CheckResult result;
        private final boolean fromCache;

        private CheckLookup(CheckResult result, boolean fromCache) {
            this.result = result;
            this.fromCache = fromCache;
        }
    }
}
