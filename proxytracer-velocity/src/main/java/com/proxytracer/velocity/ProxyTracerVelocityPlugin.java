package com.proxytracer.velocity;

import com.google.inject.Inject;
import com.proxytracer.common.ApiClient;
import com.proxytracer.common.CheckService;
import com.proxytracer.common.DiscordNotifier;
import com.proxytracer.common.IpCache;
import com.proxytracer.common.ProxyTracerConfig;
import com.proxytracer.common.WhitelistStore;
import com.proxytracer.common.YamlConfigLoader;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

@Plugin(
        id = "proxytracer",
        name = "ProxyTracer",
        version = "1.3.0",
        description = "ProxyTracer proxy/VPN protection for Velocity",
        authors = {"ProxyTracer"},
        url = "https://proxytracer.com"
)
public final class ProxyTracerVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private volatile ProxyTracerConfig config;
    private ApiClient apiClient;
    private IpCache cache;
    private WhitelistStore whitelist;
    private CheckService checkService;
    private DiscordNotifier discordNotifier;

    @Inject
    public ProxyTracerVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.apiClient = new ApiClient();
        this.cache = new IpCache();
        this.whitelist = new WhitelistStore(
                dataDirectory.resolve("whitelist.yml"),
                msg -> logger.warn(msg)
        );
        this.discordNotifier = new DiscordNotifier(
                this::getConfig,
                msg -> logger.warn(msg)
        );
        this.checkService = new CheckService(
                apiClient,
                cache,
                whitelist,
                msg -> logger.info(msg),
                msg -> logger.warn(msg)
        );

        reloadConfig();

        server.getEventManager().register(this, new ProxyTracerVelocityListener(this));

        CommandManager commandManager = server.getCommandManager();
        CommandMeta meta = commandManager.metaBuilder("proxytracer")
                .plugin(this)
                .build();
        commandManager.register(meta, new ProxyTracerVelocityCommand(this));

        logger.info("ProxyTracer (Velocity) enabled in {} mode. Install this jar on Velocity only.",
                config.getMode().name().toLowerCase());
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (discordNotifier != null) {
            discordNotifier.shutdown();
        }
    }

    public void reloadConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        try {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                YamlConfigLoader.copyDefaultIfMissing(configFile, in);
            }
        } catch (IOException e) {
            logger.warn("Could not write default config.yml: {}", e.getMessage());
        }

        Map<String, Object> map = YamlConfigLoader.load(configFile, msg -> logger.warn(msg));
        this.config = ProxyTracerConfig.fromMap(map, msg -> logger.warn(msg));
        this.whitelist.reload(config);
    }

    public void setApiKey(String apiKey) throws IOException {
        Path configFile = dataDirectory.resolve("config.yml");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            YamlConfigLoader.copyDefaultIfMissing(configFile, in);
        }
        YamlConfigLoader.setApiKey(configFile, apiKey);
        reloadConfig();
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyTracerConfig getConfig() {
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
