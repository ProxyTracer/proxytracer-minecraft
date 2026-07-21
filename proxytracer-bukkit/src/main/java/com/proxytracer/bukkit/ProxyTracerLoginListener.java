package com.proxytracer.bukkit;

import com.proxytracer.common.LoginDecision;
import com.proxytracer.common.ProxyTracerConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

final class ProxyTracerLoginListener implements Listener {
    private final ProxyTracerPlugin plugin;

    ProxyTracerLoginListener(ProxyTracerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player connection verification asynchronously.
     * Note: AsyncPlayerPreLoginEvent is executed on Bukkit's Netty network threads,
     * completely off the main server tick thread. Blocking this thread waiting for the
     * API lookup has zero impact on server TPS (Ticks Per Second).
     * WARNING: Do not change this listener to run on synchronous events (like PlayerJoinEvent 
     * or PlayerLoginEvent) as doing so would block the main thread and cause TPS spikes.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        ProxyTracerConfig config = plugin.getProxyTracerConfig();
        String playerName = event.getName();
        String ip = plugin.getCheckService().resolveLoginIp(event.getAddress(), config);

        // Permission bypass is not reliably available at pre-login; name/IP whitelist covers ops testing.
        LoginDecision decision = plugin.getCheckService().checkLogin(playerName, ip, false, config);

        switch (decision.getOutcome()) {
            case DENY:
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, decision.getKickMessage());
                if (decision.isApiError()) {
                    plugin.getDiscordNotifier().notifyError(playerName, ip, decision.getReason());
                    notifyAdmins(ChatColor.RED + "[ProxyTracer] Blocked " + playerName
                            + " because the API check failed.", config);
                } else {
                    plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "block");
                    notifyAdmins(ChatColor.RED + "[ProxyTracer] Blocked " + playerName
                            + " from " + ip + " (proxy/VPN).", config);
                }
                break;
            case WARN:
                if (decision.isApiError()) {
                    plugin.getDiscordNotifier().notifyError(playerName, ip, decision.getReason());
                    notifyAdmins(ChatColor.YELLOW + "[ProxyTracer] API check failed for "
                            + playerName + " from " + ip + ".", config);
                } else {
                    plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "warn");
                    notifyAdmins(ChatColor.YELLOW + "[ProxyTracer] " + playerName
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

    private void notifyAdmins(String message, ProxyTracerConfig config) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(config.getNotifyPermission())) {
                    player.sendMessage(message);
                }
            }
        });
    }
}
