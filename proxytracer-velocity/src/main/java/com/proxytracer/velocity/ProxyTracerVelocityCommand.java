package com.proxytracer.velocity;

import com.proxytracer.common.CheckResult;
import com.proxytracer.common.ProxyTracerApiException;
import com.proxytracer.common.ProxyTracerConfig;
import com.proxytracer.common.WhitelistStore;
import com.proxytracer.common.YamlConfigLoader;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class ProxyTracerVelocityCommand implements SimpleCommand {
    private static final List<String> ROOT = Arrays.asList("reload", "set", "setkey", "check", "cache", "status", "whitelist");
    private static final List<String> CACHE = Collections.singletonList("clear");
    private static final List<String> WL_TYPES = Arrays.asList("name", "ip");
    private static final List<String> WL_ACTIONS = Arrays.asList("add", "remove", "list");

    private final ProxyTracerVelocityPlugin plugin;

    ProxyTracerVelocityCommand(ProxyTracerVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        ProxyTracerConfig config = plugin.getConfig();
        if (!source.hasPermission(config.getAdminPermission())) {
            source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendUsage(source);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                plugin.reloadConfig();
                source.sendMessage(Component.text("ProxyTracer config reloaded.", NamedTextColor.GREEN));
                return;
            case "set":
            case "setkey":
                handleSetKey(source, args);
                return;
            case "check":
                handleCheck(source, args);
                return;
            case "cache":
                handleCache(source, args);
                return;
            case "status":
                sendStatus(source);
                return;
            case "whitelist":
                handleWhitelist(source, args);
                return;
            default:
                sendUsage(source);
        }
    }

    private void handleSetKey(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /proxytracer set <api_key>", NamedTextColor.RED));
            return;
        }
        String key = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
            key = key.substring(1, key.length() - 1).trim();
        }
        if (key.isEmpty()) {
            source.sendMessage(Component.text("API key cannot be empty.", NamedTextColor.RED));
            return;
        }
        try {
            plugin.setApiKey(key);
            source.sendMessage(Component.text(
                    "ProxyTracer API key saved (" + YamlConfigLoader.maskApiKey(key) + ") and reloaded.",
                    NamedTextColor.GREEN));
            if (!plugin.getConfig().hasApiKey()) {
                source.sendMessage(Component.text(
                        "Warning: key still looks like a placeholder. Use your real key from proxytracer.com",
                        NamedTextColor.YELLOW));
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("Failed to save API key: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleCheck(CommandSource source, String[] args) {
        if (args.length != 2) {
            source.sendMessage(Component.text("Usage: /proxytracer check <ip>", NamedTextColor.RED));
            return;
        }
        String ip = WhitelistStore.normalizeIp(args[1].trim());
        source.sendMessage(Component.text("Checking " + ip + "...", NamedTextColor.GRAY));
        CompletableFuture.runAsync(() -> {
            try {
                CheckResult result = plugin.getCheckService().checkIpOnly(ip, plugin.getConfig());
                source.sendMessage(Component.text(
                        "ProxyTracer: " + result.getIp() + " proxy=" + result.isProxy(),
                        NamedTextColor.GREEN));
            } catch (ProxyTracerApiException e) {
                source.sendMessage(Component.text("ProxyTracer API error: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void handleCache(CommandSource source, String[] args) {
        if (args.length == 2 && "clear".equalsIgnoreCase(args[1])) {
            plugin.getCache().clear();
            source.sendMessage(Component.text("ProxyTracer cache cleared.", NamedTextColor.GREEN));
            return;
        }
        source.sendMessage(Component.text("Usage: /proxytracer cache clear", NamedTextColor.RED));
    }

    private void handleWhitelist(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text(
                    "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]", NamedTextColor.RED));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
        WhitelistStore store = plugin.getWhitelist();

        if ("list".equals(action) || args.length == 2) {
            if ("name".equals(type)) {
                list(source, "names", store.listNames());
                return;
            }
            if ("ip".equals(type)) {
                list(source, "IPs", store.listIps());
                return;
            }
        }

        if (args.length < 4) {
            source.sendMessage(Component.text(
                    "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]", NamedTextColor.RED));
            return;
        }

        String value = args[3].trim();
        if ("name".equals(type)) {
            if ("add".equals(action)) {
                boolean added = store.addName(value);
                source.sendMessage(Component.text(
                        added ? "Whitelisted name: " + value : "Name already whitelisted: " + value,
                        added ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return;
            }
            if ("remove".equals(action)) {
                boolean removed = store.removeName(value);
                source.sendMessage(Component.text(
                        removed ? "Removed name whitelist: " + value : "Name was not whitelisted: " + value,
                        removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return;
            }
        }
        if ("ip".equals(type)) {
            if ("add".equals(action)) {
                boolean added = store.addIp(value);
                source.sendMessage(Component.text(
                        added ? "Whitelisted IP: " + WhitelistStore.normalizeIp(value) : "IP already whitelisted: " + value,
                        added ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return;
            }
            if ("remove".equals(action)) {
                boolean removed = store.removeIp(value);
                source.sendMessage(Component.text(
                        removed ? "Removed IP whitelist: " + value : "IP was not whitelisted: " + value,
                        removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return;
            }
        }
        source.sendMessage(Component.text(
                "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]", NamedTextColor.RED));
    }

    private void list(CommandSource source, String label, List<String> values) {
        source.sendMessage(Component.text("Whitelisted " + label + " (" + values.size() + "):", NamedTextColor.GOLD));
        if (values.isEmpty()) {
            source.sendMessage(Component.text("(none)", NamedTextColor.GRAY));
        } else {
            for (String v : values) {
                source.sendMessage(Component.text("- " + v, NamedTextColor.GRAY));
            }
        }
    }

    private void sendStatus(CommandSource source) {
        ProxyTracerConfig config = plugin.getConfig();
        source.sendMessage(Component.text("ProxyTracer status (Velocity)", NamedTextColor.GOLD));
        source.sendMessage(Component.text("API key: " + (config.hasApiKey() ? "configured" : "missing"), NamedTextColor.GRAY));
        source.sendMessage(Component.text("Mode: " + config.getMode().name().toLowerCase(Locale.ROOT), NamedTextColor.GRAY));
        source.sendMessage(Component.text("Cache: " + plugin.getCache().size() + " entries", NamedTextColor.GRAY));
        source.sendMessage(Component.text(
                "Whitelist names: " + plugin.getWhitelist().nameCount()
                        + " | IPs: " + plugin.getWhitelist().ipCount(), NamedTextColor.GRAY));
        source.sendMessage(Component.text("Site: " + config.getMessageUrl(), NamedTextColor.GRAY));
    }

    private void sendUsage(CommandSource source) {
        source.sendMessage(Component.text("Usage: /proxytracer <reload|set|check|cache|status|whitelist>", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("set: /proxytracer set <api_key>", NamedTextColor.GRAY));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission(plugin.getConfig().getAdminPermission())) {
            return Collections.emptyList();
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return ROOT;
        }
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        if (args.length == 2 && "cache".equalsIgnoreCase(args[0])) {
            return filter(CACHE, args[1]);
        }
        if (args.length == 2 && "whitelist".equalsIgnoreCase(args[0])) {
            return filter(WL_TYPES, args[1]);
        }
        if (args.length == 3 && "whitelist".equalsIgnoreCase(args[0])) {
            return filter(WL_ACTIONS, args[2]);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
