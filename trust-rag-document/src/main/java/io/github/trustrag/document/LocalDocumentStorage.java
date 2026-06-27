package io.github.trustrag.document;

import io.github.trustrag.core.exception.InvalidRagRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地上传文件存储。
 *
 * <p>所有文件都限制在配置的 storageRoot 内，写入时使用临时文件再移动，
 * 避免半写入文件被 Worker 消费。</p>
 */
public final class LocalDocumentStorage {

    private final Path root;
    private final long maxUploadBytes;

    public LocalDocumentStorage(DocumentImportSettings settings) {
        this.root = settings.storageRoot().toAbsolutePath().normalize();
        this.maxUploadBytes = settings.maxUploadBytes();
    }

    public Path store(String filename, InputStream input, long declaredSize) throws IOException {
        if (input == null) {
            throw new InvalidRagRequestException("Uploaded file must not be empty");
        }
        if (declaredSize > maxUploadBytes) {
            throw new InvalidRagRequestException("Uploaded file exceeds the configured size limit");
        }
        Files.createDirectories(root);
        String safeFilename = safeFilename(filename);
        Path directory = root.resolve("uploads").resolve(UUID.randomUUID().toString()).normalize();
        verifyInsideRoot(directory);
        Files.createDirectories(directory);
        Path target = directory.resolve(safeFilename).normalize();
        Path temporary = directory.resolve(safeFilename + ".part").normalize();
        verifyInsideRoot(target);
        long written = 0;
        try (InputStream source = input;
             OutputStream output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                written += read;
                if (written > maxUploadBytes) {
                    throw new InvalidRagRequestException(
                            "Uploaded file exceeds the configured size limit");
                }
                output.write(buffer, 0, read);
            }
        } catch (RuntimeException | IOException exception) {
            Files.deleteIfExists(temporary);
            deleteEmptyDirectory(directory);
            throw exception;
        }
        if (written == 0) {
            Files.deleteIfExists(temporary);
            deleteEmptyDirectory(directory);
            throw new InvalidRagRequestException("Uploaded file must not be empty");
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            // 部分文件系统不支持原子移动，退化为替换移动仍能保证目标路径受控。
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public void delete(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        verifyInsideRoot(normalized);
        try {
            Files.deleteIfExists(normalized);
            deleteEmptyDirectory(normalized.getParent());
        } catch (IOException ignored) {
            // Source retention cleanup is best-effort and must not corrupt task state.
        }
    }

    public Path requireStoredFile(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IOException("Document storage path is missing");
        }
        Path path = Path.of(storagePath).toAbsolutePath().normalize();
        verifyInsideRoot(path);
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IOException("Uploaded source file no longer exists");
        }
        Path realPath = path.toRealPath();
        verifyInsideRoot(realPath);
        return realPath;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.bin";
        }
        String normalized = filename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String value = normalized.substring(separator + 1)
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                .trim();
        return value.isBlank() ? "document.bin" : value;
    }

    private void verifyInsideRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new IllegalArgumentException("Document path escapes the configured storage root");
        }
    }

    private void deleteEmptyDirectory(Path directory) throws IOException {
        if (directory == null || directory.equals(root) || !Files.isDirectory(directory)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }
}
