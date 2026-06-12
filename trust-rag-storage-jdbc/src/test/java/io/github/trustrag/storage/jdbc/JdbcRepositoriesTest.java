package io.github.trustrag.storage.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.GapDetectionResult;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.model.RagTrace;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TrustLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRepositoriesTest {

    private EmbeddedDatabase database;
    private JdbcKnowledgeRepository knowledgeRepository;
    private JdbcRagTraceRepository traceRepository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema-h2.sql")
                .build();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(database);
        knowledgeRepository = new JdbcKnowledgeRepository(jdbc);
        traceRepository = new JdbcRagTraceRepository(jdbc, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void persistsKnowledgeAndTrace() {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        KnowledgeItem saved = knowledgeRepository.save(new KnowledgeItem(
                null, "title", "claim", "content", null, "test",
                TrustLevel.HIGH, KnowledgeStatus.INDEXING, ScopeType.GLOBAL,
                null, null, null, null, "manual", "source", "evidence",
                null, null, null, 1.0, 0.0, 1, "hash-1",
                null, null, null, now, now, null));

        assertThat(saved.id()).isPositive();
        assertThat(knowledgeRepository.findById(saved.id())).contains(saved);

        KnowledgeItem enabled = saved.withIndexState(
                KnowledgeStatus.HIGH_ENABLED, saved.id().toString(), "test-model", 3, now);
        assertThat(knowledgeRepository.updateIfState(
                enabled, KnowledgeStatus.INDEXING, saved.version())).isTrue();
        assertThat(knowledgeRepository.updateIfState(
                enabled, KnowledgeStatus.INDEXING, saved.version())).isFalse();

        RagTrace trace = RagTrace.start(RagRequest.builder().question("question").build(), now);
        trace.complete(new LlmResponse("answer", 0.8, new TokenUsage(2, 3)), GapDetectionResult.noGap(), 12);
        traceRepository.save(trace);

        assertThat(traceRepository.existsByTraceId(trace.traceId())).isTrue();
    }
}
