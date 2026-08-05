package io.github.trustrag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.UsedKnowledge;
import io.github.trustrag.core.spi.LlmClient;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LLM 的生成质量评审服务。
 *
 * <p>它分别评估忠实度、答案正确性和相关性，并要求模型返回可解析 JSON，
 * 以便评估报告可以结构化聚合。</p>
 */
public final class DefaultGenerationJudgeService implements GenerationJudgeService {

    private static final int MAX_CONTEXT_LENGTH = 6000;
    private static final int MAX_ANSWER_LENGTH = 4000;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final EvaluationOptions.Judge options;
    private final Clock clock;

    public DefaultGenerationJudgeService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            EvaluationOptions.Judge options,
            Clock clock) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.options = options;
        this.clock = clock;
    }

    @Override
    public JudgedGeneration judge(EvalRun run, EvalCase evalCase, RagAnswer answer) {
        List<EvalJudgeDetail> details = new ArrayList<>();
        JudgeScore faithfulness = score(run, evalCase, answer, JudgeType.FAITHFULNESS);
        details.add(faithfulness.toDetail(run, evalCase, options, clock.instant()));
        JudgeScore correctness = score(run, evalCase, answer, JudgeType.ANSWER_CORRECTNESS);
        details.add(correctness.toDetail(run, evalCase, options, clock.instant()));
        JudgeScore relevance = score(run, evalCase, answer, JudgeType.ANSWER_RELEVANCE);
        details.add(relevance.toDetail(run, evalCase, options, clock.instant()));
        Double hallucination = faithfulness.score == null ? null : 1.0 - faithfulness.score;
        return new JudgedGeneration(
                faithfulness.score, correctness.score, relevance.score, hallucination, details);
    }

    private JudgeScore score(EvalRun run, EvalCase evalCase, RagAnswer answer, JudgeType judgeType) {
        String prompt = prompt(judgeType, evalCase, answer);
        int attempts = Math.max(1, options.maxRetry() + 1);
        long startedNanos = System.nanoTime();
        RuntimeException lastError = null;
        String lastRaw = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                // Judge 失败时重试，但最终失败也会保存明细，避免评估结果静默缺失。
                LlmResponse response = llmClient.generate(prompt);
                String raw = response == null ? "" : response.text();
                lastRaw = raw;
                ParsedJudge parsed = parse(raw);
                return new JudgeScore(
                        judgeType, prompt, raw, parsed.score(), parsed.passed(), parsed.reason(),
                        elapsedMs(startedNanos), attempt);
            } catch (RuntimeException exception) {
                lastError = exception;
            }
        }
        String reason = lastError == null ? "judge failed" : lastError.getMessage();
        return new JudgeScore(
                judgeType, prompt, lastRaw, null, false, reason,
                elapsedMs(startedNanos), attempts - 1);
    }

    private ParsedJudge parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new JudgeResponseException("empty judge response");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            Double score = node.has("score") && node.get("score").isNumber()
                    ? clamp(node.get("score").asDouble())
                    : null;
            if (score == null) {
                throw new JudgeResponseException("judge response JSON does not contain a numeric score");
            }
            Boolean passed = node.has("pass")
                    ? node.get("pass").asBoolean()
                    : score >= 0.7;
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            return new ParsedJudge(score, passed, reason);
        } catch (JudgeResponseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JudgeResponseException(
                    "judge response is not valid JSON: " + raw, exception);
        }
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            // 兼容模型在 JSON 前后添加少量说明文字的情况。
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String prompt(JudgeType judgeType, EvalCase evalCase, RagAnswer answer) {
        String instruction = switch (judgeType) {
            case FAITHFULNESS -> "Evaluate whether the generated answer is fully supported by the provided context.";
            case ANSWER_CORRECTNESS -> "Evaluate whether the generated answer matches the expected answer.";
            case ANSWER_RELEVANCE -> "Evaluate whether the generated answer directly answers the question.";
        };
        // 上下文和答案都做长度截断，避免 Judge 请求被超长样本拖垮。
        return """
                You are a strict TrustRAG evaluation judge.
                %s
                Return JSON only, with this schema:
                {"score":0.0,"pass":false,"reason":"short reason"}

                Question:
                %s

                Expected answer:
                %s

                Generated answer:
                %s

                Retrieved context:
                %s
                """.formatted(
                instruction,
                safe(evalCase.question()),
                safe(evalCase.expectedAnswer()),
                truncate(safe(answer.answer()), MAX_ANSWER_LENGTH),
                truncate(context(answer.usedKnowledge()), MAX_CONTEXT_LENGTH));
    }

    private String context(List<UsedKnowledge> usedKnowledge) {
        StringBuilder builder = new StringBuilder();
        for (UsedKnowledge item : usedKnowledge) {
            builder.append("[")
                    .append(item.knowledgeId())
                    .append("] ")
                    .append(safe(item.title()))
                    .append("\n")
                    .append(safe(item.content()))
                    .append("\n\n");
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record ParsedJudge(Double score, Boolean passed, String reason) {
    }

    private static final class JudgeResponseException extends RuntimeException {

        private JudgeResponseException(String message) {
            super(message);
        }

        private JudgeResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record JudgeScore(
            JudgeType judgeType,
            String prompt,
            String rawOutput,
            Double score,
            Boolean passed,
            String reason,
            long judgeLatencyMs,
            int retryCount) {

        EvalJudgeDetail toDetail(
                EvalRun run,
                EvalCase evalCase,
                EvaluationOptions.Judge options,
                Instant now) {
            return new EvalJudgeDetail(
                    null,
                    null,
                    run.id(),
                    evalCase.id(),
                    judgeType,
                    options.model(),
                    options.savePrompt() ? prompt : null,
                    options.saveOutput() ? rawOutput : null,
                    score,
                    passed,
                    reason,
                    judgeLatencyMs,
                    retryCount,
                    now);
        }
    }
}
