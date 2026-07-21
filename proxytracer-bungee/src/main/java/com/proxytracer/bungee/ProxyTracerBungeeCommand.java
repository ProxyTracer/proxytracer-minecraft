package com.proxytracer.bungee;

import com.proxytracer.common.CheckResult;
import com.proxytracer.common.ProxyTracerApiException;
import com.proxytracer.common.ProxyTracerConfig;
import com.proxytracer.common.WhitelistStore;
import com.proxytracer.common.YamlConfigLoader;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ProxyTracerBungeeCommand extends Command implements TabExecutor {
    private static final List<String> ROOT = Arrays.asList("reload", "set", "setkey", "check", "cache", "status", "whitelist");
    private static final List<String> CACHE = Collections.singletonList("clear");
    private static final List<String> WL_TYPES = Arrays.asList("name", "ip");
    private static final List<String> WL_ACTIONS = Arrays.asList("add", "remove", "list");

    private final ProxyTracerBungeePlugin plugin;

    public ProxyTracerBungeeCommand(ProxyTracerBungeePlugin plugin) {
        super("proxytracer", "proxytracer.admin");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        if (!sender.hasPermission(config.getAdminPermission())) {
            sender.sendMessage(TextComponent.fromLegacyText(
                    ChatColor.RED + "You do not have permission to use this command."));
            return;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                plugin.reloadProxyTracerConfig();
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "ProxyTracer config reloaded."));
                return;
            case "set":
            case "setkey":
                handleSetKey(sender, args);
                return;
            case "check":
                handleCheck(sender, args);
                return;
            case "cache":
                handleCache(sender, args);
                return;
            case "status":
                sendStatus(sender);
                return;
            case "whitelist":
                handleWhitelist(sender, args);
                return;
            default:
                sendUsage(sender);
        }
    }

    private void handleSetKey(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Usage: /proxytracer set <api_key>"));
            return;
        }
        String key = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
            key = key.substring(1, key.length() - 1).trim();
        }
        if (key.isEmpty()) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "API key cannot be empty."));
            return;
        }
        try {
            plugin.setApiKey(key);
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "ProxyTracer API key saved ("
                    + YamlConfigLoader.maskApiKey(key) + ") and reloaded."));
            if (!plugin.getProxyTracerConfig().hasApiKey()) {
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.YELLOW
                        + "Warning: key still looks like a placeholder. Use your real key from proxytracer.com"));
            }
        } catch (Exception e) {
            sender.sendMessage(TextComponent.fromLegacyText(
                    ChatColor.RED + "Failed to save API key: " + e.getMessage()));
        }
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Usage: /proxytracer check <ip>"));
            return;
        }
        String ip = WhitelistStore.normalizeIp(args[1].trim());
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "Checking " + ip + "..."));
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            try {
                CheckResult result = plugin.getCheckService().checkIpOnly(ip, plugin.getProxyTracerConfig());
                sender.sendMessage(TextComponent.fromLegacyText(
                        ChatColor.GREEN + "ProxyTracer: " + result.getIp() + " proxy=" + result.isProxy()));
            } catch (ProxyTracerApiException e) {
                sender.sendMessage(TextComponent.fromLegacyText(
                        ChatColor.RED + "ProxyTracer API error: " + e.getMessage()));
            }
        });
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (args.length == 2 && "clear".equalsIgnoreCase(args[1])) {
            plugin.getCache().clear();
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "ProxyTracer cache cleared."));
            return;
        }
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Usage: /proxytracer cache clear"));
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextComponent.fromLegacyText(
                    ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]"));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
        WhitelistStore store = plugin.getWhitelist();

        if ("list".equals(action) || args.length == 2) {
            if ("name".equals(type)) {
                list(sender, "names", store.listNames());
                return;
            }
            if ("ip".equals(type)) {
                list(sender, "IPs", store.listIps());
                return;
            }
        }

        if (args.length < 4) {
            sender.sendMessage(TextComponent.fromLegacyText(
                    ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]"));
            return;
        }

        String value = args[3].trim();
        if ("name".equals(type) && "add".equals(action)) {
            boolean added = store.addName(value);
            sender.sendMessage(TextComponent.fromLegacyText(
                    (added ? ChatColor.GREEN + "Whitelisted name: " : ChatColor.YELLOW + "Name already whitelisted: ")
                            + value));
            return;
        }
        if ("name".equals(type) && "remove".equals(action)) {
            boolean removed = store.removeName(value);
            sender.sendMessage(TextComponent.fromLegacyText(
                    (removed ? ChatColor.GREEN + "Removed name whitelist: " : ChatColor.YELLOW + "Name was not whitelisted: ")
                            + value));
            return;
        }
        if ("ip".equals(type) && "add".equals(action)) {
            boolean added = store.addIp(value);
            sender.sendMessage(TextComponent.fromLegacyText(
                    (added ? ChatColor.GREEN + "Whitelisted IP: " : ChatColor.YELLOW + "IP already whitelisted: ")
                            + WhitelistStore.normalizeIp(value)));
            return;
        }
        if ("ip".equals(type) && "remove".equals(action)) {
            boolean removed = store.removeIp(value);
            sender.sendMessage(TextComponent.fromLegacyText(
                    (removed ? ChatColor.GREEN + "Removed IP whitelist: " : ChatColor.YELLOW + "IP was not whitelisted: ")
                            + value));
            return;
        }
        sender.sendMessage(TextComponent.fromLegacyText(
                ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]"));
    }

    private void list(CommandSender sender, String label, List<String> values) {
        sender.sendMessage(TextComponent.fromLegacyText(
                ChatColor.GOLD + "Whitelisted " + label + " (" + values.size() + "):"));
        if (values.isEmpty()) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "(none)"));
        } else {
            for (String v : values) {
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "- " + v));
            }
        }
    }

    private void sendStatus(CommandSender sender) {
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "ProxyTracer status (Bungee)"));
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "API key: "
                + (config.hasApiKey() ? "configured" : "missing")));
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "Mode: "
                + config.getMode().name().toLowerCase(Locale.ROOT)));
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "Cache: "
                + plugin.getCache().size() + " entries"));
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "Whitelist names: "
                + plugin.getWhitelist().nameCount() + " | IPs: " + plugin.getWhitelist().ipCount()));
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "Site: " + config.getMessageUrl()));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextComponent.fromLegacyText(
                ChatColor.YELLOW + "Usage: /proxytracer <reload|set|check|cache|status|whitelist>"));
        sender.sendMessage(TextComponent.fromLegacyText(
                ChatColor.GRAY + "set: /proxytracer set <api_key>"));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.getProxyTracerConfig().getAdminPermission())) {
            return Collections.emptyList();
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

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
