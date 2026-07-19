package com.proxytracer.common;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiClient {
    private static final Pattern IP_PATTERN = Pattern.compile("\"ip\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PROXY_PATTERN = Pattern.compile("\"proxy\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public ApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CheckResult check(String ip, ProxyTracerConfig config) throws ProxyTracerApiException {
        if (!config.hasApiKey()) {
            throw new ProxyTracerApiException("API key is not configured");
        }

        String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
        URI uri = URI.create(config.getApiBaseUrl() + "/check/" + encodedIp);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ProxyTracerApiException("API request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProxyTracerApiException("API request was interrupted", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new ProxyTracerApiException("API returned HTTP " + status);
        }

        return parseResponse(response.body(), ip);
    }

    private CheckResult parseResponse(String body, String fallbackIp) throws ProxyTracerApiException {
        Matcher proxyMatcher = PROXY_PATTERN.matcher(body == null ? "" : body);
        if (!proxyMatcher.find()) {
            throw new ProxyTracerApiException("API response did not include proxy boolean");
        }

        String resultIp = fallbackIp;
        Matcher ipMatcher = IP_PATTERN.matcher(body);
        if (ipMatcher.find()) {
            resultIp = ipMatcher.group(1);
        }

        return new CheckResult(resultIp, Boolean.parseBoolean(proxyMatcher.group(1)));
    }
}
