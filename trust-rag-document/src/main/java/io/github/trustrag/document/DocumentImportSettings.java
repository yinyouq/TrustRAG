package io.github.trustrag.document;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record DocumentImportSettings(
        Path storageRoot,
        long maxUploadBytes,
        long maxGitFileBytes,
        int maxGitFiles,
        int batchSize,
        int retryLimit,
        boolean keepSourceFiles,
        boolean gitRemoteEnabled,
        Set<String> gitAllowedHosts,
        Set<Path> gitAllowedLocalRoots,
        Set<String> allowedExtensions) {

    public DocumentImportSettings {
        if (storageRoot == null) {
            throw new IllegalArgumentException("storageRoot must not be null");
        }
        if (maxUploadBytes < 1 || maxGitFileBytes < 1 || maxGitFiles < 1
                || batchSize < 1 || retryLimit < 1) {
            throw new IllegalArgumentException("Document import limits must be positive");
        }
        gitAllowedHosts = normalize(gitAllowedHosts);
        gitAllowedLocalRoots = gitAllowedLocalRoots == null
                ? Set.of()
                : gitAllowedLocalRoots.stream()
                        .filter(value -> value != null)
                        .map(value -> value.toAbsolutePath().normalize())
                        .collect(Collectors.toUnmodifiableSet());
        allowedExtensions = normalize(allowedExtensions);
        if (allowedExtensions.isEmpty()) {
            throw new IllegalArgumentException("allowedExtensions must not be empty");
        }
    }

    public static Set<String> normalize(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<Path> normalizePaths(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> Path.of(value.trim()).toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
