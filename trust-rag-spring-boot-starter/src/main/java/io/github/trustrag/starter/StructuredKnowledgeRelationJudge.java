package io.github.trustrag.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.spi.KnowledgeRelationJudge;
import io.github.trustrag.core.spi.LlmClient;

public final class StructuredKnowledgeRelationJudge implements KnowledgeRelationJudge {

    private static final String PROMPT = """
            Compare two knowledge claims for a trusted RAG system.
            Return exactly one JSON object with:
            type (SUPPORT, CONFLICT, VERSION_DIFF, or UNRELATED),
            confidence (0..1), reason.
            Treat version-specific differences as VERSION_DIFF, not a conflict.
            Candidate claim: %s
            Existing claim: %s
            Candidate version: %s
            Existing version: %s
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeRelationJudge fallback;

    public StructuredKnowledgeRelationJudge(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            KnowledgeRelationJudge fallback) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public RelationJudgement judge(KnowledgeItem candidate, KnowledgeItem existing) {
        String raw = llmClient.generate(PROMPT.formatted(
                claim(candidate), claim(existing),
                text(candidate.governance().applicableVersion()),
                text(existing.governance().applicableVersion()))).text();
        try {
            JsonNode json = objectMapper.readTree(stripFence(raw));
            ConflictType type = ConflictType.valueOf(json.path("type").asText());
            double confidence = Math.max(0.0, Math.min(1.0, json.path("confidence").asDouble()));
            return new RelationJudgement(
                    type, confidence, json.path("reason").asText("No reason supplied"));
        } catch (Exception exception) {
            return fallback.judge(candidate, existing);
        }
    }

    private String claim(KnowledgeItem item) {
        return item.claim() == null || item.claim().isBlank() ? item.content() : item.claim();
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

    private String text(String value) {
        return value == null ? "" : value;
    }
}
