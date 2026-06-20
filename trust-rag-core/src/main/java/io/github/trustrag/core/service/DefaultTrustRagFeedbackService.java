package io.github.trustrag.core.service;

import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.model.CandidateKnowledge;
import io.github.trustrag.core.model.FeedbackType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PrivacyResult;
import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.spi.CandidateExtractor;
import io.github.trustrag.core.spi.FeedbackRepository;
import io.github.trustrag.core.spi.KnowledgeRepository;
import io.github.trustrag.core.spi.PrivacyFilter;
import io.github.trustrag.core.spi.RagTraceRepository;
import io.github.trustrag.core.spi.ScopeClassifier;
import io.github.trustrag.core.spi.TransactionRunner;

import java.util.Optional;
import java.util.List;

public final class DefaultTrustRagFeedbackService implements TrustRagFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final RagTraceRepository traceRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final PrivacyFilter privacyFilter;
    private final ScopeClassifier scopeClassifier;
    private final CandidateExtractor candidateExtractor;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final TransactionRunner transactionRunner;

    public DefaultTrustRagFeedbackService(
            FeedbackRepository feedbackRepository,
            RagTraceRepository traceRepository,
            KnowledgeRepository knowledgeRepository,
            PrivacyFilter privacyFilter,
            ScopeClassifier scopeClassifier,
            CandidateExtractor candidateExtractor,
            CandidateKnowledgeService candidateKnowledgeService,
            TransactionRunner transactionRunner) {
        this.feedbackRepository = feedbackRepository;
        this.traceRepository = traceRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.privacyFilter = privacyFilter;
        this.scopeClassifier = scopeClassifier;
        this.candidateExtractor = candidateExtractor;
        this.candidateKnowledgeService = candidateKnowledgeService;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public Optional<KnowledgeItem> submitFeedback(RagFeedbackRequest request) {
        validate(request);
        PrivacyResult feedbackPrivacy = sanitizeOptional(request.feedbackContent());
        PrivacyResult correctionPrivacy = sanitizeOptional(request.correctedAnswer());
        RagFeedbackRequest sanitized = new RagFeedbackRequest(
                request.traceId(), request.feedbackType(), feedbackPrivacy.sanitizedContent(),
                correctionPrivacy.sanitizedContent(), request.userId(), request.conversationId(),
                request.projectId(), request.tenantId());
        if (request.feedbackType() != FeedbackType.CORRECTION) {
            transactionRunner.required(() -> {
                feedbackRepository.save(sanitized);
                updateFeedbackCounters(request);
            });
            return Optional.empty();
        }
        if (correctionPrivacy.sanitizedContent() == null || correctionPrivacy.sanitizedContent().isBlank()) {
            throw new InvalidRagRequestException("correctedAnswer must not be blank for correction feedback");
        }

        ScopeContext context = new ScopeContext(
                request.userId(), request.conversationId(), request.projectId(), request.tenantId());
        ScopeType scope = scopeClassifier.classify(correctionPrivacy.sanitizedContent(), context);
        if (!correctionPrivacy.allowed() || !feedbackPrivacy.allowed()) {
            scope = narrowestPrivateScope(context);
        }
        CandidateKnowledge candidate = candidateExtractor.extractCorrection(
                sanitized, correctionPrivacy.sanitizedContent(), scope)
                .withPrivacyRisk(Math.max(
                        correctionPrivacy.allowed() ? 0.0 : Math.max(0.80, correctionPrivacy.riskScore()),
                        feedbackPrivacy.allowed() ? 0.0 : Math.max(0.80, feedbackPrivacy.riskScore())));
        return transactionRunner.required(() -> {
            feedbackRepository.save(sanitized);
            updateFeedbackCounters(request);
            return Optional.of(candidateKnowledgeService.submit(candidate, context));
        });
    }

    private void updateFeedbackCounters(RagFeedbackRequest request) {
        List<Long> knowledgeIds = traceRepository.findUsedKnowledgeIds(request.traceId());
        boolean positive = request.feedbackType() == FeedbackType.LIKE;
        boolean negative = switch (request.feedbackType()) {
            case DISLIKE, CORRECTION, IRRELEVANT_CONTEXT, WRONG_ANSWER, MISSING_KNOWLEDGE -> true;
            case LIKE -> false;
        };
        knowledgeRepository.incrementFeedbackCounts(knowledgeIds, positive, negative);
    }

    private PrivacyResult sanitizeOptional(String value) {
        return value == null || value.isBlank() ? PrivacyResult.allowed(value) : privacyFilter.filter(value);
    }

    private ScopeType narrowestPrivateScope(ScopeContext context) {
        if (hasText(context.conversationId())) {
            return ScopeType.CONVERSATION;
        }
        if (hasText(context.userId())) {
            return ScopeType.USER;
        }
        if (hasText(context.projectId())) {
            return ScopeType.PROJECT;
        }
        if (hasText(context.tenantId())) {
            return ScopeType.TENANT;
        }
        throw new InvalidRagRequestException("Sensitive correction requires a conversation, user, project, or tenant scope");
    }

    private void validate(RagFeedbackRequest request) {
        if (request == null || request.traceId() == null || request.traceId().isBlank()) {
            throw new InvalidRagRequestException("traceId must not be blank");
        }
        if (request.feedbackType() == null) {
            throw new InvalidRagRequestException("feedbackType must not be null");
        }
        if (!traceRepository.existsByTraceId(request.traceId())) {
            throw new InvalidRagRequestException("Unknown traceId: " + request.traceId());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
