package com.proxytracer.bukkit;

import com.proxytracer.common.CheckResult;
import com.proxytracer.common.ProxyTracerApiException;
import com.proxytracer.common.ProxyTracerConfig;
import com.proxytracer.common.WhitelistStore;
import com.proxytracer.common.YamlConfigLoader;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class ProxyTracerCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = Arrays.asList("reload", "set", "setkey", "check", "cache", "status", "whitelist");
    private static final List<String> CACHE = Collections.singletonList("clear");
    private static final List<String> WL_TYPES = Arrays.asList("name", "ip");
    private static final List<String> WL_ACTIONS = Arrays.asList("add", "remove", "list");

    private final ProxyTracerPlugin plugin;

    ProxyTracerCommand(ProxyTracerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        if (!sender.hasPermission(config.getAdminPermission())) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "reload":
                plugin.loadProxyTracerConfig();
                sender.sendMessage(ChatColor.GREEN + "ProxyTracer config reloaded.");
                return true;
            case "set":
            case "setkey":
                return handleSetKey(sender, args);
            case "check":
                return handleCheck(sender, args);
            case "cache":
                return handleCache(sender, args);
            case "status":
                sendStatus(sender);
                return true;
            case "whitelist":
                return handleWhitelist(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleSetKey(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /proxytracer set <api_key>");
            return true;
        }
        // Allow keys with spaces if quoted poorly: join remaining args
        String key = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
            key = key.substring(1, key.length() - 1).trim();
        }
        if (key.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "API key cannot be empty.");
            return true;
        }
        try {
            plugin.setApiKey(key);
            sender.sendMessage(ChatColor.GREEN + "ProxyTracer API key saved ("
                    + YamlConfigLoader.maskApiKey(key) + ") and reloaded.");
            if (!plugin.getProxyTracerConfig().hasApiKey()) {
                sender.sendMessage(ChatColor.YELLOW + "Warning: key still looks like a placeholder. Use your real key from proxytracer.com");
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to save API key: " + e.getMessage());
        }
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /proxytracer check <ip>");
            return true;
        }

        String ip = WhitelistStore.normalizeIp(args[1].trim());
        sender.sendMessage(ChatColor.GRAY + "Checking " + ip + "...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ProxyTracerConfig config = plugin.getProxyTracerConfig();
            try {
                CheckResult result = plugin.getCheckService().checkIpOnly(ip, config);
                sender.sendMessage(ChatColor.GREEN + "ProxyTracer: " + result.getIp()
                        + " proxy=" + result.isProxy());
            } catch (ProxyTracerApiException e) {
                sender.sendMessage(ChatColor.RED + "ProxyTracer API error: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean handleCache(CommandSender sender, String[] args) {
        if (args.length == 2 && "clear".equalsIgnoreCase(args[1])) {
            plugin.getCache().clear();
            sender.sendMessage(ChatColor.GREEN + "ProxyTracer cache cleared.");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /proxytracer cache clear");
        return true;
    }

    private boolean handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]");
            return true;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 2 || (args.length >= 3 && "list".equalsIgnoreCase(args[2]))) {
            if (!"name".equals(type) && !"ip".equals(type)) {
                sender.sendMessage(ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]");
                return true;
            }
            if (args.length >= 3 && !"list".equalsIgnoreCase(args[2])) {
                // fall through to add/remove validation
            } else {
                return listWhitelist(sender, type);
            }
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]");
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        WhitelistStore store = plugin.getWhitelist();

        if ("list".equals(action)) {
            return listWhitelist(sender, type);
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /proxytracer whitelist " + type + " " + action + " <value>");
            return true;
        }

        String value = args[3].trim();
        switch (type) {
            case "name":
                if ("add".equals(action)) {
                    boolean added = store.addName(value);
                    sender.sendMessage(added
                            ? ChatColor.GREEN + "Whitelisted name: " + value
                            : ChatColor.YELLOW + "Name already whitelisted: " + value);
                    return true;
                }
                if ("remove".equals(action)) {
                    boolean removed = store.removeName(value);
                    sender.sendMessage(removed
                            ? ChatColor.GREEN + "Removed name whitelist: " + value
                            : ChatColor.YELLOW + "Name was not whitelisted: " + value);
                    return true;
                }
                break;
            case "ip":
                if ("add".equals(action)) {
                    boolean added = store.addIp(value);
                    sender.sendMessage(added
                            ? ChatColor.GREEN + "Whitelisted IP: " + WhitelistStore.normalizeIp(value)
                            : ChatColor.YELLOW + "IP already whitelisted: " + value);
                    return true;
                }
                if ("remove".equals(action)) {
                    boolean removed = store.removeIp(value);
                    sender.sendMessage(removed
                            ? ChatColor.GREEN + "Removed IP whitelist: " + value
                            : ChatColor.YELLOW + "IP was not whitelisted: " + value);
                    return true;
                }
                break;
            default:
                break;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> <add|remove|list> [value]");
        return true;
    }

    private boolean listWhitelist(CommandSender sender, String type) {
        WhitelistStore store = plugin.getWhitelist();
        if ("name".equals(type)) {
            List<String> names = store.listNames();
            sender.sendMessage(ChatColor.GOLD + "Whitelisted names (" + names.size() + "):");
            if (names.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "(none)");
            } else {
                for (String name : names) {
                    sender.sendMessage(ChatColor.GRAY + "- " + name);
                }
            }
            return true;
        }
        if ("ip".equals(type)) {
            List<String> ips = store.listIps();
            sender.sendMessage(ChatColor.GOLD + "Whitelisted IPs (" + ips.size() + "):");
            if (ips.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "(none)");
            } else {
                for (String ip : ips) {
                    sender.sendMessage(ChatColor.GRAY + "- " + ip);
                }
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /proxytracer whitelist <name|ip> list");
        return true;
    }

    private void sendStatus(CommandSender sender) {
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        sender.sendMessage(ChatColor.GOLD + "ProxyTracer status (Bukkit)");
        sender.sendMessage(ChatColor.GRAY + "API key: " + (config.hasApiKey() ? "configured" : "missing"));
        sender.sendMessage(ChatColor.GRAY + "Mode: " + config.getMode().name().toLowerCase(Locale.ROOT));
        sender.sendMessage(ChatColor.GRAY + "On API error: " + config.getOnApiError().name().toLowerCase(Locale.ROOT));
        sender.sendMessage(ChatColor.GRAY + "Cache: " + (config.isCacheEnabled() ? "enabled" : "disabled")
                + " (" + plugin.getCache().size() + " entries)");
        sender.sendMessage(ChatColor.GRAY + "Whitelist names: " + plugin.getWhitelist().nameCount()
                + " | IPs: " + plugin.getWhitelist().ipCount());
        sender.sendMessage(ChatColor.GRAY + "Discord webhook: " + (config.isDiscordEnabled() ? "enabled" : "disabled"));
        sender.sendMessage(ChatColor.GRAY + "Site: " + config.getMessageUrl());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Usage: /proxytracer <reload|set|check|cache|status|whitelist>");
        sender.sendMessage(ChatColor.GRAY + "set: /proxytracer set <api_key>");
        sender.sendMessage(ChatColor.GRAY + "whitelist: /proxytracer whitelist <name|ip> <add|remove|list> [value]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        if (!sender.hasPermission(config.getAdminPermission())) {
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
        if (args.length == 4 && "whitelist".equalsIgnoreCase(args[0]) && "remove".equalsIgnoreCase(args[2])) {
            if ("name".equalsIgnoreCase(args[1])) {
                return filter(plugin.getWhitelist().listNames(), args[3]);
            }
            if ("ip".equalsIgnoreCase(args[1])) {
                return filter(plugin.getWhitelist().listIps(), args[3]);
            }
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
