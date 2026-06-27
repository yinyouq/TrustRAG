package io.github.trustrag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagAnswer;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.spi.LlmClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DefaultGenerationJudgeService 的关键行为、边界条件和回归场景。
 */
class DefaultGenerationJudgeServiceTest {

    @Test
    void retriesMalformedJsonAndUsesTheNextValidResponse() {
        CountingLlmClient client = new CountingLlmClient(
                "not-json",
                "{\"score\":0.8,\"pass\":true,\"reason\":\"supported\"}",
                "{\"score\":0.7,\"pass\":true,\"reason\":\"correct\"}",
                "{\"score\":0.9,\"pass\":true,\"reason\":\"relevant\"}");
        DefaultGenerationJudgeService service = service(client, 1);

        JudgedGeneration result = service.judge(run(), evalCase(), answer());

        assertThat(client.calls).isEqualTo(4);
        assertThat(result.faithfulness()).isEqualTo(0.8);
        assertThat(result.answerCorrectness()).isEqualTo(0.7);
        assertThat(result.answerRelevance()).isEqualTo(0.9);
    }

    @Test
    void retainsFinalRawOutputWhenAllAttemptsFail() {
        CountingLlmClient client = new CountingLlmClient(
                "bad-1", "bad-2", "bad-3", "bad-4", "bad-5", "bad-6");
        DefaultGenerationJudgeService service = service(client, 1);

        JudgedGeneration result = service.judge(run(), evalCase(), answer());

        assertThat(client.calls).isEqualTo(6);
        assertThat(result.faithfulness()).isNull();
        assertThat(result.details()).hasSize(3);
        assertThat(result.details().get(0).rawOutput()).isEqualTo("bad-2");
        assertThat(result.details().get(0).passed()).isFalse();
        assertThat(result.details().get(0).reason()).contains("valid JSON");
    }

    private DefaultGenerationJudgeService service(LlmClient client, int maxRetry) {
        return new DefaultGenerationJudgeService(
                client,
                new ObjectMapper(),
                new EvaluationOptions.Judge(true, "judge-model", 0.0, maxRetry, true, true),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private EvalRun run() {
        return new EvalRun(
                1L, 2L, "run", EvalRunType.MANUAL, BeforeAfterGroup.NORMAL,
                EvalRunStatus.RUNNING, 1, 0, 0, null, null, Instant.EPOCH,
                Instant.EPOCH, null, null, "tester", Instant.EPOCH);
    }

    private EvalCase evalCase() {
        return new EvalCase(
                3L, 2L, "question", "expected", null, null, null, null,
                List.of(), "MEDIUM", true, Instant.EPOCH, Instant.EPOCH);
    }

    private RagAnswer answer() {
        return new RagAnswer(
                "trace", "answer", 0.9, List.of(), false,
                GapDetectionResult.noGap(), 1, 1, 10);
    }

    private static final class CountingLlmClient implements LlmClient {

        private final Queue<String> responses;
        private int calls;

        private CountingLlmClient(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public LlmResponse generate(String prompt) {
            calls++;
            return new LlmResponse(responses.remove(), 1.0, TokenUsage.unknown());
        }
    }
}
