package com.proxytracer.common;

/**
 * Formats kick / chat messages with placeholders and {@code &} color codes.
 * Output uses section-sign ({@code §}) legacy colors for all platforms.
 */
public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static String colorize(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        String normalized = value.replace("\\n", "\n");
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '&' && i + 1 < normalized.length()) {
                char code = normalized.charAt(i + 1);
                if (isColorCode(code)) {
                    out.append('\u00A7').append(Character.toLowerCase(code));
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String formatKick(String template, String url, String ip, String player) {
        String text = template == null ? "" : template;
        text = text.replace("{url}", url == null ? "https://proxytracer.com" : url);
        text = text.replace("{ip}", ip == null ? "unknown" : ip);
        text = text.replace("{player}", player == null ? "unknown" : player);
        return colorize(text);
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')
                || (code >= 'A' && code <= 'F')
                || code == 'k' || code == 'K'
                || code == 'l' || code == 'L'
                || code == 'm' || code == 'M'
                || code == 'n' || code == 'N'
                || code == 'o' || code == 'O'
                || code == 'r' || code == 'R'
                || code == 'x' || code == 'X';
    }
}
