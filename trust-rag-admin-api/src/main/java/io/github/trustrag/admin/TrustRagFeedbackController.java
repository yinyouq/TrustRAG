package io.github.trustrag.admin;

import io.github.trustrag.core.model.FeedbackType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.service.TrustRagFeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/trust-rag/feedback")
public final class TrustRagFeedbackController {

    private final TrustRagFeedbackService feedbackService;

    public TrustRagFeedbackController(TrustRagFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeedbackResponse submit(@Valid @RequestBody FeedbackRequest request) {
        FeedbackType type = FeedbackType.valueOf(request.feedbackType().toUpperCase(Locale.ROOT));
        Optional<KnowledgeItem> candidate = feedbackService.submitFeedback(new RagFeedbackRequest(
                request.traceId(),
                type,
                request.feedbackContent(),
                request.correctedAnswer(),
                request.userId(),
                request.conversationId(),
                request.projectId(),
                request.tenantId()));
        return new FeedbackResponse(true, candidate.map(KnowledgeItem::id).orElse(null));
    }

    public record FeedbackRequest(
            @NotBlank String traceId,
            @NotBlank String feedbackType,
            String feedbackContent,
            String correctedAnswer,
            String userId,
            String conversationId,
            String projectId,
            String tenantId) {
    }

    public record FeedbackResponse(boolean accepted, Long candidateKnowledgeId) {
    }
}
