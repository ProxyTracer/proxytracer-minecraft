package com.proxytracer.bungee;

import com.proxytracer.common.ApiClient;
import com.proxytracer.common.CheckService;
import com.proxytracer.common.DiscordNotifier;
import com.proxytracer.common.IpCache;
import com.proxytracer.common.ProxyTracerConfig;
import com.proxytracer.common.WhitelistStore;
import com.proxytracer.common.YamlConfigLoader;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

public final class ProxyTracerBungeePlugin extends Plugin {
    private volatile ProxyTracerConfig config;
    private ApiClient apiClient;
    private IpCache cache;
    private WhitelistStore whitelist;
    private CheckService checkService;
    private DiscordNotifier discordNotifier;

    @Override
    public void onEnable() {
        this.apiClient = new ApiClient();
        this.cache = new IpCache();
        Path dataFolder = getDataFolder().toPath();
        this.whitelist = new WhitelistStore(
                dataFolder.resolve("whitelist.yml"),
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

        reloadProxyTracerConfig();

        getProxy().getPluginManager().registerListener(this, new ProxyTracerBungeeListener(this));
        getProxy().getPluginManager().registerCommand(this, new ProxyTracerBungeeCommand(this));

        getLogger().info("ProxyTracer (Bungee) enabled in "
                + config.getMode().name().toLowerCase() + " mode. Install this jar on the proxy only.");
    }

    @Override
    public void onDisable() {
        if (discordNotifier != null) {
            discordNotifier.shutdown();
        }
    }

    public void reloadProxyTracerConfig() {
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        try {
            try (InputStream in = getResourceAsStream("config.yml")) {
                YamlConfigLoader.copyDefaultIfMissing(configFile, in);
            }
        } catch (IOException e) {
            getLogger().warning("Could not write default config.yml: " + e.getMessage());
        }

        Map<String, Object> map = YamlConfigLoader.load(configFile, msg -> getLogger().warning(msg));
        this.config = ProxyTracerConfig.fromMap(map, msg -> getLogger().warning(msg));
        this.whitelist.reload(config);
    }

    public void setApiKey(String apiKey) throws IOException {
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        try (InputStream in = getResourceAsStream("config.yml")) {
            YamlConfigLoader.copyDefaultIfMissing(configFile, in);
        }
        YamlConfigLoader.setApiKey(configFile, apiKey);
        reloadProxyTracerConfig();
    }

    public ProxyTracerConfig getProxyTracerConfig() {
        return config;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public IpCache getCache() {
        return cache;
    }

    public WhitelistStore getWhitelist() {
        return whitelist;
    }

    public CheckService getCheckService() {
        return checkService;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }
}
