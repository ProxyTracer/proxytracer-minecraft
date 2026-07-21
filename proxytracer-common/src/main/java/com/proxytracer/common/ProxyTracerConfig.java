package com.proxytracer.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class ProxyTracerConfig {
    public static final String DEFAULT_KICK =
            "&c&lProxyTracer\n"
                    + "&7https://proxytracer.com\n"
                    + "\n"
                    + "&cYour connection was blocked.\n"
                    + "&7Detected as a &fproxy/VPN&7.\n"
                    + "\n"
                    + "&7IP: &f{ip}\n"
                    + "&7Player: &f{player}\n"
                    + "\n"
                    + "&8Protection by ProxyTracer\n"
                    + "&8proxytracer.com";

    private final String apiBaseUrl;
    private final String apiKey;
    private final int timeoutMs;
    private final ActionMode onApiError;
    private final ActionMode mode;
    private final String kickMessageTemplate;
    private final String messageUrl;
    private final boolean cacheEnabled;
    private final Duration cacheTtl;
    private final int cacheMaxEntries;
    private final String bypassPermission;
    private final String adminPermission;
    private final String notifyPermission;
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final boolean discordNotifyAllowed;
    private final boolean discordNotifyBlocked;
    private final boolean discordNotifyErrors;
    private final boolean debugEnabled;
    private final String debugOverrideLoginIp;
    private final List<String> configWhitelistNames;
    private final List<String> configWhitelistIps;

    private ProxyTracerConfig(Builder b) {
        this.apiBaseUrl = b.apiBaseUrl;
        this.apiKey = b.apiKey;
        this.timeoutMs = b.timeoutMs;
        this.onApiError = b.onApiError;
        this.mode = b.mode;
        this.kickMessageTemplate = b.kickMessageTemplate;
        this.messageUrl = b.messageUrl;
        this.cacheEnabled = b.cacheEnabled;
        this.cacheTtl = b.cacheTtl;
        this.cacheMaxEntries = b.cacheMaxEntries;
        this.bypassPermission = b.bypassPermission;
        this.adminPermission = b.adminPermission;
        this.notifyPermission = b.notifyPermission;
        this.discordEnabled = b.discordEnabled;
        this.discordWebhookUrl = b.discordWebhookUrl;
        this.discordNotifyAllowed = b.discordNotifyAllowed;
        this.discordNotifyBlocked = b.discordNotifyBlocked;
        this.discordNotifyErrors = b.discordNotifyErrors;
        this.debugEnabled = b.debugEnabled;
        this.debugOverrideLoginIp = b.debugOverrideLoginIp;
        this.configWhitelistNames = Collections.unmodifiableList(new ArrayList<>(b.configWhitelistNames));
        this.configWhitelistIps = Collections.unmodifiableList(new ArrayList<>(b.configWhitelistIps));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load from a nested Map as produced by SnakeYAML / simple YAML parsers.
     */
    @SuppressWarnings("unchecked")
    public static ProxyTracerConfig fromMap(Map<String, Object> root, Consumer<String> warn) {
        if (root == null) {
            root = Collections.emptyMap();
        }
        Map<String, Object> api = map(root.get("api"));
        Map<String, Object> messages = map(root.get("messages"));
        Map<String, Object> cache = map(root.get("cache"));
        Map<String, Object> permissions = map(root.get("permissions"));
        Map<String, Object> discord = map(root.get("discord"));
        Map<String, Object> debug = map(root.get("debug"));
        Map<String, Object> whitelist = map(root.get("whitelist"));

        String apiKey = str(api.get("key"), "");
        if (isMissingApiKey(apiKey) && warn != null) {
            warn.accept("ProxyTracer API key is not configured. Set api.key in config.yml.");
        }
        boolean discordEnabled = bool(discord.get("enabled"), false);
        String webhook = str(discord.get("webhook-url"), "");
        if (discordEnabled && webhook.isEmpty() && warn != null) {
            warn.accept("Discord webhook is enabled but discord.webhook-url is empty.");
        }

        return builder()
                .apiBaseUrl(stripTrailingSlash(str(api.get("base-url"), "https://api.proxytracer.com/v1")))
                .apiKey(apiKey.trim())
                .timeoutMs(Math.max(250, intVal(api.get("timeout-ms"), 2000)))
                .onApiError(ActionMode.parse(str(api.get("on-error"), "allow"), ActionMode.ALLOW))
                .mode(ActionMode.parse(str(root.get("mode"), "block"), ActionMode.BLOCK))
                .kickMessageTemplate(str(messages.get("kick"), DEFAULT_KICK))
                .messageUrl(str(messages.get("url"), "https://proxytracer.com"))
                .cacheEnabled(bool(cache.get("enabled"), true))
                .cacheTtl(Duration.ofMinutes(Math.max(1, longVal(cache.get("ttl-minutes"), 1440))))
                .cacheMaxEntries(Math.max(1, intVal(cache.get("max-entries"), 10000)))
                .bypassPermission(permission(str(permissions.get("bypass"), "proxytracer.bypass"), "proxytracer.bypass"))
                .adminPermission(permission(str(permissions.get("admin"), "proxytracer.admin"), "proxytracer.admin"))
                .notifyPermission(permission(str(permissions.get("notify"), "proxytracer.notify"), "proxytracer.notify"))
                .discordEnabled(discordEnabled)
                .discordWebhookUrl(webhook.trim())
                .discordNotifyAllowed(bool(discord.get("notify-allowed"), false))
                .discordNotifyBlocked(bool(discord.get("notify-blocked"), true))
                .discordNotifyErrors(bool(discord.get("notify-errors"), true))
                .debugEnabled(bool(debug.get("enabled"), false))
                .debugOverrideLoginIp(str(debug.get("override-login-ip"), "").trim())
                .configWhitelistNames(stringList(whitelist.get("names")))
                .configWhitelistIps(stringList(whitelist.get("ips")))
                .build();
    }

    public String formatKickMessage(String ip, String player) {
        return MessageFormatter.formatKick(kickMessageTemplate, messageUrl, ip, player);
    }

    public static boolean isMissingApiKey(String apiKey) {
        return apiKey == null || apiKey.isEmpty() || "proxy_your_key_here".equals(apiKey);
    }

    public boolean hasApiKey() {
        return !isMissingApiKey(apiKey);
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public ActionMode getOnApiError() {
        return onApiError;
    }

    public ActionMode getMode() {
        return mode;
    }

    public String getKickMessageTemplate() {
        return kickMessageTemplate;
    }

    public String getMessageUrl() {
        return messageUrl;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public String getAdminPermission() {
        return adminPermission;
    }

    public String getNotifyPermission() {
        return notifyPermission;
    }

    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public boolean isDiscordNotifyAllowed() {
        return discordNotifyAllowed;
    }

    public boolean isDiscordNotifyBlocked() {
        return discordNotifyBlocked;
    }

    public boolean isDiscordNotifyErrors() {
        return discordNotifyErrors;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public String getDebugOverrideLoginIp() {
        return debugOverrideLoginIp;
    }

    public List<String> getConfigWhitelistNames() {
        return configWhitelistNames;
    }

    public List<String> getConfigWhitelistIps() {
        return configWhitelistIps;
    }

    private static String stripTrailingSlash(String value) {
        String cleaned = value == null || value.trim().isEmpty()
                ? "https://api.proxytracer.com/v1"
                : value.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String permission(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return Collections.emptyMap();
    }

    private static String str(Object o, String fallback) {
        if (o == null) {
            return fallback;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? fallback : s;
    }

    private static boolean bool(Object o, boolean fallback) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static int intVal(Object o, int fallback) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longVal(Object o, long fallback) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        if (!(o instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) o) {
            if (item != null) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public static final class Builder {
        private String apiBaseUrl = "https://api.proxytracer.com/v1";
        private String apiKey = "";
        private int timeoutMs = 2000;
        private ActionMode onApiError = ActionMode.ALLOW;
        private ActionMode mode = ActionMode.BLOCK;
        private String kickMessageTemplate = DEFAULT_KICK;
        private String messageUrl = "https://proxytracer.com";
        private boolean cacheEnabled = true;
        private Duration cacheTtl = Duration.ofMinutes(1440);
        private int cacheMaxEntries = 10000;
        private String bypassPermission = "proxytracer.bypass";
        private String adminPermission = "proxytracer.admin";
        private String notifyPermission = "proxytracer.notify";
        private boolean discordEnabled;
        private String discordWebhookUrl = "";
        private boolean discordNotifyAllowed;
        private boolean discordNotifyBlocked = true;
        private boolean discordNotifyErrors = true;
        private boolean debugEnabled;
        private String debugOverrideLoginIp = "";
        private List<String> configWhitelistNames = new ArrayList<>();
        private List<String> configWhitelistIps = new ArrayList<>();

        public Builder apiBaseUrl(String v) {
            this.apiBaseUrl = v;
            return this;
        }

        public Builder apiKey(String v) {
            this.apiKey = v;
            return this;
        }

        public Builder timeoutMs(int v) {
            this.timeoutMs = v;
            return this;
        }

        public Builder onApiError(ActionMode v) {
            this.onApiError = v;
            return this;
        }

        public Builder mode(ActionMode v) {
            this.mode = v;
            return this;
        }

        public Builder kickMessageTemplate(String v) {
            this.kickMessageTemplate = v;
            return this;
        }

        public Builder messageUrl(String v) {
            this.messageUrl = v;
            return this;
        }

        public Builder cacheEnabled(boolean v) {
            this.cacheEnabled = v;
            return this;
        }

        public Builder cacheTtl(Duration v) {
            this.cacheTtl = v;
            return this;
        }

        public Builder cacheMaxEntries(int v) {
            this.cacheMaxEntries = v;
            return this;
        }

        public Builder bypassPermission(String v) {
            this.bypassPermission = v;
            return this;
        }

        public Builder adminPermission(String v) {
            this.adminPermission = v;
            return this;
        }

        public Builder notifyPermission(String v) {
            this.notifyPermission = v;
            return this;
        }

        public Builder discordEnabled(boolean v) {
            this.discordEnabled = v;
            return this;
        }

        public Builder discordWebhookUrl(String v) {
            this.discordWebhookUrl = v;
            return this;
        }

        public Builder discordNotifyAllowed(boolean v) {
            this.discordNotifyAllowed = v;
            return this;
        }

        public Builder discordNotifyBlocked(boolean v) {
            this.discordNotifyBlocked = v;
            return this;
        }

        public Builder discordNotifyErrors(boolean v) {
            this.discordNotifyErrors = v;
            return this;
        }

        public Builder debugEnabled(boolean v) {
            this.debugEnabled = v;
            return this;
        }

        public Builder debugOverrideLoginIp(String v) {
            this.debugOverrideLoginIp = v;
            return this;
        }

        public Builder configWhitelistNames(List<String> v) {
            this.configWhitelistNames = v == null ? new ArrayList<>() : new ArrayList<>(v);
            return this;
        }

        public Builder configWhitelistIps(List<String> v) {
            this.configWhitelistIps = v == null ? new ArrayList<>() : new ArrayList<>(v);
            return this;
        }

        public ProxyTracerConfig build() {
            return new ProxyTracerConfig(this);
        }
    }
}
