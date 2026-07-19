package com.proxytracer.common;

public enum ActionMode {
    ALLOW,
    BLOCK,
    WARN,
    LOG;

    public static ActionMode parse(String value, ActionMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return ActionMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
