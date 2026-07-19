package com.proxytracer.bukkit;

import com.proxytracer.common.ApiClient;
import com.proxytracer.common.CheckService;
import com.proxytracer.common.DiscordNotifier;
import com.proxytracer.common.IpCache;
import com.proxytracer.common.ProxyTracerConfig;
import com.proxytracer.common.WhitelistStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProxyTracerPlugin extends JavaPlugin {
    private volatile ProxyTracerConfig proxyTracerConfig;
    private ApiClient apiClient;
    private IpCache cache;
    private WhitelistStore whitelist;
    private CheckService checkService;
    private DiscordNotifier discordNotifier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.apiClient = new ApiClient();
        this.cache = new IpCache();
        this.whitelist = new WhitelistStore(
                getDataFolder().toPath().resolve("whitelist.yml"),
                msg -> getLogger().warning(msg)
        );
        this.discordNotifier = new DiscordNotifier(
                this::getProxyTracerConfig,
                msg -> getLogger().warning(msg)
        );
        this.checkService = new CheckService(
                apiClient,
                cache,
                whitelist,
                msg -> getLogger().info(msg),
                msg -> getLogger().warning(msg)
        );

        loadProxyTracerConfig();

        getServer().getPluginManager().registerEvents(new ProxyTracerLoginListener(this), this);

        PluginCommand command = getCommand("proxytracer");
        if (command != null) {
            ProxyTracerCommand executor = new ProxyTracerCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("ProxyTracer (Bukkit) enabled in "
                + proxyTracerConfig.getMode().name().toLowerCase() + " mode.");
    }

    @Override
    public void onDisable() {
        if (discordNotifier != null) {
            discordNotifier.shutdown();
        }
    }

    void loadProxyTracerConfig() {
        reloadConfig();
        this.proxyTracerConfig = loadFromBukkitConfig(getConfig());
        this.whitelist.reload(proxyTracerConfig);
    }

    /**
     * Persists API key to config.yml and reloads runtime config.
     */
    void setApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is empty");
        }
        getConfig().set("api.key", apiKey.trim());
        saveConfig();
        loadProxyTracerConfig();
    }

    private ProxyTracerConfig loadFromBukkitConfig(FileConfiguration config) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("base-url", config.getString("api.base-url", "https://api.proxytracer.com/v1"));
        api.put("key", config.getString("api.key", ""));
        api.put("timeout-ms", config.getInt("api.timeout-ms", 2000));
        api.put("on-error", config.getString("api.on-error", "allow"));
        root.put("api", api);

        root.put("mode", config.getString("mode", "block"));

        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("url", config.getString("messages.url", "https://proxytracer.com"));
        messages.put("kick", config.getString("messages.kick", ProxyTracerConfig.DEFAULT_KICK));
        root.put("messages", messages);

        Map<String, Object> cacheMap = new LinkedHashMap<>();
        cacheMap.put("enabled", config.getBoolean("cache.enabled", true));
        cacheMap.put("ttl-minutes", config.getLong("cache.ttl-minutes", 1440));
        cacheMap.put("max-entries", config.getInt("cache.max-entries", 10000));
        root.put("cache", cacheMap);

        Map<String, Object> whitelistMap = new LinkedHashMap<>();
        whitelistMap.put("names", config.getStringList("whitelist.names"));
        whitelistMap.put("ips", config.getStringList("whitelist.ips"));
        root.put("whitelist", whitelistMap);

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("bypass", config.getString("permissions.bypass", "proxytracer.bypass"));
        permissions.put("admin", config.getString("permissions.admin", "proxytracer.admin"));
        permissions.put("notify", config.getString("permissions.notify", "proxytracer.notify"));
        root.put("permissions", permissions);

        Map<String, Object> discord = new LinkedHashMap<>();
        discord.put("enabled", config.getBoolean("discord.enabled", false));
        discord.put("webhook-url", config.getString("discord.webhook-url", ""));
        discord.put("notify-allowed", config.getBoolean("discord.notify-allowed", false));
        discord.put("notify-blocked", config.getBoolean("discord.notify-blocked", true));
        discord.put("notify-errors", config.getBoolean("discord.notify-errors", true));
        root.put("discord", discord);

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("enabled", config.getBoolean("debug.enabled", false));
        debug.put("override-login-ip", config.getString("debug.override-login-ip", ""));
        root.put("debug", debug);

        return ProxyTracerConfig.fromMap(root, msg -> getLogger().warning(msg));
    }

    ProxyTracerConfig getProxyTracerConfig() {
        return proxyTracerConfig;
    }

    ApiClient getApiClient() {
        return apiClient;
    }

    IpCache getCache() {
        return cache;
    }

    WhitelistStore getWhitelist() {
        return whitelist;
    }

    CheckService getCheckService() {
        return checkService;
    }

    DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }
}
