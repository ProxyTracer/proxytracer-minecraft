package com.proxytracer.common;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Runtime whitelist of player names (case-insensitive) and IPs (exact match).
 * Merges config defaults with a persistent {@code whitelist.yml} file.
 */
public final class WhitelistStore {
    private final Path file;
    private final Set<String> namesLower = Collections.synchronizedSet(new TreeSet<>());
    private final Set<String> ips = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Consumer<String> warn;

    public WhitelistStore(Path file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn == null ? s -> {
        } : warn;
    }

    public void reload(ProxyTracerConfig config) {
        namesLower.clear();
        ips.clear();

        if (config != null) {
            for (String name : config.getConfigWhitelistNames()) {
                addNameInternal(name);
            }
            for (String ip : config.getConfigWhitelistIps()) {
                addIpInternal(ip);
            }
        }

        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(in);
                if (loaded instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) loaded;
                    for (String name : stringList(map.get("names"))) {
                        addNameInternal(name);
                    }
                    for (String ip : stringList(map.get("ips"))) {
                        addIpInternal(ip);
                    }
                }
            } catch (Exception e) {
                warn.accept("Failed to load whitelist.yml: " + e.getMessage());
            }
        }
    }

    public boolean isNameWhitelisted(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return namesLower.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean isIpWhitelisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return ips.contains(normalizeIp(ip));
    }

    public boolean addName(String name) {
        if (!addNameInternal(name)) {
            return false;
        }
        save();
        return true;
    }

    public boolean removeName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        boolean removed = namesLower.remove(name.toLowerCase(Locale.ROOT));
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean addIp(String ip) {
        if (!addIpInternal(ip)) {
            return false;
        }
        save();
        return true;
    }

    public boolean removeIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        boolean removed = ips.remove(normalizeIp(ip));
        if (removed) {
            save();
        }
        return removed;
    }

    public List<String> listNames() {
        synchronized (namesLower) {
            return new ArrayList<>(namesLower);
        }
    }

    public List<String> listIps() {
        synchronized (ips) {
            return new ArrayList<>(ips);
        }
    }

    public int nameCount() {
        return namesLower.size();
    }

    public int ipCount() {
        return ips.size();
    }

    public void save() {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> data = Map.of(
                    "names", listNames(),
                    "ips", listIps()
            );
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            warn.accept("Failed to save whitelist.yml: " + e.getMessage());
        }
    }

    private boolean addNameInternal(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return namesLower.add(name.trim().toLowerCase(Locale.ROOT));
    }

    private boolean addIpInternal(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        return ips.add(normalizeIp(ip.trim()));
    }

    public static String normalizeIp(String ip) {
        if (ip == null) {
            return "";
        }
        String cleaned = ip.trim();
        // Strip IPv6 zone id (e.g. fe80::1%eth0)
        int zone = cleaned.indexOf('%');
        if (zone >= 0) {
            cleaned = cleaned.substring(0, zone);
        }
        // Normalize IPv6-mapped IPv4
        if (cleaned.startsWith("::ffff:")) {
            cleaned = cleaned.substring(7);
        }
        return cleaned;
    }

    private static List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        if (!(o instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) o) {
            if (item != null) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
