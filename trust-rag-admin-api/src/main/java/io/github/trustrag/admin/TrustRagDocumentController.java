package io.github.trustrag.admin;

import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.document.DocumentImportOptions;
import io.github.trustrag.document.DocumentImportService;
import io.github.trustrag.document.DocumentImportStatus;
import io.github.trustrag.document.DocumentImportTask;
import io.github.trustrag.document.DocumentSourceKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

/**
 * 文档导入 API。
 *
 * <p>上传和 Git 导入只创建异步任务，调用方通过 taskId 查询解析和入库进度。</p>
 */
@RestController
@RequestMapping("/api/documents")
public final class TrustRagDocumentController {

    private final DocumentImportService service;

    public TrustRagDocumentController(DocumentImportService service) {
        this.service = service;
    }

    @PostMapping(
            path = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentTaskResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(defaultValue = "document") String sourceType,
            @RequestParam(defaultValue = "HIGH") String trustLevel,
            @RequestParam(defaultValue = "GLOBAL") String scopeType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String tenantId) throws IOException {
        DocumentImportTask task = service.submitUpload(
                file.getOriginalFilename(),
                file.getContentType(),
                sourceUrl,
                file.getInputStream(),
                file.getSize(),
                options(
                        title, sourceType, trustLevel, scopeType,
                        userId, conversationId, projectId, tenantId));
        return DocumentTaskResponse.from(task);
    }

    @PostMapping("/git")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentTaskResponse git(@Valid @RequestBody GitImportRequest request) {
        DocumentImportTask task = service.submitGit(
                request.repositoryUri(),
                request.gitRef(),
                options(
                        request.title(), request.sourceType(),
                        request.trustLevel(), request.scopeType(),
                        request.userId(), request.conversationId(),
                        request.projectId(), request.tenantId()));
        return DocumentTaskResponse.from(task);
    }

    @GetMapping("/tasks/{taskId}")
    public DocumentTaskResponse task(@PathVariable String taskId) {
        return DocumentTaskResponse.from(service.find(taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public DocumentTaskResponse retry(@PathVariable String taskId) {
        return DocumentTaskResponse.from(service.retry(taskId));
    }

    private DocumentImportOptions options(
            String title,
            String sourceType,
            String trustLevel,
            String scopeType,
            String userId,
            String conversationId,
            String projectId,
            String tenantId) {
        // API 层只做字符串到领域枚举的转换，作用域合法性由 document/core 服务继续校验。
        return new DocumentImportOptions(
                title,
                sourceType,
                parseTrust(trustLevel),
                parseScope(scopeType),
                userId,
                conversationId,
                projectId,
                tenantId);
    }

    private TrustLevel parseTrust(String value) {
        return value == null || value.isBlank()
                ? TrustLevel.HIGH
                : TrustLevel.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private ScopeType parseScope(String value) {
        return value == null || value.isBlank()
                ? ScopeType.GLOBAL
                : ScopeType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public record GitImportRequest(
            @NotBlank String repositoryUri,
            String gitRef,
            String title,
            String sourceType,
            String trustLevel,
            String scopeType,
            String userId,
            String conversationId,
            String projectId,
            String tenantId) {
    }

    public record DocumentTaskResponse(
            String taskId,
            DocumentSourceKind sourceKind,
            DocumentImportStatus status,
            String originalFilename,
            String sourceUri,
            int totalDocuments,
            int totalSections,
            int importedCount,
            int duplicateCount,
            int failedCount,
            int retryCount,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Instant updatedAt) {

        static DocumentTaskResponse from(DocumentImportTask task) {
            return new DocumentTaskResponse(
                    task.taskId(),
                    task.sourceKind(),
                    task.status(),
                    task.originalFilename(),
                    task.sourceUri(),
                    task.totalDocuments(),
                    task.totalSections(),
                    task.importedCount(),
                    task.duplicateCount(),
                    task.failedCount(),
                    task.retryCount(),
                    task.errorMessage(),
                    task.createdAt(),
                    task.startedAt(),
                    task.finishedAt(),
                    task.updatedAt());
        }
    }
}
