package com.proxytracer.bungee;

import com.proxytracer.common.LoginDecision;
import com.proxytracer.common.ProxyTracerConfig;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

// Must be public: Bungee EventBus reflects into handler methods (Java 16+ blocks package-private).
public final class ProxyTracerBungeeListener implements Listener {
    private final ProxyTracerBungeePlugin plugin;

    public ProxyTracerBungeeListener(ProxyTracerBungeePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(PreLoginEvent event) {
        if (event.isCancelled()) {
            return;
        }

        PendingConnection connection = event.getConnection();
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        String playerName = connection.getName();
        String ip = resolveIp(connection, config);

        // Intentionally block the event thread until API returns (strict first-join block).
        event.registerIntent(plugin);
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            try {
                LoginDecision decision = plugin.getCheckService().checkLogin(playerName, ip, false, config);
                applyDecision(event, null, playerName, ip, decision, config, true);
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    /**
     * Secondary path if PreLogin was not used; mostly for completeness.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(LoginEvent event) {
        // Primary check is on PreLoginEvent; skip double-check if already cancelled.
        if (event.isCancelled()) {
            return;
        }
    }

    private void applyDecision(
            PreLoginEvent preLoginEvent,
            LoginEvent loginEvent,
            String playerName,
            String ip,
            LoginDecision decision,
            ProxyTracerConfig config,
            boolean preLogin
    ) {
        switch (decision.getOutcome()) {
            case DENY:
                String kick = decision.getKickMessage() == null
                        ? "Blocked by ProxyTracer"
                        : decision.getKickMessage();
                if (preLogin) {
                    preLoginEvent.setCancelled(true);
                    preLoginEvent.setCancelReason(TextComponent.fromLegacyText(kick));
                } else if (loginEvent != null) {
                    loginEvent.setCancelled(true);
                    loginEvent.setCancelReason(TextComponent.fromLegacyText(kick));
                }
                if (decision.isApiError()) {
                    plugin.getDiscordNotifier().notifyError(playerName, ip, decision.getReason());
                    notifyStaff(ChatColor.RED + "[ProxyTracer] Blocked " + playerName
                            + " because the API check failed.", config);
                } else {
                    plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "block");
                    notifyStaff(ChatColor.RED + "[ProxyTracer] Blocked " + playerName
                            + " from " + ip + " (proxy/VPN).", config);
                }
                break;
            case WARN:
                if (decision.isApiError()) {
                    plugin.getDiscordNotifier().notifyError(playerName, ip, decision.getReason());
                    notifyStaff(ChatColor.YELLOW + "[ProxyTracer] API check failed for "
                            + playerName + " from " + ip + ".", config);
                } else {
                    plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "warn");
                    notifyStaff(ChatColor.YELLOW + "[ProxyTracer] " + playerName
                            + " joined from " + ip + " flagged as proxy/VPN.", config);
                }
                break;
            case ALLOW:
            default:
                if (decision.isProxy() && "proxy-log".equals(decision.getReason())) {
                    plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "log");
                } else if (!decision.isProxy() && "clean".equals(decision.getReason())) {
                    plugin.getDiscordNotifier().notifyAllowed(playerName, ip);
                }
                break;
        }
    }

    private String resolveIp(PendingConnection connection, ProxyTracerConfig config) {
        if (config.isDebugEnabled() && !config.getDebugOverrideLoginIp().isEmpty()) {
            return plugin.getCheckService().resolveLoginIp(null, config);
        }
        SocketAddress address = connection.getSocketAddress();
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) address;
            if (inet.getAddress() != null) {
                return plugin.getCheckService().resolveLoginIp(inet.getAddress(), config);
            }
        }
        return "unknown";
    }

    private void notifyStaff(String message, ProxyTracerConfig config) {
        for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
            if (player.hasPermission(config.getNotifyPermission())) {
                player.sendMessage(TextComponent.fromLegacyText(message));
            }
        }
    }
}
