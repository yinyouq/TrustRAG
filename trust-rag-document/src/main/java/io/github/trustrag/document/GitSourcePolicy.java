package io.github.trustrag.document;

import io.github.trustrag.core.exception.InvalidRagRequestException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * GitSourcePolicy 定义安全或可见性策略，集中校验调用方是否满足约束。
 */
public final class GitSourcePolicy {

    private final DocumentImportSettings settings;

    public GitSourcePolicy(DocumentImportSettings settings) {
        this.settings = settings;
    }

    public String validate(String sourceUri) {
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new InvalidRagRequestException("Git repository URI must not be blank");
        }
        String normalized = sourceUri.trim();
        Path directPath = pathIfLocal(normalized);
        if (directPath != null) {
            validateLocalPath(directPath);
            return normalized;
        }
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw new InvalidRagRequestException("Invalid Git repository URI");
        }
        if (uri.getScheme() == null || "file".equalsIgnoreCase(uri.getScheme())) {
            Path localPath = uri.getScheme() == null ? Path.of(normalized) : Path.of(uri);
            validateLocalPath(localPath);
            return normalized;
        }
        if (!settings.gitRemoteEnabled()) {
            throw new InvalidRagRequestException("Remote Git imports are disabled");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!List.of("https", "http", "ssh", "git").contains(scheme)) {
            throw new InvalidRagRequestException("Unsupported Git URI scheme: " + scheme);
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new InvalidRagRequestException(
                    "Git URI must not contain credentials, query parameters, or fragments");
        }
        String host = uri.getHost();
        if (host == null || !settings.gitAllowedHosts().contains(host.toLowerCase(Locale.ROOT))) {
            throw new InvalidRagRequestException("Git host is not in the configured allowlist");
        }
        return normalized;
    }

    public String cloneUri(String sourceUri) {
        Path localPath = pathIfLocal(sourceUri);
        try {
            return localPath == null ? sourceUri : localPath.toRealPath().toUri().toString();
        } catch (java.io.IOException exception) {
            throw new InvalidRagRequestException("Local Git repository cannot be resolved");
        }
    }

    private Path pathIfLocal(String sourceUri) {
        try {
            Path path = Path.of(sourceUri);
            return Files.isDirectory(path) ? path : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void validateLocalPath(Path localPath) {
        if (!Files.isDirectory(localPath)) {
            throw new InvalidRagRequestException("Local Git repository does not exist");
        }
        try {
            Path realPath = localPath.toRealPath();
            boolean allowed = settings.gitAllowedLocalRoots().stream()
                    .map(this::realOrNormalized)
                    .anyMatch(realPath::startsWith);
            if (!allowed) {
                throw new InvalidRagRequestException(
                        "Local Git repository is outside the configured allowlist");
            }
        } catch (java.io.IOException exception) {
            throw new InvalidRagRequestException("Local Git repository cannot be resolved");
        }
    }

    private Path realOrNormalized(Path path) {
        try {
            return path.toRealPath();
        } catch (java.io.IOException ignored) {
            return path.toAbsolutePath().normalize();
        }
    }
}
