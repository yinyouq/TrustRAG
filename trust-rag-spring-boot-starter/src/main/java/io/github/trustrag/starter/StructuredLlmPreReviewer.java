package io.github.trustrag.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.PreReviewResult;
import io.github.trustrag.core.model.PromotionAction;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.core.spi.LlmClient;
import io.github.trustrag.core.spi.LlmPreReviewer;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化 LLM 预审器。
 *
 * <p>用于低可信候选晋升前的质量、证据和风险判断。输出必须是 JSON，
 * 晋升引擎会把它和规则评分一起纳入最终决策。</p>
 */
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
            Similar knowledge:
            %s
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
                text(candidate.evidence()), text(candidate.sourceType()), text(candidate.sourceRef()),
                similarKnowledge(similarKnowledge))).text();
        try {
            JsonNode json = objectMapper.readTree(stripFence(raw));
            if (!json.isObject()) {
                throw new IllegalArgumentException("pre-review output is not a JSON object");
            }
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
            throw new TrustRagException(
                    "Invalid LLM pre-review JSON: " + exception.getMessage(), exception);
        }
    }

    private double score(JsonNode json, String name) {
        return Math.max(0.0, Math.min(1.0, json.path(name).asDouble(0.0)));
    }

    private PromotionAction action(String value) {
        try {
            return PromotionAction.valueOf(value);
        } catch (Exception exception) {
            // 无法识别的动作按保守策略保留低可信，避免模型格式漂移导致误晋升。
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

    private String similarKnowledge(List<KnowledgeItem> values) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        return values.stream()
                .limit(10)
                .map(item -> "- [" + item.trustLevel().name() + "] " + effectiveClaim(item))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(none)");
    }
}
