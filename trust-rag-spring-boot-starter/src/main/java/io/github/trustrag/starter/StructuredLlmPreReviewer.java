package io.github.trustrag.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PreReviewResult;
import io.github.trustrag.core.model.PromotionAction;
import io.github.trustrag.core.spi.LlmClient;
import io.github.trustrag.core.spi.LlmPreReviewer;

import java.util.ArrayList;
import java.util.List;

public final class StructuredLlmPreReviewer implements LlmPreReviewer {

    private static final String PROMPT = """
            You are the conservative pre-reviewer for a trusted RAG knowledge pool.
            Evaluate only the supplied content and evidence. Never invent evidence.
            Return exactly one JSON object with:
            qualityScore, generalValueScore, evidenceSufficiencyScore, riskScore (0..1),
            suggestedAction (PROMOTE_TO_MEDIUM, KEEP_LOW, REJECT, MARK_CONFLICT,
            MARK_EXPIRED, or MERGE_PENDING), reason, normalizedClaim, tags (string array).
            Candidate:
            title: %s
            claim: %s
            content: %s
            evidence: %s
            sourceType: %s
            sourceRef: %s
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public StructuredLlmPreReviewer(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PreReviewResult review(KnowledgeItem candidate, List<KnowledgeItem> similarKnowledge) {
        String raw = llmClient.generate(PROMPT.formatted(
                text(candidate.title()), text(candidate.claim()), text(candidate.content()),
                text(candidate.evidence()), text(candidate.sourceType()), text(candidate.sourceRef()))).text();
        try {
            JsonNode json = objectMapper.readTree(stripFence(raw));
            return new PreReviewResult(
                    score(json, "qualityScore"),
                    score(json, "generalValueScore"),
                    score(json, "evidenceSufficiencyScore"),
                    score(json, "riskScore"),
                    action(json.path("suggestedAction").asText()),
                    json.path("reason").asText("No reason supplied"),
                    json.path("normalizedClaim").asText(effectiveClaim(candidate)),
                    tags(json.path("tags")),
                    raw);
        } catch (Exception exception) {
            return PreReviewResult.conservative(
                    effectiveClaim(candidate), "Invalid LLM pre-review JSON: " + exception.getMessage());
        }
    }

    private double score(JsonNode json, String name) {
        return Math.max(0.0, Math.min(1.0, json.path(name).asDouble(0.0)));
    }

    private PromotionAction action(String value) {
        try {
            return PromotionAction.valueOf(value);
        } catch (Exception exception) {
            return PromotionAction.KEEP_LOW;
        }
    }

    private List<String> tags(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private String stripFence(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("```")) {
            int firstLine = result.indexOf('\n');
            int lastFence = result.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                result = result.substring(firstLine + 1, lastFence).trim();
            }
        }
        return result;
    }

    private String effectiveClaim(KnowledgeItem item) {
        return item.claim() == null || item.claim().isBlank() ? item.content() : item.claim();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
