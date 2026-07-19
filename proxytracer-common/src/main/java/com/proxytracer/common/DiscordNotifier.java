package com.proxytracer.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DiscordNotifier {
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Supplier<ProxyTracerConfig> configSupplier;
    private final Consumer<String> warnLogger;

    public DiscordNotifier(Supplier<ProxyTracerConfig> configSupplier, Consumer<String> warnLogger) {
        this.configSupplier = configSupplier;
        this.warnLogger = warnLogger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "proxytracer-discord");
            t.setDaemon(true);
            return t;
        });
    }

    public void notifyAllowed(String playerName, String ip) {
        ProxyTracerConfig config = configSupplier.get();
        if (config != null && config.isDiscordNotifyAllowed()) {
            send(config, "ProxyTracer allowed " + playerName + " from " + ip + ".");
        }
    }

    public void notifyBlocked(String playerName, String ip, String action) {
        ProxyTracerConfig config = configSupplier.get();
        if (config != null && config.isDiscordNotifyBlocked()) {
            send(config, "ProxyTracer detected proxy/VPN for " + playerName + " from " + ip + ". Action: " + action + ".");
        }
    }

    public void notifyError(String playerName, String ip, String error) {
        ProxyTracerConfig config = configSupplier.get();
        if (config != null && config.isDiscordNotifyErrors()) {
            send(config, "ProxyTracer API error for " + playerName + " from " + ip + ": " + error);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void send(ProxyTracerConfig config, String message) {
        if (!config.isDiscordEnabled() || config.getDiscordWebhookUrl().isEmpty()) {
            return;
        }

        executor.execute(() -> {
            String body = "{\"content\":\"" + escapeJson(message) + "\"}";
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.getDiscordWebhookUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    warnLogger.accept("Discord webhook returned HTTP " + response.statusCode());
                }
            } catch (IOException e) {
                warnLogger.accept("Discord webhook failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                warnLogger.accept("Discord webhook was interrupted");
            } catch (IllegalArgumentException e) {
                warnLogger.accept("Discord webhook URL is invalid: " + e.getMessage());
            }
        });
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
