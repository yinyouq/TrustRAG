package io.github.trustrag.example;

import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.service.TrustRagEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例问答入口，用最小请求体演示 TrustRagEngine 的调用方式。
 */
@RestController
@RequestMapping("/api/chat")
public final class ChatController {

    private final TrustRagEngine trustRagEngine;

    public ChatController(TrustRagEngine trustRagEngine) {
        this.trustRagEngine = trustRagEngine;
    }

    @PostMapping
    public RagAnswer chat(@Valid @RequestBody ChatRequest request) {
        return trustRagEngine.ask(RagRequest.builder()
                .question(request.question())
                .userId(request.userId())
                .conversationId(request.conversationId())
                .projectId(request.projectId())
                .tenantId(request.tenantId())
                .build());
    }

    public record ChatRequest(
            @NotBlank String question,
            String userId,
            String conversationId,
            String projectId,
            String tenantId) {
    }
}
