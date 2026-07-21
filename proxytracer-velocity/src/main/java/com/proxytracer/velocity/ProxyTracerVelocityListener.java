package com.proxytracer.velocity;

import com.proxytracer.common.LoginDecision;
import com.proxytracer.common.ProxyTracerConfig;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetSocketAddress;

final class ProxyTracerVelocityListener {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final LegacyComponentSerializer SECTION =
            LegacyComponentSerializer.legacySection();

    private final ProxyTracerVelocityPlugin plugin;

    ProxyTracerVelocityListener(ProxyTracerVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        ProxyTracerConfig config = plugin.getConfig();
        String playerName = player.getUsername();
        String ip = resolveIp(player, config);
        boolean bypass = player.hasPermission(config.getBypassPermission());

        return EventTask.async(() -> {
            LoginDecision decision = plugin.getCheckService().checkLogin(playerName, ip, bypass, config);

            switch (decision.getOutcome()) {
                case DENY:
                    event.setResult(ResultedEvent.ComponentResult.denied(sectionOrLegacy(decision.getKickMessage())));
                    if (decision.isApiError()) {
                        plugin.getDiscordNotifier().notifyError(playerName, ip, decision.getReason());
                        notifyStaff("&c[ProxyTracer] Blocked " + playerName + " because the API check failed.", config);
                    } else {
                        plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "block");
                        notifyStaff("&c[ProxyTracer] Blocked " + playerName + " from " + ip + " (proxy/VPN).", config);
                    }
                    break;
                case WARN:
                    if (decision.isApiError()) {
                        plugin.getDiscordNotifier().notifyError(playerName, ip, decision.getReason());
                        notifyStaff("&e[ProxyTracer] API check failed for " + playerName + " from " + ip + ".", config);
                    } else {
                        plugin.getDiscordNotifier().notifyBlocked(playerName, ip, "warn");
                        notifyStaff("&e[ProxyTracer] " + playerName + " joined from " + ip + " flagged as proxy/VPN.", config);
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
        });
    }

    private String resolveIp(Player player, ProxyTracerConfig config) {
        if (config.isDebugEnabled() && !config.getDebugOverrideLoginIp().isEmpty()) {
            return plugin.getCheckService().resolveLoginIp(null, config);
        }
        InetSocketAddress remote = player.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return plugin.getCheckService().resolveLoginIp(remote.getAddress(), config);
    }

    private void notifyStaff(String legacyMessage, ProxyTracerConfig config) {
        var component = LEGACY.deserialize(legacyMessage);
        for (Player player : plugin.getServer().getAllPlayers()) {
            if (player.hasPermission(config.getNotifyPermission())) {
                player.sendMessage(component);
            }
        }
    }

    private static net.kyori.adventure.text.Component sectionOrLegacy(String message) {
        // Kick messages from common are already section-sign colorized.
        if (message == null) {
            return net.kyori.adventure.text.Component.text("Blocked by ProxyTracer");
        }
        if (message.indexOf('\u00A7') >= 0) {
            return SECTION.deserialize(message);
        }
        return LEGACY.deserialize(message);
    }
}
