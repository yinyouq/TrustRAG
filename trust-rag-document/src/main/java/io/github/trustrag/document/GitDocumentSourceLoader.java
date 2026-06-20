package io.github.trustrag.document;

import org.apache.tika.Tika;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GitDocumentSourceLoader implements DocumentSourceLoader {

    private final DocumentImportSettings settings;
    private final GitSourcePolicy sourcePolicy;
    private final Path storageRoot;
    private final Tika tika = new Tika();

    public GitDocumentSourceLoader(
            DocumentImportSettings settings,
            GitSourcePolicy sourcePolicy) {
        this.settings = settings;
        this.sourcePolicy = sourcePolicy;
        this.storageRoot = settings.storageRoot().toAbsolutePath().normalize();
    }

    @Override
    public boolean supports(DocumentSourceKind sourceKind) {
        return sourceKind == DocumentSourceKind.GIT;
    }

    @Override
    public LoadedDocumentSource load(DocumentImportTask task) throws IOException {
        sourcePolicy.validate(task.sourceUri());
        Path cloneDirectory = storageRoot.resolve("git").resolve(task.taskId()).normalize();
        verifyInsideRoot(cloneDirectory);
        deleteTree(cloneDirectory);
        Files.createDirectories(cloneDirectory.getParent());
        try {
            var command = Git.cloneRepository()
                    .setURI(sourcePolicy.cloneUri(task.sourceUri()))
                    .setDirectory(cloneDirectory.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1);
            if (task.gitRef() != null && !task.gitRef().isBlank()) {
                command.setBranch(task.gitRef().trim());
            }
            try (Git ignored = command.call()) {
                // The checked-out working tree is consumed below.
            }
        } catch (GitAPIException exception) {
            deleteTree(cloneDirectory);
            throw new IOException("Git repository clone failed", exception);
        }

        List<DocumentResource> resources = scan(task.sourceUri(), cloneDirectory);
        return new LoadedDocumentSource(resources, () -> deleteTree(cloneDirectory));
    }

    private List<DocumentResource> scan(String repositoryUri, Path root) throws IOException {
        List<DocumentResource> resources = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(value -> !Files.isSymbolicLink(value))
                    .sorted()
                    .toList()) {
                Path relative = root.relativize(path);
                if (relative.startsWith(".git") || !isAllowed(path)) {
                    continue;
                }
                if (Files.size(path) > settings.maxGitFileBytes()) {
                    continue;
                }
                if (resources.size() >= settings.maxGitFiles()) {
                    throw new IOException("Git repository exceeds the configured document file limit");
                }
                String relativePath = relative.toString().replace('\\', '/');
                resources.add(new DocumentResource(
                        path,
                        relativePath,
                        tika.detect(path),
                        repositoryUri + "#" + relativePath));
            }
        }
        if (resources.isEmpty()) {
            throw new IOException("Git repository contains no supported document files");
        }
        return List.copyOf(resources);
    }

    private boolean isAllowed(Path path) {
        String extension = ParserSupport.extension(path.getFileName().toString());
        return settings.allowedExtensions().contains(extension);
    }

    private void verifyInsideRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) {
            throw new IllegalArgumentException("Git work path escapes the configured storage root");
        }
    }

    private void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        Path normalized = root.toAbsolutePath().normalize();
        verifyInsideRoot(normalized);
        if (!Files.exists(normalized)) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Temporary clone cleanup is best-effort.
        }
    }
}
