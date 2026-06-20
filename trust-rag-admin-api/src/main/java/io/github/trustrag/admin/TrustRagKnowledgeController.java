package io.github.trustrag.admin;

import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.KnowledgeImportRequest;
import io.github.trustrag.core.model.KnowledgeImportResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.PrivacyResult;
import io.github.trustrag.core.model.ReviewRequest;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.service.CandidateKnowledgeService;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import io.github.trustrag.core.service.KnowledgeReviewService;
import io.github.trustrag.core.spi.PrivacyFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/trust-rag/admin/knowledge")
public final class TrustRagKnowledgeController {

    private final KnowledgeReviewService reviewService;
    private final KnowledgeIngestionService ingestionService;
    private final CandidateKnowledgeService candidateService;
    private final PrivacyFilter privacyFilter;

    public TrustRagKnowledgeController(
            KnowledgeReviewService reviewService,
            KnowledgeIngestionService ingestionService,
            CandidateKnowledgeService candidateService,
            PrivacyFilter privacyFilter) {
        this.reviewService = reviewService;
        this.ingestionService = ingestionService;
        this.candidateService = candidateService;
        this.privacyFilter = privacyFilter;
    }

    @GetMapping("/candidates")
    public List<KnowledgeItem> candidates(
            @RequestParam(defaultValue = "HUMAN_REVIEW_PENDING") String status,
            @RequestParam(required = false) String trustLevel,
            @RequestParam(required = false) String scopeType,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return reviewService.findCandidates(
                KnowledgeStatus.valueOf(status.toUpperCase(Locale.ROOT)),
                parseOptionalTrust(trustLevel),
                parseOptionalScope(scopeType),
                limit,
                offset);
    }

    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeItem createCandidate(@Valid @RequestBody ManualCandidateRequest request) {
        PrivacyResult privacy = privacyFilter.filter(request.content());
        if (!privacy.allowed()) {
            throw new InvalidRagRequestException("Candidate contains sensitive content: " + privacy.reason());
        }
        ScopeType scope = request.scopeType() == null || request.scopeType().isBlank()
                ? ScopeType.GLOBAL_CANDIDATE
                : ScopeType.valueOf(request.scopeType().toUpperCase(Locale.ROOT));
        CandidateKnowledge candidate = new CandidateKnowledge(
                request.title(),
                request.claim(),
                privacy.sanitizedContent(),
                request.evidence(),
                "manual",
                request.sourceRef(),
                scope,
                TrustLevel.LOW,
                KnowledgeStatus.LOW_PENDING,
                0.80,
                0.0,
                request.tags());
        return candidateService.submit(candidate, request.scopeContext());
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeImportResult importKnowledge(@Valid @RequestBody ImportRequest request) {
        return ingestionService.importKnowledge(new KnowledgeImportRequest(
                request.title(),
                request.content(),
                request.sourceType(),
                request.sourceRef(),
                parseTrust(request.trustLevel()),
                parseScope(request.scopeType()),
                request.userId(),
                request.conversationId(),
                request.projectId(),
                request.tenantId()));
    }

    @PostMapping("/{id}/approve")
    public KnowledgeItem approve(@PathVariable long id, @Valid @RequestBody ReviewBody request) {
        return reviewService.approve(id, request.toCore());
    }

    @PostMapping("/{id}/approve-high")
    public KnowledgeItem approveHigh(@PathVariable long id, @Valid @RequestBody ReviewBody request) {
        return reviewService.approve(id, request.toCore());
    }

    @PostMapping("/{id}/reject")
    public KnowledgeItem reject(@PathVariable long id, @Valid @RequestBody ReviewBody request) {
        return reviewService.reject(id, request.toCore());
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

    private TrustLevel parseOptionalTrust(String value) {
        return value == null || value.isBlank()
                ? null
                : TrustLevel.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private ScopeType parseOptionalScope(String value) {
        return value == null || value.isBlank()
                ? null
                : ScopeType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public record ReviewBody(
            @NotBlank String reviewerId,
            String comment,
            String modifiedTitle,
            String modifiedContent) {

        ReviewRequest toCore() {
            return new ReviewRequest(reviewerId, comment, modifiedTitle, modifiedContent);
        }
    }

    public record ManualCandidateRequest(
            @NotBlank String title,
            String claim,
            @NotBlank String content,
            String evidence,
            String sourceRef,
            String scopeType,
            String userId,
            String conversationId,
            String projectId,
            String tenantId,
            List<String> tags) {

        ScopeContext scopeContext() {
            return new ScopeContext(userId, conversationId, projectId, tenantId);
        }
    }

    public record ImportRequest(
            @NotBlank String title,
            @NotBlank String content,
            String sourceType,
            String sourceRef,
            String trustLevel,
            String scopeType,
            String userId,
            String conversationId,
            String projectId,
            String tenantId) {
    }
}
