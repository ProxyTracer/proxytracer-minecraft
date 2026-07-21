package com.proxytracer.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class IpCache {
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    public synchronized Optional<CheckResult> get(String ip, Duration ttl) {
        Entry entry = entries.get(ip);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.createdAt.plus(ttl).isBefore(Instant.now())) {
            entries.remove(ip);
            return Optional.empty();
        }
        return Optional.of(entry.result);
    }

    public synchronized void put(String ip, CheckResult result, int maxEntries) {
        entries.put(ip, new Entry(result, Instant.now()));
        trim(maxEntries);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    private void trim(int maxEntries) {
        Iterator<String> iterator = entries.keySet().iterator();
        while (entries.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class Entry {
        private final CheckResult result;
        private final Instant createdAt;

        private Entry(CheckResult result, Instant createdAt) {
            this.result = result;
            this.createdAt = createdAt;
        }
    }
}
