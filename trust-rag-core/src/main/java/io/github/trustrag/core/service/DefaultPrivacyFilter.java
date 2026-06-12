package io.github.trustrag.core.service;

import io.github.trustrag.core.model.PrivacyResult;
import io.github.trustrag.core.spi.PrivacyFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DefaultPrivacyFilter implements PrivacyFilter {

    private static final Pattern API_KEY = Pattern.compile(
            "(?i)(api[_-]?key|secret|token)\\s*[:=]\\s*['\\\"]?[a-z0-9_\\-]{12,}");
    private static final Pattern DB_URL = Pattern.compile(
            "(?i)jdbc:(postgresql|mysql|mariadb|oracle|sqlserver):[^\\s]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private final boolean blockApiKey;
    private final boolean blockDbUrl;
    private final boolean blockPhone;

    public DefaultPrivacyFilter(boolean blockApiKey, boolean blockDbUrl, boolean blockPhone) {
        this.blockApiKey = blockApiKey;
        this.blockDbUrl = blockDbUrl;
        this.blockPhone = blockPhone;
    }

    @Override
    public PrivacyResult filter(String content) {
        if (content == null || content.isBlank()) {
            return new PrivacyResult(false, "", 1.0, "Content is empty");
        }
        String sanitized = content;
        List<String> risks = new ArrayList<>();
        if (blockApiKey && API_KEY.matcher(sanitized).find()) {
            sanitized = API_KEY.matcher(sanitized).replaceAll("$1=[REDACTED]");
            risks.add("credential");
        }
        if (blockDbUrl && DB_URL.matcher(sanitized).find()) {
            sanitized = DB_URL.matcher(sanitized).replaceAll("jdbc:[REDACTED]");
            risks.add("database_url");
        }
        if (blockPhone && PHONE.matcher(sanitized).find()) {
            sanitized = PHONE.matcher(sanitized).replaceAll("[PHONE_REDACTED]");
            risks.add("phone");
        }
        if (risks.isEmpty()) {
            return PrivacyResult.allowed(sanitized);
        }
        return new PrivacyResult(false, sanitized, Math.min(1.0, risks.size() * 0.4), String.join(",", risks));
    }
}
