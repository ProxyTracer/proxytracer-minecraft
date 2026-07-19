package com.proxytracer.common;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Load platform-neutral config.yml with SnakeYAML.
 */
public final class YamlConfigLoader {
    private YamlConfigLoader() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> load(Path configFile, Consumer<String> warn) {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            return Collections.emptyMap();
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        } catch (Exception e) {
            if (warn != null) {
                warn.accept("Failed to load config.yml: " + e.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    public static void copyDefaultIfMissing(Path configFile, InputStream defaultResource) throws IOException {
        if (configFile == null || defaultResource == null) {
            return;
        }
        if (Files.isRegularFile(configFile)) {
            return;
        }
        Path parent = configFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream in = defaultResource; OutputStream out = Files.newOutputStream(configFile)) {
            in.transferTo(out);
        }
    }

    /**
     * Updates {@code api.key} in config.yml and writes the file back.
     * Note: round-trip may reformat / drop comments.
     */
    @SuppressWarnings("unchecked")
    public static void setApiKey(Path configFile, String apiKey) throws IOException {
        if (configFile == null) {
            throw new IOException("config path is null");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API key is empty");
        }

        Path parent = configFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Object> root = new LinkedHashMap<>(load(configFile, null));
        Object apiObj = root.get("api");
        Map<String, Object> api;
        if (apiObj instanceof Map) {
            api = new LinkedHashMap<>((Map<String, Object>) apiObj);
        } else {
            api = new LinkedHashMap<>();
        }
        api.put("key", apiKey.trim());
        root.put("api", api);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
            yaml.dump(root, writer);
        }
    }

    /** Mask for chat: show prefix only. */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "(empty)";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "********";
        }
        return key.substring(0, 8) + "…";
    }
}
